package br.com.sistema.triagem.service;

import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.repository.ChamadoRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ChamadoService {

    private static final Set<String> STATUS_VALIDOS = Set.of(
            "ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_USUARIO", "ENCERRADO");
    private static final Set<String> STATUS_ABERTOS = Set.of(
            "ABERTO", "EM_ATENDIMENTO", "AGUARDANDO_USUARIO");

    private final ChamadoRepository chamadoRepository;

    public ChamadoService(ChamadoRepository chamadoRepository) {
        this.chamadoRepository = chamadoRepository;
    }

    public Chamado criarChamado(String telefoneCliente, String nomeCliente, String prioridadeSugerida) {
        String telefone = required(telefoneCliente, "telefoneCliente", 30);
        Chamado chamado = new Chamado();
        chamado.setTelefoneCliente(telefone);
        chamado.setNomeCliente(optional(nomeCliente, "nomeCliente", 150));
        chamado.setPrioridadeSugerida(optional(prioridadeSugerida, "prioridadeSugerida", 20));
        chamado.setStatus("ABERTO");
        chamado.setDataAbertura(LocalDateTime.now());

        chamadoRepository.findFirstByTelefoneClienteOrderByDataAberturaDesc(telefone)
                .filter(anterior -> "ENCERRADO".equals(anterior.getStatus()))
                .ifPresent(anterior -> chamado.setChamadoAnteriorId(anterior.getId()));

        return chamadoRepository.save(chamado);
    }

    @Transactional(readOnly = true)
    public Page<Chamado> listarChamados(
            String status, String telefone, String prioridadeFinal, String responsavel, Pageable pageable) {
        String statusNormalizado = optional(status);
        if (statusNormalizado != null) {
            statusNormalizado = normalizeStatus(statusNormalizado);
        }

        String telefoneFiltro = optional(telefone);
        String prioridadeFiltro = optional(prioridadeFinal);
        String responsavelFiltro = optional(responsavel);

        Specification<Chamado> specification = (root, query, builder) -> builder.conjunction();
        if (statusNormalizado != null) {
            String filtro = statusNormalizado;
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), filtro));
        }
        if (telefoneFiltro != null) {
            String filtro = "%" + telefoneFiltro.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("telefoneCliente")), filtro));
        }
        if (prioridadeFiltro != null) {
            String filtro = prioridadeFiltro.toLowerCase(Locale.ROOT);
            specification = specification.and((root, query, builder) ->
                    builder.equal(builder.lower(root.get("prioridadeFinal")), filtro));
        }
        if (responsavelFiltro != null) {
            String filtro = "%" + responsavelFiltro.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) ->
                    builder.like(builder.lower(root.get("responsavel")), filtro));
        }

        return chamadoRepository.findAll(specification, pageable);
    }

    @Transactional(readOnly = true)
    public Chamado buscarPorId(Long id) {
        return chamadoRepository.findById(id)
                .orElseThrow(() -> notFound(id));
    }

    @Transactional(readOnly = true)
    public Page<Chamado> listarChamadosAbertos(Pageable pageable) {
        return chamadoRepository.findByStatusIn(STATUS_ABERTOS, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Chamado> buscarPorTelefone(String telefone, Pageable pageable) {
        return chamadoRepository.findByTelefoneCliente(required(telefone, "telefone"), pageable);
    }

    public Chamado alterarStatus(Long id, String status) {
        Chamado chamado = buscarPorId(id);
        if ("ENCERRADO".equals(chamado.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Chamado encerrado não pode ser alterado. Abra um novo chamado.");
        }

        String novoStatus = normalizeStatus(status);
        if ("ENCERRADO".equals(novoStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Use o endpoint de encerramento para informar a causa real.");
        }

        chamado.setStatus(novoStatus);
        return chamadoRepository.save(chamado);
    }

    public Chamado alterarPrioridadeFinal(Long id, String prioridadeFinal) {
        Chamado chamado = buscarPorId(id);
        validarNaoEncerrado(chamado);
        chamado.setPrioridadeFinal(required(prioridadeFinal, "prioridadeFinal", 20));
        return chamadoRepository.save(chamado);
    }

    public Chamado atribuirResponsavel(Long id, String responsavel) {
        Chamado chamado = buscarPorId(id);
        validarNaoEncerrado(chamado);
        chamado.setResponsavel(required(responsavel, "responsavel", 150));
        return chamadoRepository.save(chamado);
    }

    public Chamado encerrarChamado(Long id, String causaReal) {
        Chamado chamado = buscarPorId(id);
        if ("ENCERRADO".equals(chamado.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O chamado já está encerrado.");
        }

        chamado.setCausaReal(required(causaReal, "causaReal"));
        chamado.setStatus("ENCERRADO");
        chamado.setDataEncerramento(LocalDateTime.now());
        return chamadoRepository.save(chamado);
    }

    private static void validarNaoEncerrado(Chamado chamado) {
        if ("ENCERRADO".equals(chamado.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Chamado encerrado não pode ser alterado. Abra um novo chamado.");
        }
    }

    private static String normalizeStatus(String status) {
        String normalized = required(status, "status", 30).toUpperCase(Locale.ROOT);
        if (!STATUS_VALIDOS.contains(normalized)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Status inválido. Valores permitidos: " + STATUS_VALIDOS);
        }
        return normalized;
    }

    private static String required(String value, String field) {
        String normalized = optional(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " é obrigatório.");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = required(value, field);
        if (normalized.length() > maxLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, field + " deve ter no máximo " + maxLength + " caracteres.");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        String normalized = optional(value);
        if (normalized != null && normalized.length() > maxLength) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, field + " deve ter no máximo " + maxLength + " caracteres.");
        }
        return normalized;
    }

    private static String optional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Chamado não encontrado: " + id);
    }
}
