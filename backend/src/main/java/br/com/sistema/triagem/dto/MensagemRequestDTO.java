package br.com.sistema.triagem.dto;

import java.time.LocalDateTime;

public record MensagemRequestDTO(
        String remetente,
        String conteudo,
        String tipo,
        LocalDateTime dataEnvio,
        String identificadorExterno) {
}
