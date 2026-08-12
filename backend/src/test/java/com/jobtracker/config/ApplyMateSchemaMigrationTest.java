package com.jobtracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplyMateSchemaMigrationTest {

    @Test
    void migratesLegacyDesktopSchemaAndCanRunTwice() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:applymate-migration;DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE job_application (id BIGINT AUTO_INCREMENT PRIMARY KEY)");

        ApplyMateSchemaMigration migration = new ApplyMateSchemaMigration(jdbcTemplate, dataSource);
        migration.migrate();
        migration.migrate();

        assertTrue(hasColumn(dataSource, "candidate_profile", "profile_name"));
        assertTrue(hasColumn(dataSource, "candidate_profile", "profile_description"));
        assertTrue(hasColumn(dataSource, "candidate_profile", "source_resume_id"));
        assertTrue(hasColumn(dataSource, "job_application", "profile_id"));
    }

    private boolean hasColumn(DataSource dataSource, String tableName, String columnName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             ResultSet columns = connection.getMetaData().getColumns(
                     connection.getCatalog(), connection.getSchema(), tableName, columnName)) {
            return columns.next();
        }
    }
}
