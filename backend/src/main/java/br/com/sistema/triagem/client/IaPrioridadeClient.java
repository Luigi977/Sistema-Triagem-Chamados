package br.com.sistema.triagem.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
public class IaPrioridadeClient {
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final String url;
    private final boolean enabled;
    private final Duration timeout;
    private final String feedbackToken;
    public IaPrioridadeClient(ObjectMapper mapper,
            @Value("${ia.enabled:false}") boolean enabled,
            @Value("${ia.base-url:http://localhost:8000}") String url,
            @Value("${ia.timeout:10s}") Duration timeout,
            @Value("${ia.feedback-token:}") String feedbackToken) {
        this.mapper = mapper; this.enabled = enabled; this.url = url; this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.feedbackToken = feedbackToken;
    }
    public boolean enabled() { return enabled; }
    public boolean feedbackEnabled() { return enabled && feedbackToken.length() >= 32; }
    public void enviarFeedback(JsonNode payload) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(url + "/learning/feedback"))
                .timeout(timeout).header("Content-Type", "application/json").header("X-Feedback-Token", feedbackToken)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 && response.statusCode() != 201)
            throw new IllegalStateException("Feedback nao recebido");
        var receipt = mapper.readTree(response.body());
        if (!receipt.path("evento_id").asText().equals(payload.path("evento_id").asText())
                || !"RECEBIDO".equals(receipt.path("estado").asText()))
            throw new IllegalStateException("Recibo de feedback invalido");
    }
    public JsonNode recomendar(JsonNode input) {
        if (!enabled) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "IA desativada");
        try {
            var request = HttpRequest.newBuilder(URI.create(url + "/predict/priority"))
                    .timeout(timeout).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(input))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 503)
                throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Modelo de IA indisponivel");
            if (response.statusCode() != 200 || response.body().length() > 64000)
                throw new ResponseStatusException(BAD_GATEWAY, "Resposta invalida da IA");
            JsonNode result = mapper.readTree(response.body());
            validate(result);
            return result;
        } catch (ResponseStatusException e) { throw e; }
        catch (HttpTimeoutException e) { throw new ResponseStatusException(GATEWAY_TIMEOUT, "Tempo limite da IA"); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(BAD_GATEWAY, "IA interrompida");
        } catch (Exception e) { throw new ResponseStatusException(BAD_GATEWAY, "Falha ao consultar IA"); }
    }
    private static void validate(JsonNode n) {
        if (n == null || !n.isObject()) throw new IllegalArgumentException();
        var priorities = Set.of("BAIXA", "MEDIA", "ALTA", "CRITICA");
        for (String key : new String[]{"prioridade_recomendada", "prioridade_base", "prioridade_matriz"})
            if (!priorities.contains(n.path(key).asText())) throw new IllegalArgumentException();
        for (String key : new String[]{"impacto", "urgencia"}) {
            if (!Set.of("BAIXO", "MEDIO", "ALTO").contains(n.path(key).asText())) throw new IllegalArgumentException();
            String origin = n.path("origem_" + key).asText();
            JsonNode confidence = n.get("confianca_" + key);
            if (!Set.of("INFORMADO", "MODELO").contains(origin) || confidence == null) throw new IllegalArgumentException();
            if ("MODELO".equals(origin) && (!confidence.isNumber() || confidence.asDouble() < 0 || confidence.asDouble() > 1))
                throw new IllegalArgumentException();
            if ("INFORMADO".equals(origin) && !confidence.isNull()) throw new IllegalArgumentException();
        }
        String method = n.path("metodo_prioridade").asText();
        JsonNode pc = n.get("confianca_prioridade");
        if (!Set.of("MATRIZ", "FEEDBACK_TECNICO").contains(method) || pc == null
                || ("MATRIZ".equals(method) && !pc.isNull())
                || ("FEEDBACK_TECNICO".equals(method) && (!pc.isNumber() || pc.asDouble() < 0 || pc.asDouble() > 1)))
            throw new IllegalArgumentException();
        if (!Set.of("INFORMADO", "MODELO", "MISTO").contains(n.path("origem").asText())
                || !n.path("revisao_humana_obrigatoria").isBoolean() || !n.path("revisao_humana_obrigatoria").asBoolean()
                || !n.path("modelo_sintetico").isBoolean() || !n.path("baixa_confianca").isBoolean()
                || n.path("versao_matriz").asText().isBlank() || n.path("versao_regras").asText().isBlank()
                || !n.path("regras_aplicadas").isArray()) throw new IllegalArgumentException();
    }
}
