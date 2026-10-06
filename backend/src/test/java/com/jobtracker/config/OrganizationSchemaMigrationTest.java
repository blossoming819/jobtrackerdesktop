package com.jobtracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationSchemaMigrationTest {

    @Test
    void migratesLegacyCompaniesAndCanRunTwice() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:organization-migration;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE job_application (id BIGINT AUTO_INCREMENT PRIMARY KEY, company_name VARCHAR(100), deleted TINYINT DEFAULT 0)");
        jdbcTemplate.update("INSERT INTO job_application (company_name, deleted) VALUES ('示例集团', 0), ('示例集团', 0)");

        OrganizationSchemaMigration migration = new OrganizationSchemaMigration(jdbcTemplate, dataSource);
        migration.migrate();
        migration.migrate();

        assertTrue(hasColumn(dataSource, "job_application", "organization_unit_id"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM organization_unit WHERE name = '示例集团'", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM job_application WHERE organization_unit_id IS NOT NULL", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT DISTINCT organization_path_snapshot FROM job_application", String.class)).isEqualTo("示例集团");
    }

    private boolean hasColumn(DataSource dataSource, String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(
                     connection.getCatalog(), connection.getSchema(), tableName, columnName)) {
            return columns.next();
        }
    }
}
