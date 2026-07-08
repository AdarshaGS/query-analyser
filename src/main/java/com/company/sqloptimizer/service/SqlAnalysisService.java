package com.company.sqloptimizer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.company.sqloptimizer.parser.EnhancedParsedQuery;
import com.company.sqloptimizer.parser.ParsedQuery;
import com.company.sqloptimizer.parser.SqlParser;
import com.company.sqloptimizer.service.exceptiontranslator.ExceptionTranslator;
import com.company.sqloptimizer.service.exceptiontranslator.SqlAnalysisException;
import com.company.sqloptimizer.service.sqlpreprocessor.SqlPreprocessor;

import lombok.RequiredArgsConstructor;

/**
 * Service for analyzing SQL queries by executing EXPLAIN and analyzing the execution plan.
 * This class acts as an orchestrator, delegating specific responsibilities to specialized components.
 */
@Service
@RequiredArgsConstructor
public class SqlAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqlAnalysisService.class);

    private final SqlPreprocessor sqlPreprocessor;
    private final ExceptionTranslator exceptionTranslator;
    @Qualifier("enhancedSqlParser")
    private final SqlParser sqlParser;

    /**
     * Analyzes a SQL query by executing EXPLAIN and providing optimization recommendations.
     *
     * @param sqlQuery the SQL query to analyze
     * @return analysis results including score, issues, and recommendations
     */
    public SqlAnalysisResponse analyzeSql(String sqlQuery) {
        LOGGER.info("Starting SQL analysis for query: {}", truncateSqlForLogging(sqlQuery));

        try {
            // Step 1: Validate SQL
            validateSql(sqlQuery);

            // Step 2: Preprocess SQL
            String processedSql = sqlPreprocessor.preprocess(sqlQuery);

            // Step 3: Parse SQL to extract metadata (tables, joins, where clauses, etc.)
            ParsedQuery parsedQuery = sqlParser.parse(processedSql);
            EnhancedParsedQuery enhancedParsedQuery = null;
            if (parsedQuery instanceof EnhancedParsedQuery) {
                enhancedParsedQuery = (EnhancedParsedQuery) parsedQuery;
            }

            // Step 4: Execute EXPLAIN
            // String explainJson = explainExecutor.executeExplain(processedSql);
            // LOGGER.debug("Obtained EXPLAIN JSON (truncated): {}", truncateJsonForLogging(explainJson));

            // // Step 6: Parse JSON
            // var explainResult = explainJsonParser.parse(explainJson);

            // Build the response with all metadata
            List<String> detectedTablesList = (parsedQuery.getTables() != null)
                    ? new ArrayList<>(parsedQuery.getTables())
                    : Collections.emptyList();
            List<String> groupByClausesList = (enhancedParsedQuery != null)
                    ? enhancedParsedQuery.getGroupByClauses()
                    : Collections.emptyList();
            List<String> orderByClausesList = (enhancedParsedQuery != null)
                    ? enhancedParsedQuery.getOrderByClauses()
                    : Collections.emptyList();
            List<String> subqueriesList = (enhancedParsedQuery != null)
                    ? enhancedParsedQuery.getSubqueries()
                    : Collections.emptyList();

            return SqlAnalysisResponse.builder()
                    // .issues(explainResult.getIssues())
                    // .recommendations(explainResult.getRecommendations())
                    .detectedTables(detectedTablesList)
                    .joins(parsedQuery.getJoins())
                    .whereClauses(parsedQuery.getWhereClauses())
                    .aggregations(parsedQuery.getAggregations())
                    .selectFields(parsedQuery.getSelectFields())
                    .groupByClauses(groupByClausesList)
                    .orderByClauses(orderByClausesList)
                    .subqueries(subqueriesList)
                    .build();

        } catch (DataAccessException ex) {
            // Handle and translate data access exceptions (SQL-related)
            throw exceptionTranslator.translate(ex, sqlQuery);
        } catch (Exception ex) {
            // Handle other exceptions (validation, parsing, etc.)
            if (ex instanceof SqlAnalysisException) {
                throw (SqlAnalysisException) ex;
            }
            throw new RuntimeException("Failed to analyze SQL: " + ex.getMessage(), ex);
        }
    }

    /**
     * Validates that the SQL query is not null or empty.
     *
     * @param sqlQuery the SQL query to validate
     * @throws IllegalArgumentException if the SQL is invalid
     */
    private void validateSql(String sqlQuery) {
        if (sqlQuery == null || sqlQuery.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be null or empty");
        }
    }

    

    /**
     * Truncates SQL for logging to prevent excessively long log entries.
     *
     * @param sql the SQL query
     * @return truncated SQL suitable for logging
     */
    private String truncateSqlForLogging(String sql) {
        if (sql == null) {
            return null;
        }
        int maxLength = 200;
        if (sql.length() <= maxLength) {
            return sql;
        }
        return sql.substring(0, maxLength) + "... [truncated]";
    }
}