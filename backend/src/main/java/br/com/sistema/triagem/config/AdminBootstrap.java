package br.com.sistema.triagem.config;

import br.com.sistema.triagem.service.AcessoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
    private final AcessoService acesso;
    private final String login;
    private final String senha;
    public AdminBootstrap(AcessoService acesso,
            @Value("${TRIAGEM_ADMIN_LOGIN:}") String login,
            @Value("${TRIAGEM_ADMIN_PASSWORD:}") String senha) {
        this.acesso = acesso; this.login = login; this.senha = senha;
    }
    @Override
    public void run(ApplicationArguments args) {
        if (acesso.listar().isEmpty() && !login.isBlank() && !senha.isBlank())
            acesso.cadastrar(new AcessoService.Cadastro(login, "Administrador", senha, "ADMIN", "N1"));
    }
}
