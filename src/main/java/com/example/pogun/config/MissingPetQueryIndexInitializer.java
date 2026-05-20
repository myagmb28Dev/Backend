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
public class MissingPetQueryIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.schema-sync.enabled:true}")
    private boolean schemaSyncEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!schemaSyncEnabled) {
            log.info("Missing-pet query index sync skipped (app.schema-sync.enabled=false)");
            return;
        }

        try {
            Boolean tableExists = jdbcTemplate.queryForObject(
                    """
                            select exists (
                                select 1
                                from information_schema.tables
                                where table_schema = 'public'
                                  and table_name = 'pet_notices'
                            )
                            """,
                    Boolean.class
            );
            if (!Boolean.TRUE.equals(tableExists)) {
                return;
            }

            jdbcTemplate.execute(
                    """
                            create index if not exists idx_pet_notices_hidden_status_missing_date_created_at
                            on pet_notices (is_hidden, status, missing_date desc, created_at desc)
                            """
            );

            boolean trgmAvailable = ensurePgTrgmExtension();
            if (!trgmAvailable) {
                log.warn("pg_trgm extension unavailable; skipping missing_region/breed trigram indexes");
                return;
            }

            jdbcTemplate.execute(
                    """
                            create index if not exists idx_pet_notices_missing_region_trgm
                            on pet_notices using gin (lower(missing_region) gin_trgm_ops)
                            """
            );
            jdbcTemplate.execute(
                    """
                            create index if not exists idx_pet_notices_title_trgm
                            on pet_notices using gin (lower(title) gin_trgm_ops)
                            """
            );
            jdbcTemplate.execute(
                    """
                            create index if not exists idx_pet_notices_description_trgm
                            on pet_notices using gin (lower(description) gin_trgm_ops)
                            """
            );
            jdbcTemplate.execute(
                    """
                            create index if not exists idx_pet_notices_breed_trgm
                            on pet_notices using gin (lower(breed) gin_trgm_ops)
                            """
            );
            log.info("Missing-pet query indexes synchronized");
        } catch (RuntimeException e) {
            log.warn("Failed to synchronize missing-pet query indexes", e);
        }
    }

    private boolean ensurePgTrgmExtension() {
        try {
            jdbcTemplate.execute("create extension if not exists pg_trgm");
        } catch (RuntimeException e) {
            log.warn("create extension pg_trgm failed; continuing without trigram indexes", e);
        }
        Boolean trgmExists = jdbcTemplate.queryForObject(
                """
                        select exists (
                            select 1
                            from pg_extension
                            where extname = 'pg_trgm'
                        )
                        """,
                Boolean.class
        );
        return Boolean.TRUE.equals(trgmExists);
    }
}
