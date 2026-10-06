package com.jobtracker.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrganizationSchemaMigration {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @PostConstruct
    public void migrate() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS organization_unit (id BIGINT AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT, name VARCHAR(200) NOT NULL, unit_type VARCHAR(40) NOT NULL DEFAULT 'OTHER', company_entity TINYINT NOT NULL DEFAULT 0, aliases VARCHAR(500), sort_order INT NOT NULL DEFAULT 0, active TINYINT NOT NULL DEFAULT 1, created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted TINYINT DEFAULT 0)");
        addColumnIfMissing("submission_group_id", "VARCHAR(80)");
        addColumnIfMissing("preference_order", "INT");
        addColumnIfMissing("submission_organization_id", "BIGINT");
        addColumnIfMissing("employer_organization_id", "BIGINT");
        addColumnIfMissing("organization_unit_id", "BIGINT");
        addColumnIfMissing("organization_path_snapshot", "VARCHAR(1200)");
        addColumnIfMissing("employer_name_snapshot", "VARCHAR(200)");
        addColumnIfMissing("group_name_snapshot", "VARCHAR(200)");
        createIndexes();
        migrateLegacyCompanies();
        migrateSubmissionParents();
    }

    private void migrateSubmissionParents() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS application_submission (id VARCHAR(80) PRIMARY KEY, company_name VARCHAR(200) NOT NULL, recruitment_type VARCHAR(80), work_location VARCHAR(100), source VARCHAR(80), applied_time TIMESTAMP, submission_organization_id BIGINT, employer_organization_id BIGINT, organization_unit_id BIGINT, organization_path_snapshot VARCHAR(1200), employer_name_snapshot VARCHAR(200), group_name_snapshot VARCHAR(200), remark VARCHAR(1000), created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted TINYINT DEFAULT 0)");
        String sql = "SELECT id, company_name, submission_group_id, submission_organization_id, employer_organization_id, organization_unit_id, organization_path_snapshot, employer_name_snapshot, group_name_snapshot, "
                + optionalColumn("recruitment_type") + ", " + optionalColumn("work_location") + ", "
                + optionalColumn("source") + ", " + optionalColumn("applied_time") + ", " + optionalColumn("remark")
                + " FROM job_application WHERE deleted = 0 ORDER BY id";
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            String groupId = text(row.get("submission_group_id"));
            if (groupId == null) {
                groupId = UUID.randomUUID().toString();
                jdbcTemplate.update("UPDATE job_application SET submission_group_id = ? WHERE id = ?", groupId, row.get("id"));
            }
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM application_submission WHERE id = ?", Long.class, groupId);
            if (count != null && count > 0) continue;
            jdbcTemplate.update("INSERT INTO application_submission (id, company_name, recruitment_type, work_location, source, applied_time, submission_organization_id, employer_organization_id, organization_unit_id, organization_path_snapshot, employer_name_snapshot, group_name_snapshot, remark, deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    groupId, defaultText(row.get("company_name"), "未填写公司"), row.get("recruitment_type"), row.get("work_location"), row.get("source"), row.get("applied_time"),
                    row.get("submission_organization_id"), row.get("employer_organization_id"), row.get("organization_unit_id"), row.get("organization_path_snapshot"), row.get("employer_name_snapshot"), row.get("group_name_snapshot"), row.get("remark"));
        }
    }

    private String optionalColumn(String columnName) {
        return columnExists("job_application", columnName) ? columnName : "NULL AS " + columnName;
    }

    private String text(Object value) {
        String result = value == null ? null : String.valueOf(value).trim();
        return result == null || result.isEmpty() ? null : result;
    }

    private String defaultText(Object value, String fallback) {
        String result = text(value);
        return result == null ? fallback : result;
    }

    private void migrateLegacyCompanies() {
        List<Map<String, Object>> units = jdbcTemplate.queryForList("SELECT id, name FROM organization_unit WHERE parent_id IS NULL AND deleted = 0");
        Map<String, Long> rootIds = new LinkedHashMap<>();
        for (Map<String, Object> row : units) {
            rootIds.put(key(String.valueOf(row.get("name"))), ((Number) row.get("id")).longValue());
        }
        List<String> companies = jdbcTemplate.queryForList("SELECT DISTINCT company_name FROM job_application WHERE deleted = 0 AND organization_unit_id IS NULL AND company_name IS NOT NULL AND TRIM(company_name) <> ''", String.class);
        for (String company : companies) {
            String normalized = company.trim().replaceAll("\\s+", " ");
            Long id = rootIds.get(key(normalized));
            if (id == null) {
                jdbcTemplate.update("INSERT INTO organization_unit (name, unit_type, company_entity, sort_order, active, deleted) VALUES (?, 'COMPANY', 1, 0, 1, 0)", normalized);
                id = jdbcTemplate.queryForObject("SELECT MAX(id) FROM organization_unit WHERE parent_id IS NULL AND name = ? AND deleted = 0", Long.class, normalized);
                rootIds.put(key(normalized), id);
            }
            jdbcTemplate.update("UPDATE job_application SET submission_organization_id = ?, employer_organization_id = ?, organization_unit_id = ?, organization_path_snapshot = ?, employer_name_snapshot = ?, group_name_snapshot = ? WHERE organization_unit_id IS NULL AND LOWER(TRIM(company_name)) = LOWER(?)", id, id, id, normalized, normalized, normalized, company);
        }
    }

    private void addColumnIfMissing(String columnName, String definition) {
        if (!columnExists("job_application", columnName)) {
            jdbcTemplate.execute("ALTER TABLE job_application ADD COLUMN " + columnName + " " + definition);
        }
    }

    private boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();
            return hasColumn(metadata, catalog, schema, tableName, columnName)
                    || hasColumn(metadata, catalog, schema, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT))
                    || hasColumn(metadata, catalog, null, tableName, columnName)
                    || hasColumn(metadata, catalog, null, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT));
        } catch (SQLException exception) {
            throw new IllegalStateException("检查组织字段失败", exception);
        }
    }

    private boolean hasColumn(DatabaseMetaData metadata, String catalog, String schema, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = metadata.getColumns(catalog, schema, tableName, columnName)) {
            return columns.next();
        }
    }

    private void createIndexes() {
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_organization_parent ON organization_unit (parent_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_application_organization ON job_application (organization_unit_id)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_application_employer_org ON job_application (employer_organization_id)");
        } catch (Exception ignored) {
            // Older MySQL deployments receive the same indexes from database/schema.sql.
        }
    }

    private String key(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
