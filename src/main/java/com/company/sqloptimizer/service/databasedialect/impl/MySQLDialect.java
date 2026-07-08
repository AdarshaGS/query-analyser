package com.company.sqloptimizer.service.databasedialect.impl;

import com.company.sqloptimizer.service.databasedialect.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MySQL implementation of DatabaseDialect.
 */
public class MySQLDialect implements DatabaseDialect {

    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLDialect.class);

    @Override
    public boolean supportsJsonExplain(JdbcTemplate jdbcTemplate) {
        try {
            String version = getDatabaseVersion(jdbcTemplate);
            if (version == null || version.isBlank()) {
                LOGGER.warn("Unable to determine MySQL version, assuming FORMAT=JSON not supported");
                return false;
            }

            // MySQL 5.7.8+ supports FORMAT=JSON
            // Version format: "5.7.25-log", "8.0.15", etc.
            String[] parts = version.split("[.-]");
            if (parts.length >= 3) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);
                int patch = Integer.parseInt(parts[2]);

                // MySQL 5.7.8+ supports FORMAT=JSON
                if (major > 5 || (major == 5 && minor >= 7 && patch >= 8)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("Error checking MySQL version for FORMAT=JSON support: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String buildExplain(String sql) {
        // MySQL: EXPLAIN FORMAT=JSON <sql>
        return "EXPLAIN FORMAT=JSON " + sql;
    }

    @Override
    public String getDatabaseName(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            LOGGER.warn("Could not get MySQL database name: {}", e.getMessage());
            return "MySQL";
        }
    }

    @Override
    public String getDatabaseVersion(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            LOGGER.warn("Could not get MySQL version: {}", e.getMessage());
            return "";
        }
    }
}