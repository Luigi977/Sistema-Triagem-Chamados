package br.com.sistema.triagem.service;

import br.com.sistema.triagem.client.IaPrioridadeClient;
import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.repository.ChamadoRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class TriagemPrioridadeService {
    public record Contexto(String cargo, String setor, String criticidadeServico, Boolean confirmado) {}
    public record Solicitar(String impactoInformado, String urgenciaInformada, Contexto contexto) {}
    public record Decidir(String acao, String prioridadeFinal, String justificativa) {}
    public record Triagem(Long id, Long chamadoId, Long mensagemId, Long autorId, String estado,
            JsonNode resultado, String erro, LocalDateTime criadaEm, LocalDateTime decididaEm,
            Long decisorId, String prioridadeFinal, String justificativa,
            AprendizadoIaService.FeedbackTreinamento feedbackTreinamento) {}
    private record Reserva(long id, long chamadoId, String hash, JsonNode entrada, boolean automatico) {}
    private record Fonte(String texto, Long mensagemId, String hash) {}
    private final JdbcTemplate jdbc;
    private final ChamadoRepository chamados;
    private final AcessoService acesso;
    private final ObjectMapper mapper;
    private final IaPrioridadeClient client;
    private final TransactionTemplate transaction;
    private final boolean autoEnabled;
    private final AprendizadoIaService aprendizado;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public TriagemPrioridadeService(JdbcTemplate jdbc, ChamadoRepository chamados, AcessoService acesso,
            ObjectMapper mapper, IaPrioridadeClient client, PlatformTransactionManager manager,
            @Value("${ia.auto-enabled:false}") boolean autoEnabled, AprendizadoIaService aprendizado) {
        this.jdbc = jdbc; this.chamados = chamados; this.acesso = acesso; this.mapper = mapper;
        this.client = client; this.transaction = new TransactionTemplate(manager); this.autoEnabled = autoEnabled;
        this.aprendizado = aprendizado;
    }
    public List<Triagem> listar(long chamadoId) {
        var c = chamados.findById(chamadoId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        acesso.editar(c);
        return jdbc.query("SELECT * FROM triagens_prioridade WHERE chamado_id=? ORDER BY id DESC", this::map, chamadoId);
    }
    public Triagem solicitar(long chamadoId, Solicitar request) {
        if (request == null) throw new ResponseStatusException(BAD_REQUEST);
        Reserva reserva = transaction.execute(tx -> reservar(chamadoId, request, false));
        return executar(reserva);
    }
    private Reserva reservar(long chamadoId, Solicitar request, boolean automatico) {
        Chamado c = bloquear(chamadoId);
        if (!automatico) acesso.editar(c);
        aberto(c);
        Fonte fonte = fonte(c);
        if (fonte.texto().isBlank()) throw new ResponseStatusException(BAD_REQUEST, "Chamado sem texto para analisar");
        ObjectNode input = mapper.createObjectNode().put("texto", fonte.texto());
        if (request.impactoInformado() != null) input.put("impacto_informado", categoria(request.impactoInformado(), Set.of("BAIXO","MEDIO","ALTO")));
        if (request.urgenciaInformada() != null) input.put("urgencia_informada", categoria(request.urgenciaInformada(), Set.of("BAIXO","MEDIO","ALTO")));
        ObjectNode context = input.putObject("contexto");
        Contexto ctx = request.contexto();
        if (ctx != null) {
            if (ctx.cargo() != null) context.put("cargo", texto(ctx.cargo(), 100));
            if (ctx.setor() != null) context.put("setor", texto(ctx.setor(), 150));
            else if (c.getSetor() != null) context.put("setor", c.getSetor());
            if (ctx.criticidadeServico() != null) context.put("criticidade_servico", categoria(ctx.criticidadeServico(), Set.of("BAIXA","MEDIA","ALTA")));
            context.put("confirmado", Boolean.TRUE.equals(ctx.confirmado()));
        } else if (c.getSetor() != null) context.put("setor", c.getSetor());
        Long autor = automatico ? null : acesso.atual().id();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement("INSERT INTO triagens_prioridade(chamado_id,mensagem_id,evento_chave,autor_id,estado,entrada_hash,entrada,criada_em) VALUES (?,?,?,?,'PROCESSANDO',?,?,?)", new String[]{"id"});
            ps.setLong(1, chamadoId); ps.setObject(2, fonte.mensagemId());
            ps.setString(3, automatico ? "mensagem:" + fonte.mensagemId() : null); ps.setObject(4, autor);
            ps.setString(5, fonte.hash()); ps.setString(6, input.toString()); ps.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));
            return ps;
        }, key);
        return new Reserva(key.getKey().longValue(), chamadoId, fonte.hash(), input, automatico);
    }
    private Triagem executar(Reserva reserva) {
        JsonNode result;
        try { result = client.recomendar(reserva.entrada()); }
        catch (ResponseStatusException error) {
            transaction.executeWithoutResult(tx -> jdbc.update("UPDATE triagens_prioridade SET estado='FALHA',erro=? WHERE id=? AND estado='PROCESSANDO'",
                    "IA_HTTP_" + error.getStatusCode().value(), reserva.id()));
            throw error;
        }
        return transaction.execute(tx -> {
            Chamado c = bloquear(reserva.chamadoId());
            boolean obsolete = "ENCERRADO".equals(c.getStatus()) || !fonte(c).hash().equals(reserva.hash())
                    || !ultima(reserva.chamadoId()).equals(reserva.id());
            jdbc.update("UPDATE triagens_prioridade SET estado=?,resultado=? WHERE id=?",
                    obsolete ? "OBSOLETA" : "AGUARDANDO_REVISAO", result.toString(), reserva.id());
            if (!obsolete) {
                c.setPrioridadeSugerida(result.get("prioridade_recomendada").asText());
                chamados.saveAndFlush(c);
            }
            return buscar(reserva.id(), reserva.chamadoId());
        });
    }
    public Triagem decidir(long chamadoId, long id, Decidir request) {
        return transaction.execute(tx -> {
            Chamado c = bloquear(chamadoId); acesso.editar(c); aberto(c);
            Triagem t = buscar(id, chamadoId);
            String hash = jdbc.queryForObject("SELECT entrada_hash FROM triagens_prioridade WHERE id=?", String.class, id);
            if (!"AGUARDANDO_REVISAO".equals(t.estado()) || !ultima(chamadoId).equals(id) || !fonte(c).hash().equals(hash))
                throw new ResponseStatusException(CONFLICT, "Sugestao obsoleta ou ja decidida; solicite nova analise");
            if (request == null) throw new ResponseStatusException(BAD_REQUEST);
            String action = categoria(request.acao(), Set.of("ACEITAR", "CORRIGIR"));
            String priority;
            String reason = null;
            if ("ACEITAR".equals(action)) {
                if (request.prioridadeFinal() != null) throw new ResponseStatusException(BAD_REQUEST, "Aceite nao recebe prioridadeFinal");
                priority = t.resultado().path("prioridade_recomendada").asText();
            } else {
                priority = categoria(request.prioridadeFinal(), Set.of("BAIXA","MEDIA","ALTA","CRITICA"));
                reason = texto(request.justificativa(), 1000);
            }
            c.setPrioridadeFinal(priority); chamados.saveAndFlush(c);
            jdbc.update("UPDATE triagens_prioridade SET estado=?,decidida_em=?,decisor_id=?,prioridade_final=?,justificativa=? WHERE id=?",
                    "ACEITAR".equals(action) ? "ACEITA" : "CORRIGIDA", Timestamp.valueOf(LocalDateTime.now()), acesso.atual().id(), priority, reason, id);
            acesso.registrar(c, "TRIAGEM_" + action);
            aprendizado.registrar(c, id, action, priority);
            return buscar(id, chamadoId);
        });
    }
    // Somente mensagens de texto do cliente já persistidas, uma análise por mensagem.
    @Scheduled(fixedDelayString = "${ia.worker-delay-ms:5000}")
    public void processarMensagens() {
        // Recuperação de uma chamada interrompida por queda do processo.
        jdbc.update("UPDATE triagens_prioridade SET estado='FALHA',erro='PROCESSO_INTERROMPIDO' WHERE estado='PROCESSANDO' AND criada_em < ?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)));
        if (!autoEnabled || !client.enabled()) return;
        var ids = jdbc.queryForList("SELECT c.id FROM chamados c JOIN mensagens m ON m.chamado_id=c.id "
                + "WHERE c.canal='WHATSAPP' AND c.status<>'ENCERRADO' AND m.remetente='CLIENTE' AND m.tipo='TEXTO' "
                + "AND m.id=(SELECT MAX(m2.id) FROM mensagens m2 WHERE m2.chamado_id=c.id AND m2.remetente='CLIENTE' AND m2.tipo='TEXTO') "
                + "AND NOT EXISTS (SELECT 1 FROM triagens_prioridade t WHERE t.evento_chave=CONCAT('mensagem:',CAST(m.id AS VARCHAR))) ORDER BY c.id LIMIT 10", Long.class);
        for (Long id : ids) {
            try {
                var reservation = transaction.execute(tx -> reservar(id, new Solicitar(null,null,null), true));
                executar(reservation);
            } catch (Exception ignored) {
                // Falha registrada; sem repetir inferência automaticamente. Nova tentativa pelo painel.
            }
        }
    }
    private Fonte fonte(Chamado c) {
        var messages = jdbc.queryForList("SELECT id,conteudo FROM mensagens WHERE chamado_id=? AND remetente='CLIENTE' AND tipo='TEXTO' ORDER BY id DESC LIMIT 1", c.getId());
        String text = messages.isEmpty() ? Objects.toString(c.getDescricao(), Objects.toString(c.getResumo(), "")) : (String) messages.getFirst().get("conteudo");
        Long messageId = messages.isEmpty() ? null : ((Number)messages.getFirst().get("id")).longValue();
        Long operation = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM operacoes WHERE chamado_id=?", Long.class, c.getId());
        try {
            String source = mapper.writeValueAsString(Arrays.asList(text, messageId, c.getSetor(), c.getEquipe(), c.getResponsavelId(), c.getStatus(), c.getPrioridadeFinal(), operation));
            return new Fonte(text, messageId, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8))));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private Long ultima(long chamadoId) { return jdbc.queryForObject("SELECT MAX(id) FROM triagens_prioridade WHERE chamado_id=?", Long.class, chamadoId); }
    private Chamado bloquear(long id) {
        Chamado c = chamados.findForUpdate(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        entityManager.refresh(c, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return c;
    }
    private static void aberto(Chamado c) { if ("ENCERRADO".equals(c.getStatus())) throw new ResponseStatusException(CONFLICT, "Chamado encerrado"); }
    private static String texto(String s, int max) { if (s == null || s.isBlank() || s.trim().length() > max) throw new ResponseStatusException(BAD_REQUEST, "Texto obrigatorio ou acima do limite"); return s.trim(); }
    private static String categoria(String s, Set<String> values) { String v = texto(s, 30).toUpperCase(Locale.ROOT); if (!values.contains(v)) throw new ResponseStatusException(BAD_REQUEST, "Categoria invalida"); return v; }
    private Triagem buscar(long id, long chamadoId) {
        return jdbc.query("SELECT * FROM triagens_prioridade WHERE id=? AND chamado_id=?", this::map, id, chamadoId).stream().findFirst().orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
    }
    private Triagem map(ResultSet rs, int row) throws SQLException {
        JsonNode result = null;
        try { if (rs.getString("resultado") != null) result = mapper.readTree(rs.getString("resultado")); }
        catch (Exception e) { throw new SQLException("Resultado persistido invalido", e); }
        return new Triagem(rs.getLong("id"), rs.getLong("chamado_id"), rs.getObject("mensagem_id", Long.class),
                rs.getObject("autor_id", Long.class), rs.getString("estado"), result, rs.getString("erro"),
                rs.getTimestamp("criada_em").toLocalDateTime(), rs.getTimestamp("decidida_em") == null ? null : rs.getTimestamp("decidida_em").toLocalDateTime(),
                rs.getObject("decisor_id", Long.class), rs.getString("prioridade_final"), rs.getString("justificativa"),
                aprendizado.status(rs.getLong("id")));
    }
}
