package com.clinica.escuta.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseSequenceSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSequenceSynchronizer.class);

    private static final List<String> TABLES_WITH_ID_SEQUENCE = List.of(
            "professions",
            "units",
            "rooms",
            "users",
            "reservations",
            "holidays",
            "audit_logs",
            "complaints"
    );

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSequenceSynchronizer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String table : TABLES_WITH_ID_SEQUENCE) {
            syncSequence(table);
        }
    }

    public void syncSequence(String tableName) {
        if (!TABLES_WITH_ID_SEQUENCE.contains(tableName)) {
            return;
        }
        try {
            String sql = String.format(
                    "SELECT setval(pg_get_serial_sequence('%s', 'id'), COALESCE((SELECT MAX(id) FROM %s), 0) + 1, false)",
                    tableName,
                    tableName
            );
            jdbcTemplate.queryForObject(sql, Long.class);
            log.debug("Sequência de ID sincronizada para a tabela: {}", tableName);
        } catch (Exception e) {
            // Ignora silenciosamente em bancos de teste (ex: H2) ou tabelas sem sequência serial
            log.debug("Não foi necessário sincronizar sequência para {}: {}", tableName, e.getMessage());
        }
    }
}

