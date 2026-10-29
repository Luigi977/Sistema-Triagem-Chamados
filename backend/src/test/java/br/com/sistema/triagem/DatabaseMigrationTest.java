package br.com.sistema.triagem;

import br.com.sistema.triagem.model.entity.Chamado;
import br.com.sistema.triagem.model.entity.Mensagem;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseMigrationTest {
    private final String url = "jdbc:h2:mem:migration_" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    private Flyway flyway(boolean baseline) {
        return Flyway.configure().dataSource(url, "sa", "")
                .baselineOnMigrate(baseline).baselineVersion("0").load();
    }

    private void hibernate(String action) {
        try (var factory = new Configuration().addAnnotatedClass(Chamado.class)
                .addAnnotatedClass(Mensagem.class)
                .setProperty("hibernate.connection.url", url)
                .setProperty("hibernate.connection.username", "sa")
                .setProperty("hibernate.connection.password", "")
                .setProperty("hibernate.hbm2ddl.auto", action).buildSessionFactory()) {
            assertTrue(factory.isOpen());
        }
    }

    private long insertChamado(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO chamados (telefone_cliente, status, data_abertura, causa_real) "
                    + "VALUES ('11900000000', 'ENCERRADO', TIMESTAMP '2026-01-01 10:00:00', 'Causa ficticia')",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            try (var keys = statement.getGeneratedKeys()) {
                assertTrue(keys.next());
                return keys.getLong(1);
            }
        }
    }

    private void assertPreserved(Connection connection, long id) throws Exception {
        try (var statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT * FROM chamados WHERE id = " + id)) {
            assertTrue(row.next());
            assertEquals("11900000000", row.getString("telefone_cliente"));
            assertEquals("ENCERRADO", row.getString("status"));
            assertEquals("Causa ficticia", row.getString("causa_real"));
            assertEquals("2026-01-01T10:00", row.getTimestamp("data_abertura").toLocalDateTime().toString());
            assertNull(row.getString("resumo"));
            assertFalse(row.next());
        }
        assertTrue(insertChamado(connection) > id, "Identity deve continuar depois dos registros existentes");
        hibernate("validate");
        assertEquals(0, flyway(false).migrate().migrationsExecuted);
    }

    @Test
    void criaBancoVazioCompativelComHibernate() {
        assertEquals(3, flyway(false).migrate().migrationsExecuted);
        hibernate("validate");
        assertEquals(0, flyway(false).migrate().migrationsExecuted);
    }

    @Test
    void atualizaVersaoAnteriorSemApagarChamados() throws Exception {
        Flyway.configure().dataSource(url, "sa", "").target("1").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            long id = insertChamado(connection);
            assertEquals(2, flyway(false).migrate().migrationsExecuted);
            assertPreserved(connection, id);
        }
    }

    @Test
    void adotaBancoLegadoDoHibernatePreservandoMensagens() throws Exception {
        hibernate("create");
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement()) {
            long id = insertChamado(connection);
            statement.executeUpdate("INSERT INTO mensagens (chamado_id, remetente, conteudo, tipo, data_envio, identificador_externo) "
                    + "VALUES (" + id + ", 'CLIENTE', 'Mensagem ficticia', 'TEXTO', CURRENT_TIMESTAMP, 'fixture-unica')");
            assertThrows(FlywayException.class, () -> flyway(false).migrate());
            assertEquals(3, flyway(true).migrate().migrationsExecuted);
            try (var row = statement.executeQuery("SELECT * FROM mensagens")) {
                assertTrue(row.next());
                assertEquals(id, row.getLong("chamado_id"));
                assertEquals("Mensagem ficticia", row.getString("conteudo"));
                assertEquals("fixture-unica", row.getString("identificador_externo"));
                assertFalse(row.next());
            }
            assertPreserved(connection, id);
        }
    }
}
