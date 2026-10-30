package br.com.sistema.triagem.service;

import br.com.sistema.triagem.model.entity.Chamado;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional
public class AcessoService {
    public record Tecnico(Long id, String login, String nome, String perfil, String equipe, boolean ativo) {}
    public record Cadastro(String login, String nome, String senha, String perfil, String equipe) {}
    public record Sessao(String token, Instant expiraEm, Tecnico tecnico) {}
    public record Operacao(Long id, Long autorId, String acao, java.time.LocalDateTime dataOperacao) {}
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String dummyHash = encoder.encode(UUID.randomUUID().toString());

    public AcessoService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private Tecnico map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new Tecnico(rs.getLong("id"), rs.getString("login"), rs.getString("nome"),
                rs.getString("perfil"), rs.getString("equipe"), rs.getBoolean("ativo"));
    }

    public Tecnico atual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new ResponseStatusException(UNAUTHORIZED);
        return jdbc.query("SELECT * FROM tecnicos WHERE login = ? AND ativo = true", this::map, auth.getName())
                .stream().findFirst().orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    public void admin() {
        if (!"ADMIN".equals(atual().perfil())) throw new ResponseStatusException(FORBIDDEN);
    }

    public Tecnico buscar(Long id) {
        return jdbc.query("SELECT * FROM tecnicos WHERE id = ?", this::map, id).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tecnico inexistente"));
    }

    public List<Tecnico> listar() { return jdbc.query("SELECT * FROM tecnicos ORDER BY id", this::map); }

    public Tecnico cadastrar(Cadastro c) {
        String login = texto(c.login(), 100).toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9._@-]+")) throw new ResponseStatusException(BAD_REQUEST, "Login invalido");
        String nome = texto(c.nome(), 150);
        String equipe = categoria(c.equipe(), Set.of("N1", "N2"));
        String perfil = categoria(c.perfil(), Set.of("ADMIN", "TECNICO"));
        if (c.senha() == null || c.senha().isBlank() || c.senha().length() < 12
                || c.senha().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(BAD_REQUEST, "Senha deve ter 12 caracteres a 72 bytes");
        try {
            jdbc.update("INSERT INTO tecnicos(login,nome,senha_hash,perfil,equipe) VALUES (?,?,?,?,?)",
                    login, nome, encoder.encode(c.senha()), perfil, equipe);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ResponseStatusException(CONFLICT, "Login ja cadastrado");
        }
        return jdbc.query("SELECT * FROM tecnicos WHERE login = ?", this::map, login).getFirst();
    }

    public Tecnico desativar(Long id) {
        admin();
        if (atual().id().equals(id)) throw new ResponseStatusException(CONFLICT, "Nao pode desativar a propria conta");
        buscar(id);
        jdbc.update("UPDATE tecnicos SET ativo = false WHERE id = ?", id);
        jdbc.update("DELETE FROM sessoes WHERE tecnico_id = ?", id);
        return buscar(id);
    }

    public Sessao login(String login, String senha) {
        if (login == null || senha == null || login.length() > 100 || senha.length() > 72)
            throw new ResponseStatusException(UNAUTHORIZED, "Credenciais invalidas");
        String normalized = login.trim().toLowerCase(Locale.ROOT);
        var hashes = jdbc.queryForList("SELECT senha_hash FROM tecnicos WHERE login = ? AND ativo = true", String.class, normalized);
        boolean valid = encoder.matches(senha, hashes.isEmpty() ? dummyHash : hashes.getFirst());
        if (!valid || hashes.isEmpty()) throw new ResponseStatusException(UNAUTHORIZED, "Credenciais invalidas");
        Tecnico tecnico = jdbc.query("SELECT * FROM tecnicos WHERE login = ?", this::map, normalized).getFirst();
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expira = Instant.now().plusSeconds(28800);
        jdbc.update("DELETE FROM sessoes WHERE expira_em <= ?", Timestamp.from(Instant.now()));
        jdbc.update("INSERT INTO sessoes(token_hash,tecnico_id,expira_em) VALUES (?,?,?)", hash(token), tecnico.id(), Timestamp.from(expira));
        return new Sessao(token, expira, tecnico);
    }

    public Optional<Tecnico> autenticar(String token) {
        return jdbc.query("SELECT t.* FROM tecnicos t JOIN sessoes s ON s.tecnico_id=t.id "
                + "WHERE s.token_hash=? AND s.expira_em > ? AND t.ativo=true", this::map,
                hash(token), Timestamp.from(Instant.now())).stream().findFirst();
    }

    public void logout(String token) { jdbc.update("DELETE FROM sessoes WHERE token_hash=?", hash(token)); }

    public void editar(Chamado chamado) {
        Tecnico t = atual();
        if (!"ADMIN".equals(t.perfil()) && (!t.equipe().equals(chamado.getEquipe())
                || (chamado.getResponsavelId() != null && !t.id().equals(chamado.getResponsavelId()))))
            throw new ResponseStatusException(FORBIDDEN, "Chamado fora da equipe ou atribuido a outro tecnico");
    }

    public void registrar(Chamado chamado, String acao) {
        jdbc.update("INSERT INTO operacoes(chamado_id,autor_id,acao,data_operacao) VALUES (?,?,?,?)",
                chamado.getId(), atual().id(), acao, Timestamp.from(Instant.now()));
    }

    public List<Operacao> historico(Long chamadoId) {
        return jdbc.query("SELECT id, autor_id, acao, data_operacao FROM operacoes WHERE chamado_id=? ORDER BY id",
                (rs, row) -> new Operacao(rs.getLong("id"), rs.getLong("autor_id"), rs.getString("acao"),
                        rs.getTimestamp("data_operacao").toLocalDateTime()), chamadoId);
    }

    private static String texto(String value, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) throw new ResponseStatusException(BAD_REQUEST);
        return value.trim();
    }
    private static String categoria(String value, Set<String> valid) {
        String result = texto(value, 10).toUpperCase(Locale.ROOT);
        if (!valid.contains(result)) throw new ResponseStatusException(BAD_REQUEST);
        return result;
    }
    private static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
