package br.com.sistema.triagem.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Operações duráveis compartilhadas por entrada, envio e worker. */
@Repository
public class EvolutionStore {
    private final JdbcTemplate jdbc;
    public EvolutionStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void coordenar() {
        jdbc.queryForObject("SELECT id FROM evolution_coordenacao WHERE id=1 FOR UPDATE", Integer.class);
    }

    @Transactional
    public void enfileirar(String instancia, String externo, String payload) {
        jdbc.update("INSERT INTO evolution_webhook_inbox(instancia,identificador_externo,payload) VALUES (?,?,?)",
                instancia, externo, payload);
    }

    public List<Long> pendentes(String instancia) {
        return jdbc.queryForList("SELECT id FROM evolution_webhook_inbox WHERE instancia=? AND estado IN ('PENDENTE','FALHA') "
                + "AND tentativas < 10 AND proxima_tentativa <= CURRENT_TIMESTAMP ORDER BY id LIMIT 20", Long.class, instancia);
    }

    public record Evento(long id, String instancia, String externo, String payload, String estado) {}
    public Evento bloquear(long id) {
        return jdbc.queryForObject("SELECT * FROM evolution_webhook_inbox WHERE id=? FOR UPDATE",
                (rs, n) -> new Evento(rs.getLong("id"), rs.getString("instancia"),
                        rs.getString("identificador_externo"), rs.getString("payload"), rs.getString("estado")), id);
    }

    public void concluir(long id, boolean processado) {
        jdbc.update("UPDATE evolution_webhook_inbox SET estado=?,processado_em=CURRENT_TIMESTAMP WHERE id=?",
                processado ? "PROCESSADO" : "IGNORADO", id);
    }

    @Transactional
    public void falhou(long id) {
        jdbc.update("UPDATE evolution_webhook_inbox SET estado='FALHA',tentativas=tentativas+1,proxima_tentativa=? "
                + "WHERE id=? AND estado IN ('PENDENTE','FALHA')", Timestamp.valueOf(LocalDateTime.now().plusSeconds(30)), id);
    }

    public record Contato(String id, String telefone, String jid) {}

    private Contato contato(String id) {
        return jdbc.queryForObject("SELECT * FROM evolution_contatos WHERE id=?",
                (rs,n) -> new Contato(rs.getString("id"), rs.getString("telefone"), rs.getString("jid_preferido")), id);
    }

    public Contato porId(String id) { return contato(id); }

    public Contato identificar(String jid, String alternativo) {
        String id = alias(jid);
        String outro = alternativo == null ? null : alias(alternativo);
        if (id != null && outro != null && !id.equals(outro))
            throw new IllegalStateException("Aliases WhatsApp conflitantes; revisar inbox sem mesclar contatos");
        if (id == null) id = outro;
        String telefone = telefone(jid);
        if (telefone == null) telefone = telefone(alternativo);
        String preferido = telefone == null ? jid : telefone + "@s.whatsapp.net";
        if (id == null) {
            id = preferido;
            jdbc.update("INSERT INTO evolution_contatos(id,telefone,jid_preferido) VALUES (?,?,?)", id, telefone, preferido);
        } else if (telefone != null) {
            Contato atual = contato(id);
            if (atual.telefone() != null && !telefone.equals(atual.telefone()))
                throw new IllegalStateException("Telefone conflitante para contato WhatsApp");
            jdbc.update("UPDATE evolution_contatos SET telefone=?,jid_preferido=? WHERE id=?", telefone, preferido, id);
            jdbc.update("UPDATE chamados SET telefone_cliente=? WHERE contato_whatsapp_id=? AND telefone_cliente IS NULL", telefone, id);
        }
        vincular(jid, id);
        if (alternativo != null) vincular(alternativo, id);
        return contato(id);
    }

    private String alias(String jid) {
        return jdbc.query("SELECT contato_id FROM evolution_contato_jids WHERE jid=?",
                (rs,n) -> rs.getString(1), jid).stream().findFirst().orElse(null);
    }

    private void vincular(String jid, String id) {
        if (alias(jid) == null) jdbc.update("INSERT INTO evolution_contato_jids(jid,contato_id) VALUES (?,?)", jid, id);
    }

    public static boolean jidValido(String jid) {
        return jid != null && jid.length() <= 100
                && jid.matches("([0-9]{7,15}@s\\.whatsapp\\.net|[0-9]{1,30}@lid)");
    }

    public static String telefone(String jid) {
        return jid != null && jid.matches("[0-9]{7,15}@s\\.whatsapp\\.net") ? jid.substring(0, jid.indexOf('@')) : null;
    }
}
