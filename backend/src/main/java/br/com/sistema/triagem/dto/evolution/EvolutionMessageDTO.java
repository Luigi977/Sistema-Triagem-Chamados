package br.com.sistema.triagem.dto.evolution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EvolutionMessageDTO(Key key, String pushName, JsonNode message, Long messageTimestamp) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Key(String id, String remoteJid, String remoteJidAlt, Boolean fromMe) {}
}
