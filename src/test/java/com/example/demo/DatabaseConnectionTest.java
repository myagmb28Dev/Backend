package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(locations = "file:./.env") // .env 파일을 읽어와서 프로퍼티로 사용 (spring.datasource.* 등에 매핑되도록 처리)
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testConnection() {
        // NeonDB (PostgreSQL)에 연결하여 1을 반환하는 간단한 쿼리 실행
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        
        System.out.println("==========================================");
        System.out.println("DB Connection SUCCESS! Result: " + result);
        System.out.println("==========================================");
        
        assertThat(result).isEqualTo(1);
    }
}
