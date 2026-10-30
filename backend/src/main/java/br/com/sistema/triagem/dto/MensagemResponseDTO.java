package br.com.sistema.triagem.dto;

import br.com.sistema.triagem.model.entity.Mensagem;
import java.time.LocalDateTime;

public record MensagemResponseDTO(
        Long id,
        Long chamadoId,
        String remetente,
        String conteudo,
        String tipo,
        LocalDateTime dataEnvio,
        String identificadorExterno, Long autorId) {

    public static MensagemResponseDTO from(Mensagem mensagem) {
        return new MensagemResponseDTO(
                mensagem.getId(), mensagem.getChamado().getId(), mensagem.getRemetente(),
                mensagem.getConteudo(), mensagem.getTipo(), mensagem.getDataEnvio(),
                mensagem.getIdentificadorExterno(), mensagem.getAutorId());
    }
}
