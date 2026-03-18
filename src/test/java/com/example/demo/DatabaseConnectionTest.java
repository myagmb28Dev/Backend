package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(locations = "file:./.env")
class DatabaseConnectionTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testConnection() {
        Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        
        System.out.println("==========================================");
        System.out.println("DB Connection SUCCESS! Result: " + result);
        System.out.println("==========================================");
        
        assertThat(result).isEqualTo(1);
    }
}
