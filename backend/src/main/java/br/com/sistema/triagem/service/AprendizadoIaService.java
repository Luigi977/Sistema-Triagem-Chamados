package br.com.sistema.triagem.service;

import br.com.sistema.triagem.client.IaPrioridadeClient;
import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.repository.ChamadoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AprendizadoIaService {
    public record FeedbackTreinamento(String eventoId, String estado, int tentativas) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final IaPrioridadeClient client;
    private final ChamadoRepository chamados;
    private final TransactionTemplate transaction;

    public AprendizadoIaService(JdbcTemplate jdbc, ObjectMapper mapper, IaPrioridadeClient client,
            ChamadoRepository chamados, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.mapper = mapper; this.client = client;
        this.chamados = chamados; this.transaction = new TransactionTemplate(manager);
    }
    // Chamado já está bloqueado; esta gravação faz parte da transação da decisão.
    public void registrar(Chamado chamado, long triagemId, String action, String priority) {
        try {
            var input = mapper.readTree(jdbc.queryForObject("SELECT entrada FROM triagens_prioridade WHERE id=?", String.class, triagemId));
            registrarPayload(chamado, triagemId, null, input, action, priority);
        } catch (java.io.IOException e) { throw new IllegalStateException("Entrada de triagem invalida", e); }
    }
    public void registrarManual(Chamado chamado) {
        Long operation = jdbc.queryForObject("SELECT MAX(id) FROM operacoes WHERE chamado_id=? AND acao='PRIORIDADE'", Long.class, chamado.getId());
        var messages = jdbc.queryForList("SELECT conteudo FROM mensagens WHERE chamado_id=? AND remetente='CLIENTE' AND tipo='TEXTO' ORDER BY id DESC LIMIT 1", String.class, chamado.getId());
        String text = messages.isEmpty() ? (chamado.getDescricao() != null ? chamado.getDescricao() : chamado.getResumo()) : messages.getFirst();
        if (text == null || text.isBlank()) return; // Sem texto não há exemplo para treinar.
        registrarPayload(chamado, null, operation, mapper.createObjectNode().put("texto", text), "CORRIGIR", chamado.getPrioridadeFinal());
    }
    private void registrarPayload(Chamado chamado, Long triagemId, Long operationId, JsonNode input, String action, String priority) {
            var groups = jdbc.queryForList("SELECT grupo FROM feedback_treinamento_ia WHERE chamado_id=? ORDER BY id LIMIT 1", String.class, chamado.getId());
            String group = groups.isEmpty() ? UUID.randomUUID().toString() : groups.getFirst();
            String event = UUID.randomUUID().toString();
            String text = input.path("texto").asText();
            if (text.length() > 10000) text = text.substring(0, 10000);
            // E-mail primeiro: mascarar o nome antes poderia quebrar o padrão do endereço.
            text = text.replaceAll("[\\w.+-]+@[\\w.-]+\\.[A-Za-z]{2,}", "[EMAIL]");
            if (chamado.getNomeCliente() != null && !chamado.getNomeCliente().isBlank())
                text = text.replaceAll("(?iu)(?<![\\w\\[])" + Pattern.quote(chamado.getNomeCliente()) + "(?![\\w\\]])", "[PESSOA]");
            if (chamado.getTelefoneCliente() != null && !chamado.getTelefoneCliente().isBlank())
                text = text.replace(chamado.getTelefoneCliente(), "[TELEFONE]");
            boolean critical = input.path("contexto").path("confirmado").asBoolean()
                    && "ALTA".equals(input.path("contexto").path("criticidade_servico").asText());
            var payload = mapper.createObjectNode().put("evento_id", event).put("grupo", group)
                    .put("texto", text).put("acao", action).put("prioridade_final", priority)
                    .put("servico_critico_confirmado", critical);
            Timestamp decision = triagemId != null
                    ? jdbc.queryForObject("SELECT decidida_em FROM triagens_prioridade WHERE id=?", Timestamp.class, triagemId)
                    : jdbc.queryForObject("SELECT data_operacao FROM operacoes WHERE id=?", Timestamp.class, operationId);
            payload.put("decidida_em", decision.toLocalDateTime().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime().toString());
            var now = Timestamp.valueOf(LocalDateTime.now());
            jdbc.update("INSERT INTO feedback_treinamento_ia(triagem_id,operacao_id,chamado_id,evento_id,grupo,payload,proxima_tentativa,criada_em) VALUES (?,?,?,?,?,?,?,?)",
                    triagemId, operationId, chamado.getId(), event, group, payload.toString(), now, now);
    }
    public FeedbackTreinamento status(long triagemId) {
        return jdbc.query("SELECT evento_id,estado,tentativas FROM feedback_treinamento_ia WHERE triagem_id=?",
                (rs, n) -> new FeedbackTreinamento(rs.getString(1), rs.getString(2), rs.getInt(3)), triagemId)
                .stream().findFirst().orElse(null);
    }
    @Scheduled(fixedDelayString = "${ia.feedback-delay-ms:10000}")
    public synchronized void enviarPendentes() {
        // Inclui decisões humanas anteriores à implantação desta funcionalidade.
        var missing = jdbc.queryForList("SELECT t.id,t.chamado_id,t.estado,t.prioridade_final FROM triagens_prioridade t "
                + "WHERE t.estado IN ('ACEITA','CORRIGIDA') AND t.decisor_id IS NOT NULL "
                + "AND NOT EXISTS (SELECT 1 FROM feedback_treinamento_ia f WHERE f.triagem_id=t.id) ORDER BY t.id LIMIT 20");
        for (var item : missing) {
            transaction.executeWithoutResult(tx -> {
                long callId = ((Number)item.get("chamado_id")).longValue();
                long triageId = ((Number)item.get("id")).longValue();
                jdbc.queryForObject("SELECT id FROM chamados WHERE id=? FOR UPDATE", Long.class, callId);
                if (jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE triagem_id=?", Long.class, triageId) == 0)
                    chamados.findById(callId).ifPresent(call -> registrar(call, triageId,
                            "ACEITA".equals(item.get("estado")) ? "ACEITAR" : "CORRIGIR", (String)item.get("prioridade_final")));
            });
        }
        if (!client.feedbackEnabled()) return;
        var pending = jdbc.queryForList("SELECT id,payload,tentativas FROM feedback_treinamento_ia WHERE estado='PENDENTE' AND proxima_tentativa<=? ORDER BY id LIMIT 20",
                Timestamp.valueOf(LocalDateTime.now()));
        for (var item : pending) {
            long id = ((Number)item.get("id")).longValue();
            int attempts = ((Number)item.get("tentativas")).intValue() + 1;
            try {
                client.enviarFeedback(mapper.readTree((String)item.get("payload")));
                jdbc.update("UPDATE feedback_treinamento_ia SET estado='ENVIADO',enviada_em=?,tentativas=?,ultimo_erro=NULL WHERE id=? AND estado='PENDENTE'",
                        Timestamp.valueOf(LocalDateTime.now()), attempts, id);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                // Não registra texto, token ou resposta upstream em logs/erros públicos.
                jdbc.update("UPDATE feedback_treinamento_ia SET tentativas=?,ultimo_erro='FALHA_ENVIO',proxima_tentativa=? WHERE id=? AND estado='PENDENTE'",
                        attempts, Timestamp.valueOf(LocalDateTime.now().plusSeconds(Math.min(3600L, 10L * attempts))), id);
            }
        }
    }
}
