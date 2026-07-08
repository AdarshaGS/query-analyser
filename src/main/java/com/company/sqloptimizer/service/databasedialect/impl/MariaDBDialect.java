package com.company.sqloptimizer.service.databasedialect.impl;

import com.company.sqloptimizer.service.databasedialect.DatabaseDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MariaDB implementation of DatabaseDialect.
 */
public class MariaDBDialect implements DatabaseDialect {

    private static final Logger LOGGER = LoggerFactory.getLogger(MariaDBDialect.class);

    @Override
    public boolean supportsJsonExplain(JdbcTemplate jdbcTemplate) {
        try {
            String version = getDatabaseVersion(jdbcTemplate);
            if (version == null || version.isBlank()) {
                LOGGER.warn("Unable to determine MariaDB version, assuming FORMAT=JSON not supported");
                return false;
            }

            // MariaDB 10.0.5+ supports FORMAT=JSON in EXPLAIN
            // Version format examples: "10.1.44-MariaDB", "5.5.5-MariaDB-10.1.44"
            String[] parts = splitVersion(version);
            if (parts == null || parts.length < 3) {
                return false;
            }

            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);

            // MariaDB 10.0.5+ supports FORMAT=JSON
            if (major > 10 || (major == 10 && minor > 0) || (major == 10 && minor == 0 && patch >= 5)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("Error checking MariaDB version for FORMAT=JSON support: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Splits version string into numeric parts.
     * Example: "10.1.44-MariaDB" -> ["10", "10", "44"] -> ["10", "1", "44"]
     *
     * @param versionString the version string
     * @return array of version parts or null if invalid
     */
    private String[] splitVersion(String versionString) {
        if (versionString == null) {
            return null;
        }
        // Extract leading numbers separated by dots
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(versionString);
        if (matcher.find()) {
            return new String[]{matcher.group(1), matcher.group(2), matcher.group(3)};
        }
        return null;
    }

    @Override
    public String buildExplain(String sql) {
        // MariaDB: EXPLAIN FORMAT=JSON <sql> (same as MySQL)
        return "EXPLAIN FORMAT=JSON " + sql;
    }

    @Override
    public String getDatabaseName(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            LOGGER.warn("Could not get MariaDB database name: {}", e.getMessage());
            return "MariaDB";
        }
    }

    @Override
    public String getDatabaseVersion(JdbcTemplate jdbcTemplate) {
        try {
            String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
            // Clean up version string to extract just numbers
            String[] parts = splitVersion(version);
            if (parts != null) {
                return String.join(".", parts);
            }
            return version != null ? version : "";
        } catch (Exception e) {
            LOGGER.warn("Could not get MariaDB version: {}", e.getMessage());
            return "";
        }
    }
}