package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.client.EvolutionApiClient;
import br.com.sistema.triagem.dto.MensagemResponseDTO;
import br.com.sistema.triagem.dto.evolution.ResponderWhatsAppDTO;
import br.com.sistema.triagem.service.WhatsAppService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WhatsAppController {
    private final WhatsAppService service;
    public WhatsAppController(WhatsAppService service) { this.service = service; }

    @GetMapping("/api/whatsapp/status")
    public EvolutionApiClient.Status status() { return service.status(); }

    @PostMapping("/api/whatsapp/conectar")
    public ResponseEntity<EvolutionApiClient.Conexao> conectar() {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(service.conectar());
    }

    @PostMapping("/api/chamados/{id}/responder")
    public ResponseEntity<MensagemResponseDTO> responder(@PathVariable Long id, @RequestBody ResponderWhatsAppDTO request) {
        var mensagem = service.responder(id, request);
        return ResponseEntity.created(URI.create("/api/mensagens/" + mensagem.id())).body(mensagem);
    }
}
