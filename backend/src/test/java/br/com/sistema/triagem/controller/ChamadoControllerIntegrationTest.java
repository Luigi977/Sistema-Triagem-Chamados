package br.com.sistema.triagem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import br.com.sistema.triagem.repository.ChamadoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:chamados_http;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@org.springframework.security.test.context.support.WithMockUser(username = "admin", roles = "ADMIN")
@AutoConfigureMockMvc
class ChamadoControllerIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ChamadoRepository repository;

    @BeforeEach
    void limparBanco() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM tecnicos WHERE login='admin'", Integer.class) == 0)
            jdbc.update("INSERT INTO tecnicos(login,nome,senha_hash,perfil,equipe) VALUES ('admin','Admin teste','nao-utilizado','ADMIN','N1')");
        repository.deleteAll();
    }

    private long criar(String body) throws Exception {
        String json = mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(json).get("id").asLong();
    }

    private long criarMinimo() throws Exception {
        return criar("{\"telefoneCliente\":\"11999990000\"}");
    }

    @Test
    void persisteDadosCompletosENormalizaCategorias() throws Exception {
        long id = criar("""
            {"telefoneCliente":"11999990000","nomeCliente":"Cliente de teste",
             "resumo":" Computador não liga ","descricao":"Sem sinal de energia",
             "natureza":" incidente ","setor":"Financeiro","canal":"whatsapp",
             "equipe":"n1","prioridadeSugerida":" critica "}
            """);
        mvc.perform(get("/api/chamados/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumo").value("Computador não liga"))
                .andExpect(jsonPath("$.descricao").value("Sem sinal de energia"))
                .andExpect(jsonPath("$.natureza").value("INCIDENTE"))
                .andExpect(jsonPath("$.setor").value("Financeiro"))
                .andExpect(jsonPath("$.canal").value("WHATSAPP"))
                .andExpect(jsonPath("$.equipe").value("N1"))
                .andExpect(jsonPath("$.prioridadeSugerida").value("CRITICA"))
                .andExpect(jsonPath("$.prioridadeFinal").doesNotExist());
    }

    @Test
    void mantemCriacaoMinimaCompativel() throws Exception {
        long id = criarMinimo();
        mvc.perform(get("/api/chamados/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canal").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("ABERTO"));
    }

    @ParameterizedTest
    @CsvSource({"natureza,OUTRA", "canal,EMAIL", "equipe,N3", "prioridadeSugerida,URGENTE"})
    void rejeitaCategoriasDesconhecidas(String campo, String valor) throws Exception {
        mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("telefoneCliente", "11999990000", campo, valor))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/chamados")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void atualizaDetalhesEquipeEPrioridadeSemApagarSugestao() throws Exception {
        long id = criar("""
            {"telefoneCliente":"11999990000","resumo":"Original","setor":"TI","prioridadeSugerida":"ALTA"}
            """);
        mvc.perform(patch("/api/chamados/{id}/detalhes", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"descricao\":\"Detalhe novo\",\"natureza\":\"duvida\",\"resumo\":null}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/chamados/{id}/equipe", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"equipe\":\"n2\"}")).andExpect(status().isOk());
        mvc.perform(patch("/api/chamados/{id}/prioridade", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"prioridadeFinal\":\"media\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/chamados/{id}", id))
                .andExpect(jsonPath("$.resumo").value("Original"))
                .andExpect(jsonPath("$.setor").value("TI"))
                .andExpect(jsonPath("$.descricao").value("Detalhe novo"))
                .andExpect(jsonPath("$.natureza").value("DUVIDA"))
                .andExpect(jsonPath("$.equipe").value("N2"))
                .andExpect(jsonPath("$.prioridadeSugerida").value("ALTA"))
                .andExpect(jsonPath("$.prioridadeFinal").value("MEDIA"));
    }

    @Test
    void falhaDeValidacaoNaoPersisteEdicaoParcial() throws Exception {
        long id = criar("{\"telefoneCliente\":\"11999990000\",\"resumo\":\"Original\"}");
        mvc.perform(patch("/api/chamados/{id}/detalhes", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumo\":\"Alterado\",\"natureza\":\"INVALIDA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/chamados/{id}", id)).andExpect(jsonPath("$.resumo").value("Original"));
    }

    @Test
    void rejeitaTextoVazioELimitesExcedidos() throws Exception {
        long id = criarMinimo();
        for (String resumo : new String[]{" ", "a".repeat(201)}) {
            mvc.perform(patch("/api/chamados/{id}/detalhes", id).contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(Map.of("resumo", resumo))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/chamados").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("telefoneCliente", "11999990000", "descricao", "a".repeat(10001)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exigeCausaESolucaoAntesDeEncerrar() throws Exception {
        long id = criarMinimo();
        for (String body : new String[]{"{}", "{\"causaReal\":\"Fonte queimada\"}",
                "{\"causaReal\":\"Fonte queimada\",\"solucaoAplicada\":\" \"}", "{\"solucaoAplicada\":\"Troca\"}"}) {
            mvc.perform(post("/api/chamados/{id}/encerrar", id).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/chamados/{id}", id)).andExpect(jsonPath("$.status").value("ABERTO"))
                .andExpect(jsonPath("$.causaReal").doesNotExist())
                .andExpect(jsonPath("$.dataEncerramento").doesNotExist());
    }

    @Test
    void encerraPreservaHistoricoEBloqueiaNovasAlteracoes() throws Exception {
        long id = criarMinimo();
        mvc.perform(post("/api/chamados/{id}/encerrar", id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"causaReal\":\"Fonte queimada\",\"solucaoAplicada\":\"Substituição da fonte\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/chamados/{id}", id)).andExpect(jsonPath("$.status").value("ENCERRADO"))
                .andExpect(jsonPath("$.causaReal").value("Fonte queimada"))
                .andExpect(jsonPath("$.solucaoAplicada").value("Substituição da fonte"))
                .andExpect(jsonPath("$.dataEncerramento").isNotEmpty());
        for (var change : Map.of("detalhes", "{\"resumo\":\"Novo\"}", "equipe", "{\"equipe\":\"N2\"}",
                "prioridade", "{\"prioridadeFinal\":\"BAIXA\"}").entrySet()) {
            mvc.perform(patch("/api/chamados/{id}/" + change.getKey(), id).contentType(MediaType.APPLICATION_JSON)
                    .content(change.getValue())).andExpect(status().isConflict());
        }
        long novoId = criarMinimo();
        mvc.perform(get("/api/chamados/{id}", novoId)).andExpect(jsonPath("$.chamadoAnteriorId").value(id));
    }

    @Test
    void retorna404ParaChamadoAusente() throws Exception {
        mvc.perform(patch("/api/chamados/999999/detalhes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"resumo\":\"Novo\"}")).andExpect(status().isNotFound());
    }
}
