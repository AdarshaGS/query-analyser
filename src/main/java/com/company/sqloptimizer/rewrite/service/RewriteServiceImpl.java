package com.company.sqloptimizer.rewrite.service;

import com.company.sqloptimizer.rewrite.ai.ClaudeRewriteService;
import com.company.sqloptimizer.rewrite.dto.RewriteSqlRequest;
import com.company.sqloptimizer.rewrite.dto.RewriteSqlResponse;
import com.company.sqloptimizer.rewrite.api.RewritePromptBuilder;
import com.company.sqloptimizer.analyzer.SchemaInfo;
import com.company.sqloptimizer.metadata.MetadataCollector;
import com.company.sqloptimizer.parser.SqlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service implementation for SQL rewrite functionality.
 */
@Service
public class RewriteServiceImpl implements RewriteService {

    private static final Logger log = LoggerFactory.getLogger(RewriteServiceImpl.class);

    private final RewritePromptBuilder promptBuilder;
    private final ClaudeRewriteService claudeRewriteService;
    private final MetadataCollector metadataCollector;
    private final SqlParser sqlParser;

    @Autowired
    public RewriteServiceImpl(RewritePromptBuilder promptBuilder,
                              ClaudeRewriteService claudeRewriteService,
                              MetadataCollector metadataCollector,
                              SqlParser sqlParser) {
        this.promptBuilder = promptBuilder;
        this.claudeRewriteService = claudeRewriteService;
        this.metadataCollector = metadataCollector;
        this.sqlParser = sqlParser;
    }

    /**
     * Validates the rewrite request and collects necessary metadata.
     */
    private void validateRequestAndCollectMetadata(RewriteSqlRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getSql() == null || request.getSql().isBlank()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }

        // Collect schema metadata and table statistics if not already provided
        if (request.getSchemaMetadata() == null || request.getTableStatistics() == null ||
                request.getTableStatistics().isEmpty()) {
            collectMetadataIfNeeded(request);
        }

        boolean hasIssueIndex = request.getSelectedIssueIndex() != null && request.getSelectedIssueIndex() >= 0;
        boolean hasRecommendationIndex = request.getSelectedRecommendationIndex() != null && request.getSelectedRecommendationIndex() >= 0;

        // If both indices are provided, that's ambiguous - throw an error
        if (hasIssueIndex && hasRecommendationIndex) {
            throw new IllegalArgumentException("Only one of selectedIssueIndex or selectedRecommendationIndex should be provided");
        }

        // Validate bounds if indices are provided and analysis result is available
        if (hasIssueIndex) {
            List<?> issues = request.getAnalysisResult() != null ? request.getAnalysisResult().getIssues() : Collections.emptyList();
            if (issues == null || request.getSelectedIssueIndex() >= issues.size()) {
                throw new IllegalArgumentException("Selected issue index is out of bounds");
            }
        }
        if (hasRecommendationIndex) {
            List<?> recommendations = request.getAnalysisResult() != null ? request.getAnalysisResult().getRecommendations() : Collections.emptyList();
            if (recommendations == null || request.getSelectedRecommendationIndex() >= recommendations.size()) {
                throw new IllegalArgumentException("Selected recommendation index is out of bounds");
            }
        }
    }

    /**
     * Collects metadata for tables detected in the SQL query if not already provided.
     * Uses the same approach as ReportAnalysisService - parses SQL directly to extract table names.
     */
    private void collectMetadataIfNeeded(RewriteSqlRequest request) {
        // If we already have schema metadata and table statistics, skip collection
        if (request.getSchemaMetadata() != null &&
            request.getTableStatistics() != null &&
            !request.getTableStatistics().isEmpty()) {
            return;
        }

        // Parse SQL directly to extract table names (same approach as ReportAnalysisService)
        Set<String> tableNames = new HashSet<>();
        try {
            // Use the sql parser to extract table names from the SQL
            var parsedQuery = sqlParser.parse(request.getSql());
            tableNames.addAll(parsedQuery.getTables());

            // If we have an enhanced parsed query, also get any additional tables from subqueries etc.
            if (parsedQuery instanceof com.company.sqloptimizer.parser.EnhancedParsedQuery) {
                var enhanced = (com.company.sqloptimizer.parser.EnhancedParsedQuery) parsedQuery;
                // Tables are already captured in getTables() from the base class
            }
        } catch (Exception e) {
            // If parsing fails, fall back to using detected tables from analysis result
            // This maintains backward compatibility
            List<String> detectedTables = request.getAnalysisResult() != null ?
                    request.getAnalysisResult().getDetectedTables() :
                    Collections.emptyList();
            tableNames = detectedTables.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        }

        if (tableNames.isEmpty()) {
            // If no tables detected, initialize empty metadata
            if (request.getSchemaMetadata() == null) {
                request.setSchemaMetadata(new SchemaInfo(Collections.emptySet()));
            }
            if (request.getTableStatistics() == null || request.getTableStatistics().isEmpty()) {
                request.setTableStatistics(new HashMap<>());
            }
            return;
        }

        // Get schema metadata using the metadata collector (same as ReportAnalysisService)
        SchemaInfo schemaMetadata = metadataCollector.collectMetadata(tableNames);
        request.setSchemaMetadata(schemaMetadata);

        // Collect table statistics (row counts, etc.) using the metadata collector
        Map<String, Object> tableStatistics = new HashMap<>();
        for (String tableName : tableNames) {
            long rowCount = metadataCollector.getRowCount(tableName, null);
            tableStatistics.put(tableName, Map.of(
                    "rowCount", rowCount
            ));
        }
        request.setTableStatistics(tableStatistics);
    }

    @Override
    public RewriteSqlResponse rewriteSql(RewriteSqlRequest request) {
        // Validate the request and collect metadata if needed
        validateRequestAndCollectMetadata(request);

        // If no specific issue or recommendation is selected, return original query unchanged
        boolean hasIssueIndex = request.getSelectedIssueIndex() != null && request.getSelectedIssueIndex() >= 0;
        boolean hasRecommendationIndex = request.getSelectedRecommendationIndex() != null && request.getSelectedRecommendationIndex() >= 0;

        if (!hasIssueIndex && !hasRecommendationIndex) {
            return new RewriteSqlResponse(request.getSql());
        }

        // Build prompts for AI processing
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
                request.getSql(),
                request.getExecutionPlan(),
                request.getSchemaMetadata(),
                request.getTableStatistics(),
                request.getAnalysisResult()
        );

        try {
            // Invoke Claude AI
            String optimizedQuery = claudeRewriteService.rewriteSql(systemPrompt, userPrompt);

            // Ensure we have a valid query
            if (optimizedQuery == null || optimizedQuery.isBlank()) {
                optimizedQuery = request.getSql();
            }

            return new RewriteSqlResponse(optimizedQuery);
        } catch (Exception e) {
            log.error("Failed to invoke Claude AI for rewrite: {}", e.getMessage(), e);
            // Return fallback response
            return new RewriteSqlResponse(request.getSql());
        }
    }
}