package br.com.sistema.triagem.service;

import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.dto.ChamadoRequests.CriarChamadoRequest;
import br.com.sistema.triagem.dto.ChamadoRequests.AtualizarDetalhesRequest;
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

    private static final Set<String> PRIORIDADES = Set.of("BAIXA", "MEDIA", "ALTA", "CRITICA");
    private static final Set<String> NATUREZAS = Set.of("INCIDENTE", "REQUISICAO", "DUVIDA");
    private static final Set<String> EQUIPES = Set.of("N1", "N2");
    private static final Set<String> CANAIS = Set.of("WHATSAPP", "MANUAL");

    private final ChamadoRepository chamadoRepository;
    private final AcessoService acesso;

    public ChamadoService(ChamadoRepository chamadoRepository, AcessoService acesso) {
        this.chamadoRepository = chamadoRepository;
        this.acesso = acesso;
    }

    public Chamado criarChamado(CriarChamadoRequest request) {
        String telefone = required(request.telefoneCliente(), "telefoneCliente", 30);
        Chamado chamado = new Chamado();
        chamado.setTelefoneCliente(telefone);
        chamado.setNomeCliente(optional(request.nomeCliente(), "nomeCliente", 150));
        chamado.setPrioridadeSugerida(optionalCategory(request.prioridadeSugerida(), "prioridadeSugerida", PRIORIDADES));
        chamado.setResumo(optional(request.resumo(), "resumo", 200));
        chamado.setDescricao(optional(request.descricao(), "descricao", 10000));
        chamado.setNatureza(optionalCategory(request.natureza(), "natureza", NATUREZAS));
        chamado.setSetor(optional(request.setor(), "setor", 150));
        chamado.setCanal(request.canal() == null ? "MANUAL" : category(request.canal(), "canal", CANAIS));
        chamado.setEquipe(optionalCategory(request.equipe(), "equipe", EQUIPES));
        if (!"ADMIN".equals(acesso.atual().perfil())) {
            if (chamado.getEquipe() == null) chamado.setEquipe(acesso.atual().equipe());
            acesso.editar(chamado);
        }
        chamado.setStatus("ABERTO");
        chamado.setDataAbertura(LocalDateTime.now());

        chamadoRepository.findFirstByTelefoneClienteOrderByDataAberturaDesc(telefone)
                .filter(anterior -> "ENCERRADO".equals(anterior.getStatus()))
                .ifPresent(anterior -> chamado.setChamadoAnteriorId(anterior.getId()));

        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "CRIACAO");
        return salvo;
    }

    @Transactional(readOnly = true)
    public Page<Chamado> listarChamados(
            String status, String telefone, String prioridadeFinal, String responsavel, Pageable pageable) {
        String statusNormalizado = optional(status);
        if (statusNormalizado != null) {
            statusNormalizado = normalizeStatus(statusNormalizado);
        }

        String telefoneFiltro = optional(telefone);
        String prioridadeFiltro = optionalCategory(prioridadeFinal, "prioridadeFinal", PRIORIDADES);
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
        Chamado chamado = chamadoRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        acesso.editar(chamado);
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
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "STATUS");
        return salvo;
    }

    public Chamado alterarPrioridadeFinal(Long id, String prioridadeFinal) {
        Chamado chamado = chamadoRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        acesso.editar(chamado);
        validarNaoEncerrado(chamado);
        chamado.setPrioridadeFinal(category(prioridadeFinal, "prioridadeFinal", PRIORIDADES));
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "PRIORIDADE");
        return salvo;
    }

    public Chamado atribuirResponsavel(Long id, Long responsavelId) {
        Chamado chamado = chamadoRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        acesso.editar(chamado);
        validarNaoEncerrado(chamado);
        if (responsavelId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "responsavelId obrigatorio");
        var tecnico = acesso.buscar(responsavelId);
        if (!tecnico.ativo() || !tecnico.equipe().equals(chamado.getEquipe()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tecnico inativo ou de outra equipe");
        if (!"ADMIN".equals(acesso.atual().perfil()) && !acesso.atual().id().equals(responsavelId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        chamado.setResponsavelId(responsavelId);
        chamado.setResponsavel(tecnico.nome());
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "ATRIBUICAO");
        return salvo;
    }

    public Chamado atualizarDetalhes(Long id, AtualizarDetalhesRequest request) {
        Chamado chamado = chamadoRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        acesso.editar(chamado);
        validarNaoEncerrado(chamado);
        if (request.resumo() != null) chamado.setResumo(required(request.resumo(), "resumo", 200));
        if (request.descricao() != null) chamado.setDescricao(required(request.descricao(), "descricao", 10000));
        if (request.natureza() != null) chamado.setNatureza(category(request.natureza(), "natureza", NATUREZAS));
        if (request.setor() != null) chamado.setSetor(required(request.setor(), "setor", 150));
        if (request.canal() != null) chamado.setCanal(category(request.canal(), "canal", CANAIS));
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "DETALHES");
        return salvo;
    }

    public Chamado alterarEquipe(Long id, String equipe) {
        Chamado chamado = chamadoRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        acesso.editar(chamado);
        validarNaoEncerrado(chamado);
        acesso.admin();
        String novaEquipe = category(equipe, "equipe", EQUIPES);
        if (!novaEquipe.equals(chamado.getEquipe())) {
            chamado.setResponsavelId(null);
            chamado.setResponsavel(null);
        }
        chamado.setEquipe(novaEquipe);
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "EQUIPE");
        return salvo;
    }

    public Chamado encerrarChamado(Long id, String causaReal, String solucaoAplicada) {
        Chamado chamado = chamadoRepository.findForUpdate(id).orElseThrow(() -> notFound(id));
        acesso.editar(chamado);
        if ("ENCERRADO".equals(chamado.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O chamado já está encerrado.");
        }

        String causa = required(causaReal, "causaReal", 10000);
        String solucao = required(solucaoAplicada, "solucaoAplicada", 10000);
        chamado.setCausaReal(causa);
        chamado.setSolucaoAplicada(solucao);
        chamado.setStatus("ENCERRADO");
        chamado.setDataEncerramento(LocalDateTime.now());
        Chamado salvo = chamadoRepository.saveAndFlush(chamado);
        acesso.registrar(salvo, "ENCERRAMENTO");
        return salvo;
    }

    @Transactional(readOnly = true)
    public java.util.List<AcessoService.Operacao> operacoes(Long id) {
        buscarPorId(id);
        return acesso.historico(id);
    }

    private static void validarNaoEncerrado(Chamado chamado) {
        if ("ENCERRADO".equals(chamado.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Chamado encerrado não pode ser alterado. Abra um novo chamado.");
        }
    }

    private static String optionalCategory(String value, String field, Set<String> values) {
        return value == null ? null : category(value, field, values);
    }

    private static String category(String value, String field, Set<String> values) {
        String normalized = required(value, field).toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    field + " inválido. Valores permitidos: " + values);
        }
        return normalized;
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
