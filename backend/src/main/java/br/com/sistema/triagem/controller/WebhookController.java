package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.dto.evolution.EvolutionWebhookDTO;
import br.com.sistema.triagem.service.WebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks/evolution")
public class WebhookController {
    private final WebhookService service;
    public WebhookController(WebhookService service) { this.service = service; }

    @PostMapping(consumes = "application/json")
    public ResponseEntity<WebhookService.Recebimento> receber(
            @RequestHeader(value = "X-Webhook-Token", required = false) String token,
            @RequestBody EvolutionWebhookDTO evento) {
        var resultado = service.receber(token, evento);
        return ResponseEntity.status(resultado.aceitas() > 0 ? 202 : 200).body(resultado);
    }
}
