package br.com.sistema.triagem.service;

import br.com.sistema.triagem.config.EvolutionApiConfig.Properties;
import br.com.sistema.triagem.dto.evolution.*;
import br.com.sistema.triagem.repository.MensagemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class WebhookService {
    public record Recebimento(int aceitas, int duplicadas, int ignoradas) {}
    public record Entrada(String id, String jid, String alternativo, boolean fromMe, String nome,
                          String tipo, String conteudo, LocalDateTime dataEnvio) {}
    private final Properties config;
    private final ObjectMapper mapper;
    private final EvolutionStore store;
    private final ChamadoService chamados;
    private final MensagemService mensagens;
    private final MensagemRepository repository;
    private final TransactionTemplate transaction;
    private final boolean workerEnabled;

    public WebhookService(Properties config, ObjectMapper mapper, EvolutionStore store, ChamadoService chamados,
                          MensagemService mensagens, MensagemRepository repository, PlatformTransactionManager manager,
                          @Value("${evolution.worker-enabled:true}") boolean workerEnabled) {
        this.config = config; this.mapper = mapper; this.store = store; this.chamados = chamados;
        this.mensagens = mensagens; this.repository = repository;
        this.transaction = new TransactionTemplate(manager); this.workerEnabled = workerEnabled;
    }

    public Recebimento receber(String token, EvolutionWebhookDTO evento) {
        if (!config.enabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Integração Evolution desativada");
        if (token == null || !MessageDigest.isEqual(config.webhookToken().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) throw new ResponseStatusException(UNAUTHORIZED);
        if (evento == null || evento.event() == null || evento.event().isBlank()
                || evento.instance() == null) throw invalido();
        if (!config.instance().equals(evento.instance())) throw new ResponseStatusException(FORBIDDEN, "Instância não autorizada");
        if (!"messages.upsert".equals(evento.event()) && !"MESSAGES_UPSERT".equals(evento.event()))
            return new Recebimento(0, 0, 1);
        JsonNode data = evento.data();
        if (data == null || (!data.isArray() && !data.isObject()) || (data.isArray() && (data.isEmpty() || data.size() > 100)))
            throw invalido();
        List<Entrada> entradas = new ArrayList<>();
        int ignoradas = 0;
        var nodes = data.isArray() ? data : mapper.createArrayNode().add(data);
        // Validar todo o lote antes de gravar. Somente campos necessários são persistidos.
        for (JsonNode node : nodes) {
            Entrada entrada = normalizar(node);
            if (entrada == null) ignoradas++; else entradas.add(entrada);
        }
        int aceitas = 0, duplicadas = 0;
        for (Entrada entrada : entradas) {
            try {
                store.enfileirar(config.instance(), externo(config.instance(), entrada.id()), serializar(entrada));
                aceitas++;
            } catch (DuplicateKeyException e) { duplicadas++; }
        }
        return new Recebimento(aceitas, duplicadas, ignoradas);
    }

    private Entrada normalizar(JsonNode node) {
        try {
            EvolutionMessageDTO msg = mapper.treeToValue(node, EvolutionMessageDTO.class);
            if (msg == null || msg.key() == null || msg.key().fromMe() == null
                    || msg.key().id() == null || !msg.key().id().matches("[A-Za-z0-9._:-]{1,150}")
                    || msg.key().remoteJid() == null) throw invalido();
            var key = msg.key();
            if (key.remoteJid().endsWith("@g.us") || key.remoteJid().endsWith("@broadcast")
                    || key.remoteJid().endsWith("@newsletter")) return null;
            if (!EvolutionStore.jidValido(key.remoteJid())) throw invalido();
            String alt = key.remoteJidAlt();
            if (key.remoteJid().equals(alt)) alt = null;
            if (alt != null && (!EvolutionStore.jidValido(alt)
                    || alt.substring(alt.indexOf('@')).equals(key.remoteJid().substring(key.remoteJid().indexOf('@')))))
                throw invalido();
            JsonNode content = msg.message();
            for (int i=0; i<3 && content != null; i++) {
                JsonNode wrapped = null;
                for (String wrapper : List.of("ephemeralMessage", "viewOnceMessage", "viewOnceMessageV2"))
                    if (content.has(wrapper)) { wrapped = content.path(wrapper).get("message"); break; }
                if (wrapped == null) break;
                content = wrapped;
            }
            if (content == null || !content.isObject()) throw invalido();
            String texto = null, tipo = "TEXTO";
            if (content.has("conversation")) texto = string(content.get("conversation"));
            else if (content.has("extendedTextMessage")) texto = string(content.path("extendedTextMessage").get("text"));
            else {
                for (var media : Map.of("imageMessage","IMAGEM", "audioMessage","AUDIO", "videoMessage","VIDEO",
                        "documentMessage","DOCUMENTO", "stickerMessage","STICKER").entrySet()) {
                    if (!content.has(media.getKey())) continue;
                    tipo = media.getValue();
                    texto = string(content.path(media.getKey()).get("caption"));
                    if (texto == null || texto.isBlank()) texto = "[" + tipo + "]";
                    break;
                }
                if (texto == null) return null; // protocolo, reação e outros conteúdos não suportados
            }
            if (texto == null || texto.isBlank() || texto.length() > 10000) throw invalido();
            String nome = key.fromMe() ? null : msg.pushName();
            if (nome != null) nome = nome.substring(0, Math.min(150, nome.length()));
            LocalDateTime data = msg.messageTimestamp() == null ? LocalDateTime.now()
                    : LocalDateTime.ofInstant(Instant.ofEpochSecond(msg.messageTimestamp()), ZoneId.systemDefault());
            if (data.getYear() < 1970 || data.getYear() > 9999) throw invalido();
            return new Entrada(key.id(), key.remoteJid(), alt, key.fromMe(), nome, tipo, texto, data);
        } catch (ResponseStatusException e) { throw e; }
        catch (Exception e) { throw invalido(); }
    }

    private static String string(JsonNode node) { return node != null && node.isTextual() ? node.textValue() : null; }
    private String serializar(Entrada entrada) {
        try { return mapper.writeValueAsString(entrada); }
        catch (Exception e) { throw new IllegalStateException("Falha ao serializar entrada validada"); }
    }
    public static String externo(String instancia, String id) { return "evolution:" + instancia + ":" + id; }
    private static ResponseStatusException invalido() { return new ResponseStatusException(BAD_REQUEST, "Evento Evolution inválido"); }

    @Scheduled(fixedDelayString = "${evolution.worker-delay-ms:1000}")
    public void executarWorker() {
        if (config.enabled() && workerEnabled) processarPendentes();
    }

    public void processarPendentes() {
        for (long id : store.pendentes(config.instance())) {
            try {
                transaction.executeWithoutResult(tx -> {
                    store.coordenar();
                    var evento = store.bloquear(id);
                    if (!config.instance().equals(evento.instancia())
                            || !Set.of("PENDENTE", "FALHA").contains(evento.estado())) return;
                    Entrada entrada;
                    try { entrada = mapper.readValue(evento.payload(), Entrada.class); }
                    catch (Exception e) { throw new IllegalStateException("Entrada persistida inválida"); }
                    var contato = store.identificar(entrada.jid(), entrada.alternativo());
                    // O eco também pode trazer o primeiro vínculo PN/LID do contato.
                    if (repository.findByIdentificadorExterno(evento.externo()).isPresent()) {
                        store.concluir(id, false); return;
                    }
                    var chamado = chamados.receberWhatsapp(contato, entrada.nome(), entrada.conteudo(), entrada.fromMe());
                    if (chamado != null) mensagens.registrarWhatsapp(chamado, entrada.fromMe() ? "TECNICO" : "CLIENTE",
                            entrada.conteudo(), entrada.tipo(), entrada.dataEnvio(), evento.externo(), null);
                    store.concluir(id, chamado != null);
                });
            } catch (Exception e) {
                store.falhou(id);
                LoggerFactory.getLogger(WebhookService.class).warn("Falha no evento Evolution inbox {}; nova tentativa agendada", id);
            }
        }
    }
}
