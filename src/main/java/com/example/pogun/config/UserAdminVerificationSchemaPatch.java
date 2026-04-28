package com.example.pogun.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserAdminVerificationSchemaPatch implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        patchUsersTable();
    }

    private void patchUsersTable() {
        addBooleanColumnIfMissing("admin_email_verification_required", "false");
        addTimestampColumnIfMissing("admin_email_verified_at");
    }

    private void addBooleanColumnIfMissing(String columnName, String defaultValue) {
        if (columnExists(columnName)) {
            return;
        }
        log.warn("Missing users.{} column detected. Applying schema patch.", columnName);
        jdbcTemplate.execute(
                "ALTER TABLE users ADD COLUMN " + columnName + " BOOLEAN NOT NULL DEFAULT " + defaultValue
        );
        log.warn("Schema patch applied: users.{}", columnName);
    }

    private void addTimestampColumnIfMissing(String columnName) {
        if (columnExists(columnName)) {
            return;
        }
        log.warn("Missing users.{} column detected. Applying schema patch.", columnName);
        jdbcTemplate.execute(
                "ALTER TABLE users ADD COLUMN " + columnName + " TIMESTAMP NULL"
        );
        log.warn("Schema patch applied: users.{}", columnName);
    }

    private boolean columnExists(String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'users'
                  AND column_name = ?
                """,
                Integer.class,
                columnName
        );
        return count != null && count > 0;
    }
}

