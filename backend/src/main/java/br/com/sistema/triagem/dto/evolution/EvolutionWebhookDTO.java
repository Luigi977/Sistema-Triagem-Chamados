package br.com.sistema.triagem.dto.evolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvolutionWebhookDTO(String event, String instance, JsonNode data) {}
