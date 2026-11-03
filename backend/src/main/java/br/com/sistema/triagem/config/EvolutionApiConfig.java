package br.com.sistema.triagem.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(EvolutionApiConfig.Properties.class)
public class EvolutionApiConfig {
    @ConfigurationProperties(prefix = "evolution")
    public record Properties(boolean enabled, URI baseUrl, String instance, String apiKey,
                             String webhookToken, Duration connectTimeout, Duration readTimeout,
                             boolean managed, URI webhookUrl) {
        public Properties {
            baseUrl = baseUrl == null ? URI.create("http://localhost:8081") : baseUrl;
            instance = instance == null ? "" : instance;
            apiKey = apiKey == null ? "" : apiKey;
            webhookToken = webhookToken == null ? "" : webhookToken;
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
            readTimeout = readTimeout == null ? Duration.ofSeconds(8) : readTimeout;
            if (connectTimeout.isNegative() || connectTimeout.isZero()
                    || readTimeout.isNegative() || readTimeout.isZero())
                throw new IllegalArgumentException("Timeouts da Evolution devem ser positivos");
            if (enabled && (baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                    || baseUrl.getQuery() != null || baseUrl.getFragment() != null
                    || !java.util.Set.of("http", "https").contains(baseUrl.getScheme())
                    || !instance.matches("[A-Za-z0-9_-]{1,80}") || apiKey.isBlank()
                    || webhookToken.length() < 32))
                throw new IllegalArgumentException("Configure URL, instancia, api-key e webhook-token (32+ caracteres) da Evolution");
            if (managed && (!enabled || webhookUrl == null || webhookUrl.getHost() == null
                    || !java.util.Set.of("http", "https").contains(webhookUrl.getScheme())
                    || webhookUrl.getUserInfo() != null || webhookUrl.getQuery() != null || webhookUrl.getFragment() != null))
                throw new IllegalArgumentException("Modo gerenciado exige Evolution habilitada e webhook-url valido");
        }

        // Não incluir credenciais em logs, mesmo ao inspecionar configuração.
        @Override public String toString() { return "EvolutionProperties[enabled=" + enabled + "]"; }
    }
}
