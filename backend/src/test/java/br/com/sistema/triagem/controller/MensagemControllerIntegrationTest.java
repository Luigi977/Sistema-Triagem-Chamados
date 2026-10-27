package br.com.sistema.triagem.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.repository.ChamadoRepository;
import br.com.sistema.triagem.repository.MensagemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:mensagens_http;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.open-in-view=false",
    "spring.jpa.show-sql=false"
})
@AutoConfigureMockMvc
class MensagemControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ChamadoRepository chamados;
    @Autowired MensagemRepository mensagens;

    private long chamadoId;

    @BeforeEach
    void prepararBanco() {
        mensagens.deleteAll();
        chamados.deleteAll();
        chamadoId = criarChamado();
    }

    private long criarChamado() {
        Chamado chamado = new Chamado();
        chamado.setTelefoneCliente("11999990000");
        return chamados.save(chamado).getId();
    }

    private Map<String, Object> dados() {
        return new HashMap<>(Map.of("remetente", "CLIENTE", "conteudo", "Preciso de ajuda", "tipo", "TEXTO"));
    }

    private JsonNode registrar(long id, Map<String, Object> body) throws Exception {
        var response = mvc.perform(post("/api/chamados/{id}/mensagens", id)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse();
        JsonNode mensagem = mapper.readTree(response.getContentAsString());
        assertThat(response.getHeader("Location")).endsWith("/api/mensagens/" + mensagem.get("id").asLong());
        mvc.perform(get(response.getHeader("Location"))).andExpect(status().isOk())
                .andExpect(content().json(response.getContentAsString()));
        return mensagem;
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENTE", "TECNICO", "SISTEMA"})
    void registraConsultaENormalizaRemetentes(String remetente) throws Exception {
        var body = dados();
        body.put("remetente", " " + remetente.toLowerCase(java.util.Locale.ROOT) + " ");
        body.put("tipo", " texto ");
        body.put("conteudo", "  Olá!\nPreciso de ajuda.  ");
        body.put("dataEnvio", "2026-10-27T10:15:00.123456789");
        body.put("identificadorExterno", " wamid.Exemplo ");
        JsonNode mensagem = registrar(chamadoId, body);
        assertThat(mensagem.get("chamadoId").asLong()).isEqualTo(chamadoId);
        assertThat(mensagem.get("remetente").asText()).isEqualTo(remetente);
        assertThat(mensagem.get("tipo").asText()).isEqualTo("TEXTO");
        assertThat(mensagem.get("conteudo").asText()).isEqualTo(body.get("conteudo"));
        assertThat(mensagem.get("dataEnvio").asText()).isEqualTo("2026-10-27T10:15:00.123456");
        assertThat(mensagem.get("identificadorExterno").asText()).isEqualTo("wamid.Exemplo");
        assertThat(mensagens.count()).isEqualTo(1);
    }

    @Test
    void permiteMensagensSemIdExternoEPreencheData() throws Exception {
        JsonNode primeira = registrar(chamadoId, dados());
        var body = dados();
        body.put("identificadorExterno", "   ");
        body.put("dataEnvio", null);
        JsonNode segunda = registrar(chamadoId, body);
        assertThat(primeira.get("dataEnvio").asText()).isNotBlank();
        assertThat(segunda.get("dataEnvio").asText()).isNotBlank();
        assertThat(segunda.get("identificadorExterno").isNull()).isTrue();
        assertThat(mensagens.count()).isEqualTo(2);
    }

    @Test
    void listaSomenteMensagensDoChamadoEmOrdemCronologicaComDesempatePorId() throws Exception {
        var body = dados();
        body.put("dataEnvio", "2026-10-27T12:00:00");
        long ultima = registrar(chamadoId, body).get("id").asLong();
        body.put("dataEnvio", "2026-10-27T09:00:00");
        long primeira = registrar(chamadoId, body).get("id").asLong();
        long segunda = registrar(chamadoId, body).get("id").asLong();
        registrar(criarChamado(), body);

        mvc.perform(get("/api/chamados/{id}/mensagens", chamadoId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(primeira))
                .andExpect(jsonPath("$[1].id").value(segunda))
                .andExpect(jsonPath("$[2].id").value(ultima));
    }

    @Test
    void retornaListaVaziaParaChamadoSemMensagensE404ParaRecursosAusentes() throws Exception {
        mvc.perform(get("/api/chamados/{id}/mensagens", chamadoId))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/chamados/{id}/mensagens", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/mensagens/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(post("/api/chamados/{id}/mensagens", Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(dados())))
                .andExpect(status().isNotFound());
        assertThat(mensagens.count()).isZero();
    }

    @Test
    void bloqueiaIdExternoDuplicadoInclusiveEmOutroChamadoSemAlterarOriginal() throws Exception {
        var body = dados();
        body.put("identificadorExterno", "wamid.unico");
        JsonNode original = registrar(chamadoId, body);
        body.put("identificadorExterno", " wamid.unico ");
        body.put("conteudo", "Tentativa de sobrescrever");
        for (long id : new long[]{chamadoId, criarChamado()}) {
            mvc.perform(post("/api/chamados/{id}/mensagens", id).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body)))
                    .andExpect(status().isConflict());
        }
        assertThat(mensagens.count()).isEqualTo(1);
        mvc.perform(get("/api/mensagens/{id}", original.get("id").asLong()))
                .andExpect(status().isOk()).andExpect(content().json(original.toString()));
        registrar(chamadoId, dados());
        assertThat(mensagens.count()).isEqualTo(2);
    }

    @Test
    void bloqueiaDuplicatasConcorrentes() throws Exception {
        var body = dados();
        body.put("identificadorExterno", "wamid.concorrente");
        String json = mapper.writeValueAsString(body);
        CountDownLatch prontas = new CountDownLatch(2);
        CountDownLatch iniciar = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> enviar = () -> {
                prontas.countDown();
                assertThat(iniciar.await(5, TimeUnit.SECONDS)).isTrue();
                return mvc.perform(post("/api/chamados/{id}/mensagens", chamadoId)
                                .contentType(MediaType.APPLICATION_JSON).content(json))
                        .andReturn().getResponse().getStatus();
            };
            var primeira = executor.submit(enviar);
            var segunda = executor.submit(enviar);
            assertThat(prontas.await(5, TimeUnit.SECONDS)).isTrue();
            iniciar.countDown();
            assertThat(java.util.List.of(primeira.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(mensagens.count()).isEqualTo(1);
        } finally {
            iniciar.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"remetente", "conteudo", "tipo"})
    void rejeitaCamposObrigatoriosAusentesNulosOuVazios(String campo) throws Exception {
        for (String valor : new String[]{null, "", "   "}) {
            var body = dados();
            body.put(campo, valor);
            rejeita(body);
        }
        var body = dados();
        body.remove(campo);
        rejeita(body);
        assertThat(mensagens.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"remetente,11", "conteudo,10001", "tipo,31", "identificadorExterno,256"})
    void rejeitaLimitesExcedidos(String campo, int tamanho) throws Exception {
        var body = dados();
        body.put(campo, "a".repeat(tamanho));
        rejeita(body);
        assertThat(mensagens.count()).isZero();
    }

    @Test
    void rejeitaRemetenteDesconhecidoEDataInvalida() throws Exception {
        var body = dados();
        body.put("remetente", "OUTRO");
        rejeita(body);
        body = dados();
        body.put("dataEnvio", "ontem");
        rejeita(body);
        assertThat(mensagens.count()).isZero();
    }

    private void rejeita(Map<String, Object> body) throws Exception {
        mvc.perform(post("/api/chamados/{id}/mensagens", chamadoId).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
