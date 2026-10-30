package br.com.sistema.triagem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
public class TriagemChamadosApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriagemChamadosApplication.class, args);
    }
}