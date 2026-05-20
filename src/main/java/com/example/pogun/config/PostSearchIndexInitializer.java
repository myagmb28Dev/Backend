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
public class PostSearchIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.schema-sync.enabled:true}")
    private boolean schemaSyncEnabled;

    @Override
    public void run(ApplicationArguments args) {
        if (!schemaSyncEnabled) {
            log.info("Post search index sync skipped (app.schema-sync.enabled=false)");
            return;
        }

        try {
            boolean trgmAvailable = ensurePgTrgmExtension();
            if (!trgmAvailable) {
                log.warn("pg_trgm extension unavailable; skipping post search trigram indexes");
                return;
            }
            createCommunityIndexes();
            createShelterIndexes();
            log.info("Post search indexes synchronized");
        } catch (RuntimeException e) {
            log.warn("Failed to synchronize post search indexes", e);
        }
    }

    private void createCommunityIndexes() {
        if (!tableExists("community_posts")) {
            return;
        }
        jdbcTemplate.execute(
                """
                        create index if not exists idx_community_posts_title_trgm
                        on community_posts using gin (lower(title) gin_trgm_ops)
                        """
        );
        jdbcTemplate.execute(
                """
                        create index if not exists idx_community_posts_content_trgm
                        on community_posts using gin (lower(content) gin_trgm_ops)
                        """
        );
        jdbcTemplate.execute(
                """
                        create index if not exists idx_community_posts_status_category_created_at
                        on community_posts (status, category, created_at desc)
                        """
        );
    }

    private void createShelterIndexes() {
        if (!tableExists("shelter_pets")) {
            return;
        }
        jdbcTemplate.execute(
                """
                        create index if not exists idx_shelter_pets_title_trgm
                        on shelter_pets using gin (lower(title) gin_trgm_ops)
                        """
        );
        jdbcTemplate.execute(
                """
                        create index if not exists idx_shelter_pets_description_trgm
                        on shelter_pets using gin (lower(description) gin_trgm_ops)
                        """
        );
        jdbcTemplate.execute(
                """
                        create index if not exists idx_shelter_pets_status_region_breed
                        on shelter_pets (status, region, breed)
                        """
        );
    }

    private boolean tableExists(String tableName) {
        Boolean tableExists = jdbcTemplate.queryForObject(
                """
                        select exists (
                            select 1
                            from information_schema.tables
                            where table_schema = 'public'
                              and table_name = ?
                        )
                        """,
                Boolean.class,
                tableName
        );
        return Boolean.TRUE.equals(tableExists);
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
