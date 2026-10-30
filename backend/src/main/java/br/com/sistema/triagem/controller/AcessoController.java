package br.com.sistema.triagem.controller;

import br.com.sistema.triagem.service.AcessoService;
import br.com.sistema.triagem.service.AcessoService.*;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AcessoController {
    private final AcessoService acesso;

    public AcessoController(AcessoService acesso) {
        this.acesso = acesso;
    }

    public record Login(String login, String senha) {
    }

    @PostMapping("/auth/login")
    public Sessao login(@RequestBody Login request) {
        return acesso.login(request.login(), request.senha());
    }

    @GetMapping("/auth/me")
    public Tecnico me() {
        return acesso.atual();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        acesso.logout(header.substring(7));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/tecnicos")
    public List<Tecnico> listar() {
        return acesso.listar();
    }

    @GetMapping("/equipes")
    public List<String> equipes() {
        return List.of("N1", "N2");
    }

    @PostMapping("/tecnicos")
    public ResponseEntity<Tecnico> cadastrar(@RequestBody Cadastro request) {
        acesso.admin();
        Tecnico tecnico = acesso.cadastrar(request);
        return ResponseEntity.created(URI.create("/api/tecnicos/" + tecnico.id())).body(tecnico);
    }

    @GetMapping("/tecnicos/{id}")
    public Tecnico buscar(@PathVariable Long id) {
        return acesso.buscar(id);
    }

    @PostMapping("/tecnicos/{id}/desativar")
    public Tecnico desativar(@PathVariable Long id) {
        return acesso.desativar(id);
    }
}
