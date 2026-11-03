package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.repository.*;
import br.com.sistema.triagem.service.WebhookService;
import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:evolution_http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.config.import=",
    "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false", "evolution.enabled=true",
    "evolution.instance=teste", "evolution.api-key=api-key-ficticia", "evolution.worker-enabled=false",
    "evolution.webhook-token=segredo-ficticio-de-testes-32-caracteres", "evolution.read-timeout=2s",
    "evolution.managed=true", "evolution.webhook-url=http://backend:8080/api/webhooks/evolution",
    "evolution.provision-enabled=false"
})
@AutoConfigureMockMvc
class EvolutionIntegrationTest {
    static final String TOKEN = "segredo-ficticio-de-testes-32-caracteres";
    static final HttpServer upstream;
    static volatile int responseCode = 201;
    static volatile String responseBody;
    static volatile String authHeader;
    static volatile JsonNode sentBody;
    static volatile Runnable onSend;
    static volatile boolean slow;
    static volatile boolean instanceExists;
    static volatile String connectionState="open";
    static volatile JsonNode createdInstance, configuredWebhook;
    static volatile String connectBody;
    static AtomicInteger creates=new AtomicInteger();
    static AtomicInteger sends = new AtomicInteger();
    static {
        try {
            upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            upstream.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            upstream.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String body;
                int code;
                if (path.equals("/instance/connectionState/teste")) {
                    body = responseBody == null ? "{\"instance\":{\"instanceName\":\"teste\",\"state\":\""+connectionState+"\"}}" : responseBody;
                    code = responseCode == 201 ? 200 : responseCode;
                } else if (path.equals("/instance/fetchInstances")) {
                    body=instanceExists ? "[{\"name\":\"teste\"}]" : "[]";
                    code=responseCode==201 ? (instanceExists ? 200 : 404) : responseCode;
                } else if (path.equals("/instance/create")) {
                    createdInstance=new ObjectMapper().readTree(exchange.getRequestBody());
                    creates.incrementAndGet(); instanceExists=true;
                    body="{\"instance\":{\"instanceName\":\"teste\"},\"hash\":\"SEGREDO-REMOTO\"}";code=201;
                } else if (path.equals("/webhook/set/teste")) {
                    configuredWebhook=new ObjectMapper().readTree(exchange.getRequestBody());
                    body="{}";code=responseCode;
                } else if (path.equals("/instance/connect/teste")) {
                    body=connectBody==null ? "{\"base64\":\"data:image/png;base64,aGVsbG8=\",\"apikey\":\"SEGREDO-REMOTO\",\"pairingCode\":\"SEGREDO\"}" : connectBody;
                    code=200;
                } else if (path.equals("/message/sendText/teste")) {
                    sends.incrementAndGet();
                    authHeader = exchange.getRequestHeaders().getFirst("apikey");
                    sentBody = new ObjectMapper().readTree(exchange.getRequestBody());
                    if (onSend != null) onSend.run();
                    body = responseBody == null ? "{\"key\":{\"id\":\"saida-1\",\"fromMe\":true,\"remoteJid\":\"5511999991234@s.whatsapp.net\"}}" : responseBody;
                    code = responseCode;
                } else { body = "{}"; code = 501; }
                if (slow) try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                try {
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(code, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } finally { exchange.close(); }
            });
            upstream.start();
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("evolution.base-url", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }
    @AfterAll static void stop() { upstream.stop(0); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebhookService webhook;
    @Autowired ChamadoRepository chamados;
    @Autowired MensagemRepository mensagens;

    @BeforeEach void reset() {
        responseCode=201; responseBody=null; onSend=null; slow=false; sends.set(0);
        instanceExists=true; connectionState="open"; creates.set(0); createdInstance=null;configuredWebhook=null;connectBody=null;
        jdbc.update("DELETE FROM evolution_webhook_inbox");
        jdbc.update("DELETE FROM operacoes");
        mensagens.deleteAll(); chamados.deleteAll();
        jdbc.update("DELETE FROM evolution_contato_jids"); jdbc.update("DELETE FROM evolution_contatos");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tecnicos WHERE login='admin'", Integer.class)==0)
            jdbc.update("INSERT INTO tecnicos(login,nome,senha_hash,perfil,equipe) VALUES ('admin','Admin teste','nao-usado','ADMIN','N1')");
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tecnicos WHERE login='n2'", Integer.class)==0)
            jdbc.update("INSERT INTO tecnicos(login,nome,senha_hash,perfil,equipe) VALUES ('n2','Tecnico N2','nao-usado','TECNICO','N2')");
    }
    Map<String,Object> event(String id, String jid, String alt, boolean fromMe) {
        var key = new HashMap<String,Object>(Map.of("id",id,"remoteJid",jid,"fromMe",fromMe));
        if (alt!=null) key.put("remoteJidAlt",alt);
        return new HashMap<>(Map.of("event","messages.upsert","instance","teste","apikey","NAO-PERSISTIR",
                "data",new HashMap<>(Map.of("key",key,"pushName","Cliente ficticio","message",Map.of("conversation","  Preciso de ajuda\n "),
                        "messageTimestamp","1793358000"))));
    }
    MockHttpServletRequestBuilder receive(Object body) throws Exception {
        return post("/api/webhooks/evolution").header("X-Webhook-Token",TOKEN)
                .contentType("application/json").content(mapper.writeValueAsString(body));
    }
    long incoming(String id, String jid, String alt) throws Exception {
        mvc.perform(receive(event(id,jid,alt,false))).andExpect(status().isAccepted());
        webhook.processarPendentes();
        return mensagens.findByIdentificadorExterno("evolution:teste:"+id).orElseThrow().getChamado().getId();
    }
    MockHttpServletRequestBuilder reply(long id, String text) throws Exception {
        return post("/api/chamados/{id}/responder",id).with(user("admin").roles("ADMIN"))
                .contentType("application/json").content(mapper.writeValueAsString(Map.of("texto",text)));
    }

    @Test void autenticaValidaFormatoInstanciaEIgnoraEventos() throws Exception {
        var body=event("a","5511999991234@s.whatsapp.net",null,false);
        mvc.perform(post("/api/webhooks/evolution").contentType("application/json").content(mapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        mvc.perform(receive(body).header("Authorization","Bearer invalido")).andExpect(status().isUnauthorized());
        body.put("instance","outra"); mvc.perform(receive(body)).andExpect(status().isForbidden());
        body.put("instance","teste"); body.put("data",Map.of()); mvc.perform(receive(body)).andExpect(status().isBadRequest());
        body.put("event","connection.update"); mvc.perform(receive(body)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ignoradas").value(1));
        mvc.perform(receive(event("b","123@g.us",null,false))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evolution_webhook_inbox",Integer.class)).isZero();
        assertThat(chamados.count()).isZero();
        mvc.perform(get("/api/whatsapp/status")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/chamados/1/responder").contentType("application/json").content("{\"texto\":\"oi\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test void gravaAntesDoAckDeduplicaEContinuaChamadoAposEncerramento() throws Exception {
        var first=event("entrada-1","5511999991234@s.whatsapp.net",null,false);
        mvc.perform(receive(first)).andExpect(status().isAccepted()).andExpect(jsonPath("$.aceitas").value(1));
        assertThat(chamados.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT payload FROM evolution_webhook_inbox",String.class)).doesNotContain("NAO-PERSISTIR","apikey");
        mvc.perform(receive(first)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicadas").value(1));
        webhook.processarPendentes();
        long id=chamados.findAll().getFirst().getId();
        assertThat(mensagens.findAll().getFirst().getConteudo()).isEqualTo("  Preciso de ajuda\n ");
        assertThat(chamados.findById(id).orElseThrow().getEquipe()).isEqualTo("N1");
        assertThat(incoming("entrada-2","5511999991234@s.whatsapp.net",null)).isEqualTo(id);
        jdbc.update("UPDATE chamados SET status='ENCERRADO',causa_real='Causa',solucao_aplicada='Solucao',data_encerramento=CURRENT_TIMESTAMP WHERE id=?",id);
        long next=incoming("entrada-3","5511999991234@s.whatsapp.net",null);
        assertThat(next).isNotEqualTo(id);
        assertThat(chamados.findById(next).orElseThrow().getChamadoAnteriorId()).isEqualTo(id);
        mvc.perform(receive(first)).andExpect(status().isOk()); webhook.processarPendentes();
        assertThat(chamados.count()).isEqualTo(2); assertThat(mensagens.count()).isEqualTo(3);
        assertThat(chamados.findById(id).orElseThrow().getCausaReal()).isEqualTo("Causa");
    }

    @Test void lidNaoViraTelefoneEAprendeAliasEmAmbasAsDirecoes() throws Exception {
        long id=incoming("lid-1","888888888888888@lid",null);
        assertThat(chamados.findById(id).orElseThrow().getTelefoneCliente()).isNull();
        mvc.perform(reply(id,"Resposta por LID")).andExpect(status().isCreated());
        assertThat(sentBody.path("number").asText()).isEqualTo("888888888888888@lid");
        assertThat(incoming("lid-2","888888888888888@lid","5511999991234@s.whatsapp.net")).isEqualTo(id);
        assertThat(incoming("lid-3","5511999991234@s.whatsapp.net","888888888888888@lid")).isEqualTo(id);
        assertThat(incoming("lid-4","888888888888888@lid",null)).isEqualTo(id);
        assertThat(chamados.findById(id).orElseThrow().getTelefoneCliente()).isEqualTo("5511999991234");
        assertThat(chamados.count()).isEqualTo(1);
    }

    @Test void associaContatoAoChamadoManualAtivo() throws Exception {
        var c=new br.com.sistema.triagem.model.entity.Chamado(); c.setTelefoneCliente("5511999991234");
        c.setStatus("AGUARDANDO_USUARIO"); c=chamados.save(c);
        assertThat(incoming("existente","5511999991234@s.whatsapp.net",null)).isEqualTo(c.getId());
        assertThat(chamados.count()).isEqualTo(1);
    }

    @Test void envioRegistraAutoriaEIgnoraEcoRecebidoAntesDaRespostaHttp() throws Exception {
        long id=incoming("inicial","5511999991234@s.whatsapp.net",null);
        var executor=Executors.newSingleThreadExecutor();
        List<Future<?>> workers=new ArrayList<>();
        onSend=() -> {
            try { mvc.perform(receive(event("saida-1","5511999991234@s.whatsapp.net",null,true)))
                    .andExpect(status().isAccepted()); }
            catch (Exception e) { throw new RuntimeException(e); }
            workers.add(executor.submit(webhook::processarPendentes));
        };
        try {
            mvc.perform(reply(id,"  Resposta do tecnico\n")).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.autorId").isNumber()).andExpect(jsonPath("$.remetente").value("TECNICO"))
                    .andExpect(jsonPath("$.identificadorExterno").value("evolution:teste:saida-1"));
            for (var f:workers) f.get(5,TimeUnit.SECONDS);
            assertThat(authHeader).isEqualTo("api-key-ficticia");
            assertThat(sentBody.path("text").asText()).isEqualTo("  Resposta do tecnico\n");
            assertThat(mensagens.count()).isEqualTo(2); assertThat(chamados.count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operacoes WHERE acao='RESPOSTA_WHATSAPP'",Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void ecoDeduplicadoAprendeAliasParaChamadoManual() throws Exception {
        var c=new br.com.sistema.triagem.model.entity.Chamado(); c.setTelefoneCliente("5511999991234");
        long id=chamados.save(c).getId();
        mvc.perform(reply(id,"Resposta inicial")).andExpect(status().isCreated());
        mvc.perform(receive(event("saida-1","888888888888888@lid","5511999991234@s.whatsapp.net",true)))
                .andExpect(status().isAccepted());
        webhook.processarPendentes();
        assertThat(incoming("nova-por-lid","888888888888888@lid",null)).isEqualTo(id);
        assertThat(chamados.count()).isEqualTo(1); assertThat(mensagens.count()).isEqualTo(2);
    }

    @Test void falhasRemotasNaoRegistramEnvioNemExponhemRespostaDoProvedor() throws Exception {
        long id=incoming("inicial","5511999991234@s.whatsapp.net",null);
        responseCode=500; responseBody="{\"apikey\":\"segredo-provedor\"}";
        mvc.perform(reply(id,"Oi")).andExpect(status().isBadGateway())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("segredo-provedor"))));
        assertThat(mensagens.count()).isEqualTo(1); assertThat(sends.get()).isEqualTo(1);
        responseCode=201; responseBody="{}";
        mvc.perform(reply(id,"Oi")).andExpect(status().isBadGateway());
        responseBody=null; slow=true;
        mvc.perform(reply(id,"Oi")).andExpect(status().isGatewayTimeout());
        assertThat(mensagens.count()).isEqualTo(1); assertThat(sends.get()).isEqualTo(3);
    }

    @Test void validaTextoPermissaoEncerramentoEStatus() throws Exception {
        long id=incoming("inicial","5511999991234@s.whatsapp.net",null);
        mvc.perform(reply(id," ")).andExpect(status().isBadRequest());
        mvc.perform(reply(id,"a".repeat(10001))).andExpect(status().isBadRequest());
        mvc.perform(reply(-1,"oi")).andExpect(status().isNotFound());
        mvc.perform(reply(id,"Oi").with(user("n2").roles("TECNICO"))).andExpect(status().isForbidden());
        jdbc.update("UPDATE chamados SET status='ENCERRADO' WHERE id=?",id);
        mvc.perform(reply(id,"Oi")).andExpect(status().isConflict());
        assertThat(sends.get()).isZero();
        mvc.perform(get("/api/whatsapp/status").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("open"));
        responseBody="{}";
        mvc.perform(get("/api/whatsapp/status").with(user("admin").roles("ADMIN"))).andExpect(status().isBadGateway());
    }

    @Test void eventosSimultaneosNaoDuplicamChamadosNemMensagens() throws Exception {
        try (var pool=Executors.newFixedThreadPool(6)) {
            var ready=new CountDownLatch(6); var start=new CountDownLatch(1); var tasks=new ArrayList<Future<?>>();
            for(int i=0;i<6;i++) { final String msg="concorrente-"+(i%3);
                tasks.add(pool.submit(() -> {
                    ready.countDown(); start.await();
                    mvc.perform(receive(event(msg,"5511999991234@s.whatsapp.net",null,false)))
                            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(200,202));
                    webhook.processarPendentes(); return null;
                }));
            }
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            for(var t:tasks)t.get(15,TimeUnit.SECONDS);
        }
        assertThat(chamados.count()).isEqualTo(1); assertThat(mensagens.count()).isEqualTo(3);
    }

    @Test void loteInvalidoNaoGravaParcialEAnexosNaoPersistemSegredos() throws Exception {
        var first=event("lote-1","5511999991234@s.whatsapp.net",null,false);
        var second=event("lote-2","invalido",null,false);
        first.put("data",List.of(first.get("data"),second.get("data")));
        mvc.perform(receive(first)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evolution_webhook_inbox",Integer.class)).isZero();
        var media=event("anexo","5511999991234@s.whatsapp.net",null,false);
        ((Map<String,Object>)media.get("data")).put("message",Map.of("imageMessage",Map.of("caption","Foto do defeito","url","URL-PRIVADA","mediaKey","SEGREDO")));
        mvc.perform(receive(media)).andExpect(status().isAccepted()); webhook.processarPendentes();
        assertThat(mensagens.findAll().getFirst().getTipo()).isEqualTo("IMAGEM");
        assertThat(jdbc.queryForObject("SELECT payload FROM evolution_webhook_inbox",String.class)).doesNotContain("URL-PRIVADA","SEGREDO");
    }

    @Test void mensagensDoProprioAparelhoNaoAbremChamadoEAliasConflitanteFicaRecuperavel() throws Exception {
        mvc.perform(receive(event("propria","5511999991234@s.whatsapp.net",null,true))).andExpect(status().isAccepted());
        webhook.processarPendentes(); assertThat(chamados.count()).isZero();
        incoming("a","5511999991234@s.whatsapp.net",null);
        incoming("b","999999999999999@lid",null);
        mvc.perform(receive(event("conflito","999999999999999@lid","5511999991234@s.whatsapp.net",false)))
                .andExpect(status().isAccepted()); webhook.processarPendentes();
        assertThat(jdbc.queryForObject("SELECT estado FROM evolution_webhook_inbox WHERE identificador_externo='evolution:teste:conflito'",String.class))
                .isEqualTo("FALHA");
        assertThat(mensagens.count()).isEqualTo(2); assertThat(chamados.count()).isEqualTo(2);
    }

    @Test void conectaSomenteComoAdminProvisionaWebhookERetornaQrSemChaves() throws Exception {
        instanceExists=false; connectionState="close";
        mvc.perform(post("/api/whatsapp/conectar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/whatsapp/conectar").with(user("n2").roles("TECNICO"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/whatsapp/conectar").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.estado").value("connecting"))
                .andExpect(jsonPath("$.qrCode").value("data:image/png;base64,aGVsbG8="))
                .andExpect(jsonPath("$.apikey").doesNotExist()).andExpect(jsonPath("$.hash").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SEGREDO"))));
        assertThat(creates.get()).isEqualTo(1);
        assertThat(createdInstance.path("integration").asText()).isEqualTo("WHATSAPP-BAILEYS");
        assertThat(createdInstance.path("syncFullHistory").asBoolean()).isFalse();
        assertThat(configuredWebhook.path("webhook").path("headers").path("X-Webhook-Token").asText()).isEqualTo(TOKEN);
        assertThat(configuredWebhook.path("webhook").path("url").asText()).isEqualTo("http://backend:8080/api/webhooks/evolution");
        mvc.perform(post("/api/whatsapp/conectar").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        assertThat(creates.get()).isEqualTo(1);
        assertThat(chamados.count()).isZero(); assertThat(mensagens.count()).isZero();
    }

    @Test void conexaoAbertaNaoInventaQrERecusaImagemRemotaInvalida() throws Exception {
        mvc.perform(post("/api/whatsapp/conectar").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("open"))
                .andExpect(jsonPath("$.qrCode").isEmpty());
        connectionState="close"; connectBody="{\"base64\":\"https://site-externo/imagem\"}";
        mvc.perform(post("/api/whatsapp/conectar").with(user("admin").roles("ADMIN"))).andExpect(status().isBadGateway());
        responseCode=503;
        mvc.perform(post("/api/whatsapp/conectar").with(user("admin").roles("ADMIN"))).andExpect(status().isBadGateway());
        mvc.perform(get("/whatsapp.html")).andExpect(status().isOk());
    }
}
