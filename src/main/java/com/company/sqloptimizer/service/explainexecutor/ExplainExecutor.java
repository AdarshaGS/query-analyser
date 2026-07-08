package com.company.sqloptimizer.service.explainexecutor;

import com.company.sqloptimizer.service.databasedialect.DatabaseDialect;
import com.company.sqloptimizer.service.databasedialect.DatabaseDialectFactory;
import com.company.sqloptimizer.service.exceptiontranslator.ExceptionTranslator;
import com.company.sqloptimizer.service.exceptiontranslator.SqlAnalysisException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Responsible only for database execution of EXPLAIN statements.
 * Responsibilities:
 * - Detect database vendor
 * - Detect database version
 * - Determine whether FORMAT=JSON is supported
 * - Build correct EXPLAIN syntax
 * - Execute using JdbcTemplate
 * - Log execution details
 */
@Component
@RequiredArgsConstructor
public class ExplainExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainExecutor.class);

    private final JdbcTemplate jdbcTemplate;
    private final DatabaseDialectFactory databaseDialectFactory;
    private final ExceptionTranslator exceptionTranslator;

    /**
     * Executes EXPLAIN on the given SQL query and returns the JSON result.
     *
     * @param sql the SQL query to explain
     * @return the EXPLAIN result in JSON format
     * @throws SqlAnalysisException if EXPLAIN execution fails
     */
    public String executeExplain(String sql) {
        LOGGER.info("Executing EXPLAIN for SQL: {}", sql);

        try {
            // Choose DatabaseDialect
            DatabaseDialect dialect = databaseDialectFactory.createDialect(jdbcTemplate);
            String databaseName = dialect.getDatabaseName(jdbcTemplate);
            String databaseVersion = dialect.getDatabaseVersion(jdbcTemplate);
            boolean supportsJson = dialect.supportsJsonExplain(jdbcTemplate);

            LOGGER.info("Database detected: {} version {}", databaseName, databaseVersion);
            LOGGER.debug("FORMAT=JSON supported: {}", supportsJson);

            // Build the EXPLAIN statement
            String explainSql = dialect.buildExplain(sql);
            LOGGER.debug("Generated EXPLAIN SQL: {}", explainSql);

            // Execute EXPLAIN and retrieve the JSON result
            String explainJson = jdbcTemplate.execute((StatementCallback<String>) statement -> {
                boolean hasResultSet = statement.execute(explainSql);
                if (!hasResultSet) {
                    throw new SQLException("EXPLAIN did not return a result set");
                }
                try (ResultSet rs = statement.getResultSet()) {
                    if (rs.next()) {
                        return rs.getString(1);
                    }
                    throw new SQLException("EXPLAIN result set is empty");
                }
            });

            if (explainJson == null) {
                throw new SQLException("EXPLAIN returned null result");
            }

            LOGGER.debug("EXPLAIN executed successfully, result length: {} chars", explainJson.length());
            return explainJson;

        } catch (DataAccessException daex) {
            // Translate DataAccessExceptions to domain exceptions
            throw exceptionTranslator.translate(daex, sql);
        } catch (Exception ex) {
            // Handle any other unexpected exceptions
            throw new SqlAnalysisException("UNKNOWN_ERROR", "Failed to execute EXPLAIN: " + ex.getMessage(),
                    "Check SQL syntax and database connection");
        }
    }
}