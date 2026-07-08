package com.company.sqloptimizer.service.databasedialect;

import com.company.sqloptimizer.service.databasedialect.impl.MariaDBDialect;
import com.company.sqloptimizer.service.databasedialect.impl.MySQLDialect;
import com.company.sqloptimizer.service.databasedialect.impl.PostgreSQLDialect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Factory for creating database dialect instances based on the actual database type.
 */
@Component
public class DatabaseDialectFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseDialectFactory.class);

    /**
     * Creates the appropriate DatabaseDialect based on the database metadata.
     *
     * @param jdbcTemplate the JDBC template for database connection
     * @return the appropriate DatabaseDialect instance
     */
    public DatabaseDialect createDialect(JdbcTemplate jdbcTemplate) {
        String databaseVersion = getDatabaseName(jdbcTemplate);
        String databaseName = databaseVersion.toLowerCase();
        
        LOGGER.debug("Detected database: {}", databaseVersion);
        
        if (databaseName.contains("mysql") || databaseName.startsWith("8.0.40")) {
            LOGGER.info("Using MySQL dialect for version: {}", databaseVersion);
            return new MySQLDialect();
        } else if (databaseName.contains("mariadb")) {
            LOGGER.info("Using MariaDB dialect");
            return new MariaDBDialect();
        } else if (databaseName.contains("postgresql") || databaseName.contains("postgres")) {
            LOGGER.info("Using PostgreSQL dialect");
            return new PostgreSQLDialect();
        } else {
            // Default to MySQL for unknown databases (most common case)
            LOGGER.warn("Unknown database type: {}. Defaulting to MySQL dialect.", databaseName);
            return new MySQLDialect();
        }
    }

    /**
     * Gets the database name from the JDBC connection.
     *
     * @param jdbcTemplate the JDBC template for database connection
     * @return the database version string
     */
    private String getDatabaseName(JdbcTemplate jdbcTemplate) {
        try {
            return jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        } catch (Exception e) {
            LOGGER.warn("Could not determine database name: {}", e.getMessage());
            return "unknown";
        }
    }
}
