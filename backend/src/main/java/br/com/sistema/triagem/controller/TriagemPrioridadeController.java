package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.service.TriagemPrioridadeService;
import br.com.sistema.triagem.service.TriagemPrioridadeService.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chamados/{chamadoId}/triagens-prioridade")
public class TriagemPrioridadeController {
    private final TriagemPrioridadeService service;
    public TriagemPrioridadeController(TriagemPrioridadeService service) { this.service = service; }
    @GetMapping
    public List<Triagem> listar(@PathVariable long chamadoId) { return service.listar(chamadoId); }
    @PostMapping
    public ResponseEntity<Triagem> solicitar(@PathVariable long chamadoId, @RequestBody Solicitar request) {
        Triagem result = service.solicitar(chamadoId, request);
        return ResponseEntity.created(URI.create("/api/chamados/" + chamadoId + "/triagens-prioridade")).body(result);
    }
    @PostMapping("/{id}/decisao")
    public Triagem decidir(@PathVariable long chamadoId, @PathVariable long id, @RequestBody Decidir request) { return service.decidir(chamadoId, id, request); }
}
