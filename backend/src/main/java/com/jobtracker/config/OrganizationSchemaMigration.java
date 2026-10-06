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

@Component
@RequiredArgsConstructor
public class OrganizationSchemaMigration {
    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @PostConstruct
    public void migrate() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS organization_unit (id BIGINT AUTO_INCREMENT PRIMARY KEY, parent_id BIGINT, name VARCHAR(200) NOT NULL, unit_type VARCHAR(40) NOT NULL DEFAULT 'OTHER', company_entity TINYINT NOT NULL DEFAULT 0, aliases VARCHAR(500), sort_order INT NOT NULL DEFAULT 0, active TINYINT NOT NULL DEFAULT 1, created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, deleted TINYINT DEFAULT 0)");
        addColumnIfMissing("submission_organization_id", "BIGINT");
        addColumnIfMissing("employer_organization_id", "BIGINT");
        addColumnIfMissing("organization_unit_id", "BIGINT");
        addColumnIfMissing("organization_path_snapshot", "VARCHAR(1200)");
        addColumnIfMissing("employer_name_snapshot", "VARCHAR(200)");
        addColumnIfMissing("group_name_snapshot", "VARCHAR(200)");
        createIndexes();
        migrateLegacyCompanies();
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
