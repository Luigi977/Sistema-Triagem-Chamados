package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.dto.ChamadoRequests.*;
import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.service.ChamadoService;
import java.net.URI;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/chamados")
public class ChamadoController {

    private final ChamadoService chamadoService;

    public ChamadoController(ChamadoService chamadoService) {
        this.chamadoService = chamadoService;
    }

    @PostMapping
    public ResponseEntity<Chamado> criarChamado(@RequestBody CriarChamadoRequest request) {
        Chamado chamado = chamadoService.criarChamado(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(chamado.getId())
                .toUri();
        return ResponseEntity.created(location).body(chamado);
    }

    @GetMapping
    public Page<Chamado> listarChamados(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String telefone,
            @RequestParam(required = false) String prioridadeFinal,
            @RequestParam(required = false) String responsavel,
            @PageableDefault(size = 20, sort = "dataAbertura", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return chamadoService.listarChamados(status, telefone, prioridadeFinal, responsavel, pageable);
    }

    @GetMapping("/{id}")
    public Chamado buscarPorId(@PathVariable Long id) {
        return chamadoService.buscarPorId(id);
    }

    @GetMapping("/abertos")
    public Page<Chamado> listarChamadosAbertos(
            @PageableDefault(size = 20, sort = "dataAbertura", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return chamadoService.listarChamadosAbertos(pageable);
    }

    @GetMapping("/cliente/{telefone}")
    public Page<Chamado> buscarPorTelefone(
            @PathVariable String telefone,
            @PageableDefault(size = 20, sort = "dataAbertura", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return chamadoService.buscarPorTelefone(telefone, pageable);
    }

    @GetMapping("/{id}/operacoes")
    public java.util.List<br.com.sistema.triagem.service.AcessoService.Operacao> operacoes(@PathVariable Long id) {
        return chamadoService.operacoes(id);
    }

    @PatchMapping("/{id}/status")
    public Chamado alterarStatus(@PathVariable Long id, @RequestBody AlterarStatusRequest request) {
        return chamadoService.alterarStatus(id, request.status());
    }

    @PatchMapping("/{id}/prioridade")
    public Chamado alterarPrioridadeFinal(
            @PathVariable Long id, @RequestBody AlterarPrioridadeRequest request) {
        return chamadoService.alterarPrioridadeFinal(id, request.prioridadeFinal());
    }

    @PatchMapping("/{id}/responsavel")
    public Chamado atribuirResponsavel(
            @PathVariable Long id, @RequestBody AtribuirResponsavelRequest request) {
        return chamadoService.atribuirResponsavel(id, request.responsavelId());
    }

    @PatchMapping("/{id}/detalhes")
    public Chamado atualizarDetalhes(@PathVariable Long id, @RequestBody AtualizarDetalhesRequest request) {
        return chamadoService.atualizarDetalhes(id, request);
    }

    @PatchMapping("/{id}/equipe")
    public Chamado alterarEquipe(@PathVariable Long id, @RequestBody AlterarEquipeRequest request) {
        return chamadoService.alterarEquipe(id, request.equipe());
    }

    @PostMapping("/{id}/encerrar")
    public Chamado encerrarChamado(@PathVariable Long id, @RequestBody EncerrarChamadoRequest request) {
        return chamadoService.encerrarChamado(id, request.causaReal(), request.solucaoAplicada());
    }

}
