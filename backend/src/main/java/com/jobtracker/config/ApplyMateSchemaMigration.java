package com.jobtracker.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplyMateSchemaMigration {
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void migrate() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS candidate_profile (id BIGINT AUTO_INCREMENT PRIMARY KEY, profile_id VARCHAR(80) NOT NULL, schema_version VARCHAR(20) NOT NULL, content_json LONGTEXT NOT NULL, revision INT NOT NULL DEFAULT 1, created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted TINYINT DEFAULT 0)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS profile_snapshot (id BIGINT AUTO_INCREMENT PRIMARY KEY, profile_id VARCHAR(80) NOT NULL, revision INT NOT NULL, content_json LONGTEXT NOT NULL, created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS resume_parse_record (id BIGINT AUTO_INCREMENT PRIMARY KEY, resume_id BIGINT NOT NULL, content_hash VARCHAR(64) NOT NULL, extractor VARCHAR(80) NOT NULL, status VARCHAR(40) NOT NULL, extracted_length INT NOT NULL DEFAULT 0, result_json LONGTEXT, error_code VARCHAR(80), created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS applymate_extension_pairing (id BIGINT AUTO_INCREMENT PRIMARY KEY, extension_id VARCHAR(120) NOT NULL, display_name VARCHAR(160), claim_secret_hash VARCHAR(64), token_hash VARCHAR(64), status VARCHAR(20) NOT NULL, created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, approved_time TIMESTAMP NULL)");
    }
}
