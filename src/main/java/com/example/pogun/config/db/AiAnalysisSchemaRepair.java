package com.example.pogun.config.db;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * AI 분석 저장소가 과거 photo 기반 스키마에서 남긴 NOT NULL 제약을 정리한다.
 */
@Component
@RequiredArgsConstructor
public class AiAnalysisSchemaRepair {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void repair() {
        jdbcTemplate.execute("ALTER TABLE ai_analyses ALTER COLUMN author_id DROP NOT NULL");
        jdbcTemplate.execute("ALTER TABLE ai_analyses ALTER COLUMN photo_id DROP NOT NULL");
    }
}
