package com.company.sqloptimizer.service;

import com.company.sqloptimizer.dto.ExplainAnalysisResponse;
import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.Severity;
import com.company.sqloptimizer.service.exceptiontranslator.ExceptionTranslator;
import com.company.sqloptimizer.service.exceptiontranslator.SqlAnalysisException;
import com.company.sqloptimizer.service.explainexecutor.ExplainExecutor;
import com.company.sqloptimizer.service.jsonparser.ExplainJsonParser;
import com.company.sqloptimizer.service.scorecalculator.ScoreCalculator;
import com.company.sqloptimizer.service.sqlpreprocessor.SqlPreprocessor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Service for analyzing SQL queries by executing EXPLAIN and analyzing the execution plan.
 * This class acts as an orchestrator, delegating specific responsibilities to specialized components.
 */
@Service
@RequiredArgsConstructor
public class ExplainAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExplainAnalysisService.class);

    private final SqlPreprocessor sqlPreprocessor;
    private final ExplainExecutor explainExecutor;
    private final ExplainJsonParser explainJsonParser;
    private final ExceptionTranslator exceptionTranslator;

    /**
     * Analyzes a SQL query by executing EXPLAIN and providing optimization recommendations.
     *
     * @param sqlQuery the SQL query to analyze
     * @return analysis of the execution plan including score, issues, and recommendations
     */
    public ExplainAnalysisResponse analyzeExplain(String sqlQuery) {
        LOGGER.info("Starting EXPLAIN analysis for query: {}", sqlQuery);

        try {
            // Step 1: Validate SQL
            validateSql(sqlQuery);

            // Step 2: Preprocess SQL
            String processedSql = sqlPreprocessor.preprocess(sqlQuery);
            LOGGER.debug("Preprocessed SQL: {}", processedSql);

            // Step 3: Execute EXPLAIN
            String explainJson = explainExecutor.executeExplain(processedSql);
            LOGGER.debug("Obtained EXPLAIN JSON (length: {} chars)", explainJson.length());

            // Step 4: Parse JSON
            ExplainAnalysisResult result = explainJsonParser.parse(explainJson);

            // Build the response
            return ExplainAnalysisResponse.builder()
                    .issues(result.getIssues())
                    .recommendations(result.getRecommendations())
                    .fullScanDetected(result.isFullScanDetected())
                    .tempTableDetected(result.isTempTableDetected())
                    .fileSortDetected(result.isFileSortDetected())
                    .nestedLoopDetected(result.isNestedLoopDetected())
                    .rowExplosionDetected(result.isRowExplosionDetected())
                    .build();

        } catch (Exception ex) {
            // Handle and translate exceptions
            if (ex instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) ex;
            }
            if (ex instanceof DataAccessException) {
                throw exceptionTranslator.translate((DataAccessException) ex, sqlQuery);
            }
            // For any other exception, create a domain exception
            throw new SqlAnalysisException("UNKNOWN_ERROR", "Unexpected error during analysis: " + ex.getMessage(), "Contact support");
        }
    }

    /**
     * Validates that the SQL query is not null.
     *
     * @param sqlQuery the SQL query to validate
     * @throws IllegalArgumentException if the SQL is invalid
     */
    private void validateSql(String sqlQuery) {
        if (sqlQuery == null) {
            throw new IllegalArgumentException("sqlQuery cannot be null");
        }
    }
}