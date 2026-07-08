package com.company.sqloptimizer.service.databasedialect.impl;

import com.company.sqloptimizer.service.databasedialect.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL implementation of DatabaseDialect.
 */
public class PostgreSQLDialect implements DatabaseDialect {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSQLDialect.class);

    @Override
    public boolean supportsJsonExplain(JdbcTemplate jdbcTemplate) {
        try {
            String version = getDatabaseVersion(jdbcTemplate);
            if (version == null || version.isBlank()) {
                LOGGER.warn("Unable to determine PostgreSQL version, assuming FORMAT=JSON not supported");
                return false;
            }

            // PostgreSQL 9.4+ supports FORMAT JSON in EXPLAIN
            // Version format: "PostgreSQL 12.3", "PostgreSQL 11.2", etc.
            // Extract version numbers from string like "PostgreSQL 12.3 on x86_64-pc-linux-gnu..."
            String versionNumbers = extractVersionNumbers(version);
            if (versionNumbers == null) {
                return false;
            }

            String[] parts = versionNumbers.split("[._]");
            if (parts.length >= 2) {
                int major = Integer.parseInt(parts[0]);
                int minor = Integer.parseInt(parts[1]);

                // PostgreSQL 9.4+ supports FORMAT JSON
                if (major > 9 || (major == 9 && minor >= 4)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("Error checking PostgreSQL version for FORMAT=JSON support: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extracts version numbers from PostgreSQL version string.
     * Example: "PostgreSQL 12.3 on x86_64-pc-linux-gnu..." -> "12.3"
     *
     * @param versionString the full version string
     * @return the volume numbers part or null if not found
     */
    private String extractVersionNumbers(String volumeString) {
        if (volumeString == null) {
            return null;
        }
        // Look for pattern: PostgreSQL X.Y.Z or PostgreSQL X.Y
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("PostgreSQL\\s+(\\d+\\.\\d+(?:\\.\\d+)?)");
        java.util.regex.Matcher matcher = pattern.matcher(volumeString);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    @Override
    public String buildExplain(String sql) {
        // PostgreSQL: EXPLAIN (FORMAT JSON) <sql>
        return "EXPLAIN (FORMAT JSON) " + sql;
    }

    @Override
    public String getDatabaseName(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            LOGGER.warn("Could not get PostgreSQL database name: {}", e.getMessage());
            return "PostgreSQL";
        }
    }

    @Override
    public String getDatabaseVersion(JdbcTemplate jdbcTemplate) {
        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            String versionNumbers = extractVersionNumbers(version);
            return versionNumbers != null ? versionNumbers : "";
        } catch (Exception e) {
            LOGGER.warn("Could not get PostgreSQL version: {}", e.getMessage());
            return "";
        }
    }
}