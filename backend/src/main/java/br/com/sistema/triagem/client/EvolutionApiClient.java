package br.com.sistema.triagem.client;

import br.com.sistema.triagem.config.EvolutionApiConfig.Properties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.Map;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
public class EvolutionApiClient {
    public record Status(String instancia, String estado) {}
    public record Envio(String id) {}
    public record Conexao(String instancia, String estado, String qrCode) {}
    private final Properties config;
    private final RestClient client;

    public EvolutionApiClient(Properties config) {
        this.config = config;
        var http = HttpClient.newBuilder().connectTimeout(config.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(config.readTimeout());
        client = RestClient.builder().baseUrl(config.baseUrl().toString().replaceAll("/+$", ""))
                .requestFactory(factory).defaultHeader("apikey", config.apiKey()).build();
    }

    public void exigirHabilitado() {
        if (!config.enabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Integração Evolution desativada");
    }

    public Status status() {
        exigirHabilitado();
        try {
            JsonNode body = client.get().uri("/instance/connectionState/{instance}", config.instance()).retrieve().body(JsonNode.class);
            String estado = body == null ? "" : body.path("instance").path("state").asText();
            if (!java.util.Set.of("open", "close", "connecting").contains(estado)) throw respostaInvalida();
            return new Status(config.instance(), estado);
        } catch (RestClientException e) { throw traduzir(e); }
    }

    public Envio enviarTexto(String destinatario, String texto) {
        exigirHabilitado();
        try {
            JsonNode body = client.post().uri("/message/sendText/{instance}", config.instance())
                    .body(Map.of("number", destinatario, "text", texto))
                    .retrieve().body(JsonNode.class);
            String id = body == null ? "" : body.path("key").path("id").asText();
            if (!id.matches("[A-Za-z0-9._:-]{1,150}") || !body.path("key").path("fromMe").asBoolean())
                throw respostaInvalida();
            return new Envio(id);
        } catch (RestClientException e) { throw traduzir(e); }
    }

    /** Provisiona somente a instância configurada; não aceita URL ou destinatário do painel. */
    public synchronized void prepararInstancia() {
        exigirHabilitado();
        if (!config.managed()) throw new ResponseStatusException(CONFLICT, "Evolution não está no modo gerenciado");
        try {
            if (!instanciaExiste()) {
                try {
                    client.post().uri("/instance/create").body(Map.of(
                            "instanceName", config.instance(), "integration", "WHATSAPP-BAILEYS", "qrcode", false,
                            "groupsIgnore", true, "syncFullHistory", false, "readMessages", false,
                            "readStatus", false, "webhook", webhook())).retrieve().toBodilessEntity();
                } catch (RestClientResponseException e) {
                    // Outra réplica pode ter criado a mesma instância após nossa consulta.
                    if (e.getStatusCode().value() != 403 && e.getStatusCode().value() != 409) throw e;
                    if (!instanciaExiste()) throw e;
                }
            }
            client.post().uri("/webhook/set/{instance}", config.instance())
                    .body(Map.of("webhook", webhook())).retrieve().toBodilessEntity();
        } catch (RestClientException e) { throw traduzir(e); }
    }

    private boolean instanciaExiste() {
        try {
            JsonNode existentes = client.get().uri("/instance/fetchInstances?instanceName={instance}", config.instance())
                    .retrieve().body(JsonNode.class);
            if (existentes == null || !existentes.isArray()) throw respostaInvalida();
            return !existentes.isEmpty();
        } catch (RestClientResponseException e) {
            // A Evolution 2.3.7 responde 404 para filtro por uma instância ainda inexistente.
            if (e.getStatusCode().value() == 404) return false;
            throw e;
        }
    }

    private Map<String, Object> webhook() {
        return Map.of("enabled", true, "url", config.webhookUrl().toString(), "byEvents", false, "base64", false,
                "headers", Map.of("X-Webhook-Token", config.webhookToken()), "events", java.util.List.of("MESSAGES_UPSERT"));
    }

    public Conexao conectar() {
        prepararInstancia();
        if ("open".equals(status().estado())) return new Conexao(config.instance(), "open", null);
        try {
            JsonNode body = client.get().uri("/instance/connect/{instance}", config.instance()).retrieve().body(JsonNode.class);
            if (body == null || body.path("error").asBoolean(false)) throw respostaInvalida();
            if ("open".equals(body.path("instance").path("state").asText()))
                return new Conexao(config.instance(), "open", null);
            String qr = body.path("base64").asText(null);
            if (qr == null) qr = body.path("qrcode").path("base64").asText(null);
            if (qr != null && (qr.length() > 2000000 || !qr.matches("data:image/png;base64,[A-Za-z0-9+/=\\r\\n]+")))
                throw respostaInvalida();
            // Pode levar alguns segundos para o WhatsApp gerar o primeiro QR.
            return new Conexao(config.instance(), "connecting", qr);
        } catch (RestClientException e) { throw traduzir(e); }
    }

    private ResponseStatusException respostaInvalida() { return new ResponseStatusException(BAD_GATEWAY, "Resposta inválida da Evolution"); }
    private ResponseStatusException traduzir(RestClientException e) {
        for (Throwable cause=e; cause!=null; cause=cause.getCause())
            if (cause instanceof HttpTimeoutException || cause instanceof java.net.SocketTimeoutException)
                return new ResponseStatusException(GATEWAY_TIMEOUT, "Tempo de comunicação com Evolution excedido; confirme o histórico antes de reenviar");
        // Nunca devolver corpo remoto: ele pode incluir api-key ou dados de outros contatos.
        return new ResponseStatusException(BAD_GATEWAY, "Falha de comunicação com Evolution; confirme o histórico antes de reenviar");
    }
}
