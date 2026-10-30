package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.service.AcessoService;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:acesso;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.show-sql=false", "spring.jpa.open-in-view=false"
})
@AutoConfigureMockMvc
class AcessoControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AcessoService acesso;
    @Autowired JdbcTemplate jdbc;
    AcessoService.Tecnico admin, n1, colega, n2;
    String adminToken, n1Token, colegaToken, n2Token;
    final String senha = "Senha-sintetica-123!";

    @BeforeEach
    void preparar() throws Exception {
        admin = tecnico("ADMIN", "N1"); n1 = tecnico("TECNICO", "N1");
        colega = tecnico("TECNICO", "N1"); n2 = tecnico("TECNICO", "N2");
        adminToken = login(admin); n1Token = login(n1); colegaToken = login(colega); n2Token = login(n2);
    }
    AcessoService.Tecnico tecnico(String perfil, String equipe) {
        return acesso.cadastrar(new AcessoService.Cadastro(UUID.randomUUID().toString(), "Teste", senha, perfil, equipe));
    }
    String login(AcessoService.Tecnico t) throws Exception {
        return request(post("/api/auth/login"), null, Map.of("login", t.login(), "senha", senha), 200).get("token").asText();
    }
    JsonNode request(MockHttpServletRequestBuilder req, String token, Object body, int status) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        var res = mvc.perform(req).andExpect(status().is(status)).andReturn().getResponse();
        return res.getContentAsString().isBlank() ? mapper.nullNode() : mapper.readTree(res.getContentAsString());
    }
    long chamado(String token) throws Exception {
        return request(post("/api/chamados"), token, Map.of("telefoneCliente", "11900000000", "equipe", "N1"), 201).get("id").asLong();
    }

    @Test
    void exigeCredenciaisRevogaExpiraENaoExibeSegredos() throws Exception {
        request(get("/api/chamados"), null, null, 401);
        request(post("/api/chamados"), null, Map.of("telefoneCliente", "11900000000"), 401);
        request(get("/api/tecnicos"), "invalido", null, 401);
        request(post("/api/auth/login"), null, Map.of("login", n1.login(), "senha", "errada"), 401);
        JsonNode me = request(get("/api/auth/me"), n1Token, null, 200);
        assertThat(me.get("id").asLong()).isEqualTo(n1.id());
        assertThat(me.has("senhaHash") || me.has("senha")).isFalse();
        assertThat(jdbc.queryForObject("SELECT senha_hash FROM tecnicos WHERE id=?", String.class, n1.id())).startsWith("$2a$");
        assertThat(jdbc.queryForList("SELECT token_hash FROM sessoes", String.class)).doesNotContain(n1Token);
        request(post("/api/auth/logout"), n1Token, null, 204);
        request(get("/api/auth/me"), n1Token, null, 401);
        jdbc.update("UPDATE sessoes SET expira_em=TIMESTAMP '2000-01-01 00:00:00' WHERE tecnico_id=?", n2.id());
        request(get("/api/auth/me"), n2Token, null, 401);
        mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(content().string("OK"));
    }

    @Test
    void restringeCadastroEDesativacaoSemPermitirElevacao() throws Exception {
        var cadastro = Map.of("login", UUID.randomUUID().toString(), "nome", "Novo", "senha", senha, "perfil", "ADMIN", "equipe", "N1");
        request(post("/api/tecnicos"), n1Token, cadastro, 403);
        JsonNode criado = request(post("/api/tecnicos"), adminToken, cadastro, 201);
        assertThat(criado.has("senha")).isFalse();
        request(post("/api/tecnicos"), adminToken, cadastro, 409);
        request(post("/api/tecnicos/{id}/desativar", n2.id()), n1Token, null, 403);
        request(post("/api/tecnicos/{id}/desativar", admin.id()), adminToken, null, 409);
        request(post("/api/tecnicos/{id}/desativar", n2.id()), adminToken, null, 200);
        request(get("/api/auth/me"), n2Token, null, 401);
        request(post("/api/auth/login"), null, Map.of("login", n2.login(), "senha", senha), 401);
    }

    @Test
    void validaAtribuicaoEquipeEAutoriaPreservandoFalhas() throws Exception {
        long id = chamado(n1Token);
        request(patch("/api/chamados/{id}/responsavel", id), adminToken, Map.of("responsavelId", n2.id()), 400);
        request(patch("/api/chamados/{id}/responsavel", id), adminToken, Map.of("responsavelId", -1), 404);
        request(patch("/api/chamados/{id}/responsavel", id), n1Token, Map.of("responsavelId", colega.id()), 403);
        request(patch("/api/chamados/{id}/responsavel", id), n1Token, Map.of("responsavelId", n1.id()), 200);
        for (String token : List.of(n2Token, colegaToken)) {
            request(patch("/api/chamados/{id}/status", id), token, Map.of("status", "EM_ATENDIMENTO"), 403);
            request(post("/api/chamados/{id}/mensagens", id), token, Map.of("remetente", "TECNICO", "tipo", "TEXTO", "conteudo", "Negada"), 403);
            request(post("/api/chamados/{id}/encerrar", id), token, Map.of("causaReal", "X", "solucaoAplicada", "Y"), 403);
        }
        request(patch("/api/chamados/{id}/equipe", id), n1Token, Map.of("equipe", "N2"), 403);
        JsonNode original = request(get("/api/chamados/{id}", id), adminToken, null, 200);
        assertThat(original.get("status").asText()).isEqualTo("ABERTO");
        assertThat(original.get("responsavelId").asLong()).isEqualTo(n1.id());
        assertThat(request(get("/api/chamados/{id}/operacoes", id), adminToken, null, 200).size()).isEqualTo(2);
        request(patch("/api/chamados/{id}/status", id), n1Token, Map.of("status", "EM_ATENDIMENTO", "autorId", admin.id()), 200);
        JsonNode msg = request(post("/api/chamados/{id}/mensagens", id), n1Token,
                Map.of("remetente", "TECNICO", "tipo", "TEXTO", "conteudo", "Teste", "autorId", admin.id()), 201);
        assertThat(msg.get("autorId").asLong()).isEqualTo(n1.id());
        JsonNode historico = request(get("/api/chamados/{id}/operacoes", id), adminToken, null, 200);
        assertThat(historico.size()).isEqualTo(4);
        for (JsonNode op : historico) {
            assertThat(op.get("autorId").asLong()).isEqualTo(n1.id());
            assertThat(op.get("id").asLong()).isPositive();
            assertThat(op.get("dataOperacao").asText()).isNotBlank();
        }
        assertThat(historico.get(2).get("acao").asText()).isEqualTo("STATUS");
        assertThat(historico.get(3).get("acao").asText()).isEqualTo("MENSAGEM");
        JsonNode transferido = request(patch("/api/chamados/{id}/equipe", id), adminToken, Map.of("equipe", "N2"), 200);
        assertThat(transferido.get("responsavelId").isNull()).isTrue();
        assertThat(transferido.get("responsavel").isNull()).isTrue();
        request(patch("/api/chamados/{id}/status", id), n1Token, Map.of("status", "ABERTO"), 403);
        request(patch("/api/chamados/{id}/responsavel", id), n2Token, Map.of("responsavelId", n2.id()), 200);
        request(post("/api/chamados/{id}/encerrar", id), n2Token, Map.of("causaReal", "X", "solucaoAplicada", "Y"), 200);
    }

    @Test
    void atribuicoesConcorrentesNaoSobrescrevemOutroTecnico() throws Exception {
        long id = chamado(adminToken);
        var inicio = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                inicio.await();
                return mvc.perform(patch("/api/chamados/{id}/responsavel", id)
                        .header("Authorization", "Bearer " + n1Token).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("responsavelId", n1.id()))))
                        .andReturn().getResponse().getStatus();
            });
            var b = executor.submit(() -> {
                inicio.await();
                return mvc.perform(patch("/api/chamados/{id}/responsavel", id)
                        .header("Authorization", "Bearer " + colegaToken).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("responsavelId", colega.id()))))
                        .andReturn().getResponse().getStatus();
            });
            inicio.countDown();
            assertThat(List.of(a.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    b.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 403);
            JsonNode chamado = request(get("/api/chamados/{id}", id), adminToken, null, 200);
            JsonNode historico = request(get("/api/chamados/{id}/operacoes", id), adminToken, null, 200);
            assertThat(historico.size()).isEqualTo(2);
            assertThat(historico.get(1).get("autorId").asLong()).isEqualTo(chamado.get("responsavelId").asLong());
        }
    }

    @Test
    void rejeitaTecnicoInativoECriacaoEmOutraEquipe() throws Exception {
        long id = chamado(adminToken);
        request(post("/api/tecnicos/{id}/desativar", n1.id()), adminToken, null, 200);
        request(patch("/api/chamados/{id}/responsavel", id), adminToken, Map.of("responsavelId", n1.id()), 400);
        request(post("/api/chamados"), n2Token, Map.of("telefoneCliente", "11900000000", "equipe", "N1"), 403);
        request(patch("/api/chamados/{id}/responsavel", id), adminToken, Map.of("responsavel", "Texto livre"), 400);
    }
}

