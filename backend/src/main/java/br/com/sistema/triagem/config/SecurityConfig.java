package br.com.sistema.triagem.config;

import br.com.sistema.triagem.service.AcessoService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, AcessoService acesso) throws Exception {
        return http.csrf(c -> c.disable()) // Exclusivamente Bearer; sem cookies, Basic ou formulario.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .authorizeHttpRequests(a -> a
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/health").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/login").permitAll()
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/api/**").authenticated().anyRequest().denyAll())
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,ex) -> res.sendError(401))
                .accessDeniedHandler((req,res,ex) -> res.sendError(403)))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                        throws ServletException, IOException {
                    String header = req.getHeader("Authorization");
                    if (header != null) {
                        if (!header.startsWith("Bearer ") || header.length() > 256) { res.sendError(401); return; }
                        var tecnico = acesso.autenticar(header.substring(7));
                        if (tecnico.isEmpty()) { res.sendError(401); return; }
                        var t = tecnico.get();
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                            t.login(), null, List.of(new SimpleGrantedAuthority("ROLE_" + t.perfil()))));
                    }
                    chain.doFilter(req, res);
                }
            }, UsernamePasswordAuthenticationFilter.class).build();
    }
}
