package com.example.pogun.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReportSchemaConstraintInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    @Value("${app.schema-sync.enabled:true}")
    private boolean schemaSyncEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!schemaSyncEnabled) {
            log.info("Report schema sync skipped (app.schema-sync.enabled=false)");
            return;
        }
        try {
            Boolean reportsTableExists = jdbcTemplate.queryForObject(
                    """
                            select exists (
                                select 1
                                from information_schema.tables
                                where table_schema = 'public'
                                  and table_name = 'reports'
                            )
                            """,
                    Boolean.class
            );
            if (!Boolean.TRUE.equals(reportsTableExists)) {
                return;
            }

            String constraintDefinition = jdbcTemplate.query(
                    """
                            select pg_get_constraintdef(oid)
                            from pg_constraint
                            where conrelid = 'reports'::regclass
                              and conname = 'reports_target_type_check'
                            """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : ""
            );

            if (constraintDefinition != null && constraintDefinition.contains("NOTICE_CHAT_ROOM")) {
                return;
            }

            jdbcTemplate.execute("alter table reports drop constraint if exists reports_target_type_check");
            jdbcTemplate.execute(
                    """
                            alter table reports
                            add constraint reports_target_type_check
                            check (target_type in (
                                'COMMUNITY_POST',
                                'COMMUNITY_COMMENT',
                                'PET_NOTICE',
                                'NOTICE_CHAT_ROOM',
                                'USER'
                            ))
                            """
            );
            log.info("reports_target_type_check constraint synchronized with ReportTargetType enum values");
        } catch (RuntimeException e) {
            log.warn("Failed to synchronize reports_target_type_check constraint", e);
        }
    }
}
