package com.company.sqloptimizer.service.databasedialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Interface for database-specific EXPLAIN syntax and capabilities.
 */
public interface DatabaseDialect {

    /**
     * Checks if this database dialect supports EXPLAIN FORMAT=JSON.
     *
     * @param jdbcTemplate the JDBC template for database connection
     * @return true if FORMAT=JSON is supported, false otherwise
     */
    boolean supportsJsonExplain(JdbcTemplate jdbcTemplate);

    /**
     * Builds the EXPLAIN SQL statement for this database dialect.
     *
     * @param sql the SQL query to explain
     * @return the EXPLAIN SQL statement
     */
    String buildExplain(String sql);

    /**
     * Gets the database name/vendor.
     *
     * @param jdbcTemplate the JDBC template for database connection
     * @return the database name (e.g., "MySQL", "PostgreSQL")
     */
    String getDatabaseName(JdbcTemplate jdbcTemplate);

    /**
     * Gets the database version.
     *
     * @param jdbcTemplate the JDBC template for database connection
     * @return the database version string
     */
    String getDatabaseVersion(JdbcTemplate jdbcTemplate);
}