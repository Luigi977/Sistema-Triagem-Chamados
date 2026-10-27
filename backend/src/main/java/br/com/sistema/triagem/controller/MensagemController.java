package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.dto.MensagemRequestDTO;
import br.com.sistema.triagem.dto.MensagemResponseDTO;
import br.com.sistema.triagem.service.MensagemService;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api")
public class MensagemController {

    private final MensagemService mensagemService;

    public MensagemController(MensagemService mensagemService) {
        this.mensagemService = mensagemService;
    }

    @GetMapping("/chamados/{id}/mensagens")
    public List<MensagemResponseDTO> listarPorChamado(@PathVariable Long id) {
        return mensagemService.listarPorChamado(id);
    }

    @GetMapping("/mensagens/{id}")
    public MensagemResponseDTO buscarPorId(@PathVariable Long id) {
        return mensagemService.buscarPorId(id);
    }

    @PostMapping("/chamados/{id}/mensagens")
    public ResponseEntity<MensagemResponseDTO> registrar(
            @PathVariable Long id, @RequestBody MensagemRequestDTO request) {
        MensagemResponseDTO mensagem = mensagemService.registrar(id, request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/mensagens/{id}")
                .buildAndExpand(mensagem.id())
                .toUri();
        return ResponseEntity.created(location).body(mensagem);
    }
}
