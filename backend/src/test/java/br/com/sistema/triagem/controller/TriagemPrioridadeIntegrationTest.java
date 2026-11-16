package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.service.TriagemPrioridadeService;
import br.com.sistema.triagem.service.AprendizadoIaService;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=jdbc:h2:mem:prioridade;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.open-in-view=false",
        "ia.enabled=true", "ia.timeout=2s", "ia.auto-enabled=true", "ia.worker-delay-ms=3600000",
        "ia.feedback-token=segredo-ficticio-exclusivo-deste-teste", "ia.feedback-delay-ms=3600000", "evolution.enabled=false"})
@AutoConfigureMockMvc
@WithMockUser(username="admin", roles="ADMIN")
class TriagemPrioridadeIntegrationTest {
    private static final String RESULT = """
        {"prioridade_recomendada":"CRITICA","prioridade_base":"CRITICA","impacto":"ALTO","urgencia":"ALTO",
        "prioridade_matriz":"CRITICA","metodo_prioridade":"MATRIZ","confianca_prioridade":null,
        "origem":"INFORMADO","origem_impacto":"INFORMADO","origem_urgencia":"INFORMADO",
        "confianca_impacto":null,"confianca_urgencia":null,"revisao_humana_obrigatoria":true,
        "modelo_sintetico":false,"baixa_confianca":false,"versao_modelo":null,
        "versao_matriz":"impacto-urgencia-v1","versao_regras":"contexto-v1","regras_aplicadas":[]}
        """;
    static HttpServer server;
    static volatile int upstreamStatus = 200;
    static volatile long delay = 0;
    static volatile String result = RESULT;
    static AtomicReference<String> input = new AtomicReference<>();
    static AtomicReference<String> feedback = new AtomicReference<>();
    static volatile int feedbackStatus = 201;
    static CountDownLatch entered;
    static CountDownLatch release;
    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/predict/priority", exchange -> {
                input.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                try {
                    if (entered != null) { entered.countDown(); release.await(5, TimeUnit.SECONDS); }
                    if (delay > 0) Thread.sleep(delay);
                    byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(upstreamStatus, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (Exception ignored) { } finally { exchange.close(); }
            });
            server.createContext("/learning/feedback", exchange -> {
                feedback.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                var objectMapper = new ObjectMapper();
                var event = objectMapper.readTree(feedback.get()).path("evento_id").asText();
                var body = objectMapper.createObjectNode().put("evento_id", event).put("estado", "RECEBIDO").toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(feedbackStatus, body.length);
                exchange.getResponseBody().write(body); exchange.close();
            });
            server.setExecutor(Executors.newCachedThreadPool(r -> { var t = new Thread(r); t.setDaemon(true); return t; }));
            server.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("ia.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TriagemPrioridadeService service;
    @Autowired AprendizadoIaService aprendizado;
    @BeforeEach void setup() {
        upstreamStatus = 200; delay = 0; result = RESULT; entered = null; release = null;
        feedbackStatus = 201; feedback.set(null);
        jdbc.update("DELETE FROM triagens_prioridade"); jdbc.update("DELETE FROM mensagens");
        jdbc.update("DELETE FROM operacoes"); jdbc.update("DELETE FROM chamados");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tecnicos WHERE login='admin'", Long.class) == 0)
            jdbc.update("INSERT INTO tecnicos(login,nome,senha_hash,perfil,equipe) VALUES ('admin','Admin','nao-utilizado','ADMIN','N1')");
    }
    @AfterAll static void shutdown() { server.stop(0); }
    long create() throws Exception {
        return mapper.readTree(mvc.perform(post("/api/chamados").contentType(APPLICATION_JSON)
                .content("{\"telefoneCliente\":\"11900000000\",\"resumo\":\"Servidor indisponivel\",\"descricao\":\"Toda empresa parada\",\"equipe\":\"N1\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    String path(long id) { return "/api/chamados/" + id + "/triagens-prioridade"; }
    long analyze(long id) throws Exception {
        return mapper.readTree(mvc.perform(post(path(id)).contentType(APPLICATION_JSON)
                .content("{\"impactoInformado\":\"ALTO\",\"urgenciaInformada\":\"ALTO\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("AGUARDANDO_REVISAO"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }
    @Test void acceptsAndPreservesOriginalRejectsSecondDecision() throws Exception {
        long call = create(), triage = analyze(call);
        mvc.perform(get("/api/chamados/{id}", call)).andExpect(jsonPath("$.prioridadeFinal").isEmpty());
        mvc.perform(post(path(call)+"/"+triage+"/decisao").contentType(APPLICATION_JSON).content("{\"acao\":\"ACEITAR\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEITA"))
                .andExpect(jsonPath("$.resultado.prioridade_recomendada").value("CRITICA"))
                .andExpect(jsonPath("$.decisorId").isNumber());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE chamado_id=?", Integer.class, call));
        mvc.perform(post(path(call)+"/"+triage+"/decisao").contentType(APPLICATION_JSON).content("{\"acao\":\"ACEITAR\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/chamados/{id}", call)).andExpect(jsonPath("$.prioridadeFinal").value("CRITICA"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE chamado_id=?", Integer.class, call));
        assertFalse(input.get().contains("11900000000"));
    }
    @Test void correctionNeedsReasonAndDoesNotChangeOriginal() throws Exception {
        long call = create(), triage = analyze(call);
        mvc.perform(post(path(call)+"/"+triage+"/decisao").contentType(APPLICATION_JSON).content("{\"acao\":\"CORRIGIR\",\"prioridadeFinal\":\"MEDIA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(path(call)+"/"+triage+"/decisao").contentType(APPLICATION_JSON)
                .content("{\"acao\":\"CORRIGIR\",\"prioridadeFinal\":\"MEDIA\",\"justificativa\":\"Existe alternativa operacional\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.prioridadeFinal").value("MEDIA"))
                .andExpect(jsonPath("$.resultado.prioridade_recomendada").value("CRITICA"));
        analyze(call);
        mvc.perform(get("/api/chamados/{id}", call)).andExpect(jsonPath("$.prioridadeFinal").value("MEDIA"));
    }
    @Test void newAnalysisAndManualChangeInvalidateOldDecision() throws Exception {
        long call = create(), old = analyze(call), latest = analyze(call);
        mvc.perform(post(path(call)+"/"+old+"/decisao").contentType(APPLICATION_JSON).content("{\"acao\":\"ACEITAR\"}"))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/chamados/{id}/prioridade", call).contentType(APPLICATION_JSON).content("{\"prioridadeFinal\":\"BAIXA\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(path(call)+"/"+latest+"/decisao").contentType(APPLICATION_JSON).content("{\"acao\":\"ACEITAR\"}"))
                .andExpect(status().isConflict());
    }
    @Test void failureAndTimeoutDoNotBlockManualWork() throws Exception {
        long call = create(); upstreamStatus = 503;
        mvc.perform(post(path(call)).contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isServiceUnavailable());
        mvc.perform(get(path(call))).andExpect(jsonPath("$[0].estado").value("FALHA"));
        upstreamStatus = 200; delay = 2300;
        mvc.perform(post(path(call)).contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isGatewayTimeout());
        mvc.perform(patch("/api/chamados/{id}/prioridade", call).contentType(APPLICATION_JSON).content("{\"prioridadeFinal\":\"ALTA\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/chamados/{id}/encerrar", call).contentType(APPLICATION_JSON).content("{\"causaReal\":\"Teste\",\"solucaoAplicada\":\"Resolvido\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(path(call)).contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isConflict());
    }
    @Test void invalidProviderAndInvalidInputAreRejected() throws Exception {
        long call = create();
        mvc.perform(post(path(call)).contentType(APPLICATION_JSON).content("{\"impactoInformado\":\"URGENTE\"}" )).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM triagens_prioridade", Integer.class));
        result = "{\"prioridade_recomendada\":\"CRITICA\"}";
        mvc.perform(post(path(call)).contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isBadGateway());
    }
    @Test @WithMockUser(username="outsider", roles="TECNICO")
    void restrictsOtherTeams() throws Exception {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tecnicos WHERE login='outsider'", Long.class) == 0)
            jdbc.update("INSERT INTO tecnicos(login,nome,senha_hash,perfil,equipe) VALUES ('outsider','Outro','x','TECNICO','N2')");
        jdbc.update("INSERT INTO chamados(telefone_cliente,status,data_abertura,equipe,resumo) VALUES ('11900000001','ABERTO',CURRENT_TIMESTAMP,'N1','Teste')");
        long call = jdbc.queryForObject("SELECT MAX(id) FROM chamados", Long.class);
        mvc.perform(post(path(call)).contentType(APPLICATION_JSON).content("{}" )).andExpect(status().isForbidden());
        mvc.perform(get(path(call))).andExpect(status().isForbidden());
    }
    @Test void concurrentCloseDuringInferencePreservesClosure() throws Exception {
        long call = create(); entered = new CountDownLatch(1); release = new CountDownLatch(1);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin", "unused", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        var future = CompletableFuture.supplyAsync(() -> {
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try { return service.solicitar(call, new TriagemPrioridadeService.Solicitar("ALTO","ALTO",null)); }
            finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        });
        if (!entered.await(5, TimeUnit.SECONDS)) {
            release.countDown();
            future.get(2, TimeUnit.SECONDS);
            fail("Servico HTTP nao recebeu a inferencia");
        }
        mvc.perform(post("/api/chamados/{id}/encerrar", call).contentType(APPLICATION_JSON).content("{\"causaReal\":\"Teste\",\"solucaoAplicada\":\"Resolvido\"}"))
                .andExpect(status().isOk());
        release.countDown();
        assertEquals("OBSOLETA", future.get(2, TimeUnit.SECONDS).estado());
        mvc.perform(get("/api/chamados/{id}", call)).andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.prioridadeSugerida").isEmpty());
    }
    @Test void automaticWorkerDeduplicatesAndDoesNotDecide() throws Exception {
        long call = create(); jdbc.update("UPDATE chamados SET canal='WHATSAPP' WHERE id=?", call);
        jdbc.update("INSERT INTO mensagens(chamado_id,remetente,conteudo,tipo,data_envio) VALUES (?,'CLIENTE','Sistema parou','TEXTO',CURRENT_TIMESTAMP)", call);
        service.processarMensagens(); service.processarMensagens();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM triagens_prioridade WHERE chamado_id=?", Integer.class, call));
        mvc.perform(get("/api/chamados/{id}", call)).andExpect(jsonPath("$.prioridadeFinal").isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE chamado_id=?", Integer.class, call));
    }
    @Test void feedbackRetriesWithoutUndoingCorrectionAndBackfillsOldDecisions() throws Exception {
        long call = create(), triage = analyze(call);
        mvc.perform(post(path(call)+"/"+triage+"/decisao").contentType(APPLICATION_JSON)
                .content("{\"acao\":\"CORRIGIR\",\"prioridadeFinal\":\"MEDIA\",\"justificativa\":\"Alternativa confirmada\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.feedbackTreinamento.estado").value("PENDENTE"));
        feedbackStatus = 503;
        aprendizado.enviarPendentes();
        assertEquals("PENDENTE", aprendizado.status(triage).estado());
        assertEquals(1, aprendizado.status(triage).tentativas());
        mvc.perform(get("/api/chamados/{id}", call)).andExpect(jsonPath("$.prioridadeFinal").value("MEDIA"));
        jdbc.update("UPDATE feedback_treinamento_ia SET proxima_tentativa=CURRENT_TIMESTAMP");
        feedbackStatus = 201;
        aprendizado.enviarPendentes();
        assertEquals("ENVIADO", aprendizado.status(triage).estado());
        var sent = mapper.readTree(feedback.get());
        assertEquals("MEDIA", sent.path("prioridade_final").asText());
        assertEquals("CORRIGIR", sent.path("acao").asText());
        assertFalse(sent.has("impacto")); assertFalse(sent.has("urgencia"));
        // Simula decisão anterior à V7: o reconciliador inclui uma vez.
        jdbc.update("DELETE FROM feedback_treinamento_ia WHERE triagem_id=?", triage);
        aprendizado.enviarPendentes(); aprendizado.enviarPendentes();
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE triagem_id=?", Integer.class, triage));
    }
    @Test void manualPriorityAlsoCreatesLearningSnapshotAndInvalidRequestsDoNot() throws Exception {
        long call = create();
        mvc.perform(patch("/api/chamados/{id}/prioridade", call).contentType(APPLICATION_JSON)
                .content("{\"prioridadeFinal\":\"URGENTE\"}")).andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE chamado_id=?", Integer.class, call));
        mvc.perform(patch("/api/chamados/{id}/prioridade", call).contentType(APPLICATION_JSON)
                .content("{\"prioridadeFinal\":\"ALTA\"}")).andExpect(status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM feedback_treinamento_ia WHERE chamado_id=? AND operacao_id IS NOT NULL", Integer.class, call));
        var payload = mapper.readTree(jdbc.queryForObject("SELECT payload FROM feedback_treinamento_ia WHERE chamado_id=?", String.class, call));
        assertEquals("Toda empresa parada", payload.path("texto").asText());
        assertEquals("ALTA", payload.path("prioridade_final").asText());
    }
    @Test void feedbackMasksKnownNameWithoutBreakingEmailRedaction() throws Exception {
        long call = create();
        jdbc.update("UPDATE chamados SET nome_cliente='ContatoFicticio',descricao='ContatoFicticio enviou contatoficticio@example.com' WHERE id=?", call);
        long triage = analyze(call);
        mvc.perform(post(path(call)+"/"+triage+"/decisao").contentType(APPLICATION_JSON).content("{\"acao\":\"ACEITAR\"}"))
                .andExpect(status().isOk());
        String payload = jdbc.queryForObject("SELECT payload FROM feedback_treinamento_ia WHERE triagem_id=?", String.class, triage);
        assertFalse(payload.contains("ContatoFicticio"));
        assertFalse(payload.contains("example.com"));
        assertTrue(payload.contains("[PESSOA]") && payload.contains("[EMAIL]"));
    }
}
