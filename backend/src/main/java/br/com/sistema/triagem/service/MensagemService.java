package br.com.sistema.triagem.service;

import br.com.sistema.triagem.dto.MensagemRequestDTO;
import br.com.sistema.triagem.dto.MensagemResponseDTO;
import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.model.entity.Mensagem;
import br.com.sistema.triagem.repository.ChamadoRepository;
import br.com.sistema.triagem.repository.MensagemRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class MensagemService {

    private static final Set<String> REMETENTES = Set.of("CLIENTE", "TECNICO", "SISTEMA");

    private final MensagemRepository mensagemRepository;
    private final ChamadoRepository chamadoRepository;

    public MensagemService(MensagemRepository mensagemRepository, ChamadoRepository chamadoRepository) {
        this.mensagemRepository = mensagemRepository;
        this.chamadoRepository = chamadoRepository;
    }

    public MensagemResponseDTO registrar(Long chamadoId, MensagemRequestDTO request) {
        Chamado chamado = chamadoRepository.findById(chamadoId)
                .orElseThrow(() -> chamadoNaoEncontrado(chamadoId));
        String remetente = required(request.remetente(), "remetente", 10).toUpperCase(Locale.ROOT);
        if (!REMETENTES.contains(remetente)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "remetente inválido. Valores permitidos: CLIENTE, TECNICO, SISTEMA.");
        }
        required(request.conteudo(), "conteudo", 10000);
        String tipo = required(request.tipo(), "tipo", 30).toUpperCase(Locale.ROOT);
        String identificadorExterno = optional(request.identificadorExterno(), "identificadorExterno", 255);

        Mensagem mensagem = new Mensagem();
        mensagem.setChamado(chamado);
        mensagem.setRemetente(remetente);
        mensagem.setConteudo(request.conteudo());
        mensagem.setTipo(tipo);
        LocalDateTime dataEnvio = request.dataEnvio() == null ? LocalDateTime.now() : request.dataEnvio();
        mensagem.setDataEnvio(dataEnvio.truncatedTo(ChronoUnit.MICROS));
        mensagem.setIdentificadorExterno(identificadorExterno);
        
        try {
            return MensagemResponseDTO.from(mensagemRepository.saveAndFlush(mensagem));
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().toLowerCase(Locale.ROOT)
                                .contains("uk_mensagens_identificador_externo")) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Já existe uma mensagem com este identificador externo.", exception);
                }
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<MensagemResponseDTO> listarPorChamado(Long chamadoId) {
        if (!chamadoRepository.existsById(chamadoId)) {
            throw chamadoNaoEncontrado(chamadoId);
        }
        return mensagemRepository.findByChamadoIdOrderByDataEnvioAscIdAsc(chamadoId).stream()
                .map(MensagemResponseDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MensagemResponseDTO buscarPorId(Long id) {
        return mensagemRepository.findById(id)
                .map(MensagemResponseDTO::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Mensagem não encontrada: " + id));
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = optional(value, field, maxLength);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " é obrigatório.");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " deve ter no máximo " + maxLength + " caracteres.");
        }
        return value.trim();
    }

    private static ResponseStatusException chamadoNaoEncontrado(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Chamado não encontrado: " + id);
    }
}
