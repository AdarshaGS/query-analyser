package com.company.sqloptimizer.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import com.company.sqloptimizer.ai.AiAnalysisProvider;
import com.company.sqloptimizer.ai.AiAnalysisResult;
import com.company.sqloptimizer.ai.AnalysisRequest;
import com.company.sqloptimizer.ai.ClaudeIssue;
import com.company.sqloptimizer.ai.ClaudeRecommendation;
import com.company.sqloptimizer.analyzer.SchemaInfo;
import com.company.sqloptimizer.dto.AnalysisReport;
import com.company.sqloptimizer.dto.ExplainAnalysisResponse;
import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.IssueDto;
import com.company.sqloptimizer.dto.RecommendationDto;
import com.company.sqloptimizer.dto.ReportAnalysisRequest;
import com.company.sqloptimizer.dto.ReportAnalysisResponse;
import com.company.sqloptimizer.dto.Severity;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.company.sqloptimizer.entity.AnalysisHistory;
import com.company.sqloptimizer.entity.TableInfo;
import com.company.sqloptimizer.metadata.MetadataCollector;
import com.company.sqloptimizer.recommendation.RecommendationEngine;
import com.company.sqloptimizer.repository.AnalysisHistoryRepository;
import com.company.sqloptimizer.rewrite.dto.RewriteSqlRequest;
import com.company.sqloptimizer.rewrite.dto.RewriteSqlResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

/**
 * Service for generating comprehensive SQL analysis reports.
 * This service combines SQL analysis, EXPLAIN analysis, metadata collection,
 * and AI-powered insights to produce a detailed report.
 */
@Service
@RequiredArgsConstructor
public class ReportAnalysisService {

    private final SqlAnalysisService sqlAnalysisService;
    private final MetadataCollector metadataCollector;
    private final List<AiAnalysisProvider> aiAnalysisProviders;
    private final AnalysisHistoryRepository historyRepository;
    private final RecommendationEngine recommendationEngine;
    private final JdbcTemplate jdbcTemplate;
    private final QueryLookupService queryLookupService;
    private final ObjectMapper objectMapper;
    private final ExplainAnalysisService explainAnalysisService;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final SchemaService schemaService;

    private static final Logger log = LoggerFactory.getLogger(ReportAnalysisService.class);

    public AnalysisReport analyzeReport(ReportAnalysisRequest request) {

        final String sqlQuery;
        if (request != null && request.getQuery() != null && !request.getQuery().isBlank()) {
            sqlQuery = request.getQuery();
        } else if (request != null && request.getQueryId() != null) {
            String queryOpt = queryLookupService.getQueryById(request.getQueryId());
            sqlQuery = queryOpt;
        } else if (request != null && request.getReportName() != null && !request.getReportName().isBlank()) {
            String queryOpt = queryLookupService.getQueryByName(request.getReportName());
            sqlQuery = queryOpt;
        } else {
            sqlQuery = "";
        }

        // Check if we have a cached result for this query
        final String queryHash = getQueryHash(sqlQuery);
        try {
            Optional<AnalysisHistory> cached = historyRepository.findByQueryHash(queryHash);
            if (cached.isPresent()) {
                // In a real implementation, we would deserialize the cached result
                // For now, we'll just return a new analysis (caching is left as an exercise)
                // return deserializeFromCache(cached.get());
            }
        } catch (IncorrectResultSizeDataAccessException e) {
            // Multiple cache entries found for the same hash; treat as no cache.
            System.err.println(
                    "Multiple cache entries found for query hash: " + queryHash + "; proceeding without cache.");
        }

        // Perform SQL analysis
        SqlAnalysisResponse sqlAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);

        // Obtain EXPLAIN JSON: use provided if available, otherwise generate
        final String explainJson;
        String explainJsonInput = request.getExplainJson();
        if (explainJsonInput == null || explainJsonInput.isBlank()) {
            try {
                String rawQuery = sqlQuery.trim();
                // Remove trailing semicolon if present
                if (rawQuery.endsWith(";")) {
                    rawQuery = rawQuery.substring(0, rawQuery.length() - 1);
                }
                String explainSql = "EXPLAIN FORMAT=JSON " + rawQuery;
                explainJsonInput = jdbcTemplate.queryForObject(explainSql, String.class);
            } catch (Exception e) {
                // Fallback to null and log warning
                System.err.println("Failed to generate EXPLAIN JSON: " + e.getMessage());
                explainJsonInput = null;
            }
        }
        explainJson = explainJsonInput;

        // Perform EXPLAIN analysis if we have JSON
        ExplainAnalysisResponse explainAnalysis = null;
        if (explainJson != null && !explainJson.isBlank()) {
            explainAnalysis = explainAnalysisService.analyzeExplain(explainJson);
        }

        // Collect metadata for the tables in the query
        Set<String> tableNames = sqlAnalysis.getDetectedTables().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        final SchemaInfo schemaMetadata;
        Map<String, Object> tableStatistics = new HashMap<>();
        if (!tableNames.isEmpty()) {
            // Get schema metadata
            Set<TableInfo> tables = metadataCollector.collectMetadata(tableNames).getAllTables();
            schemaMetadata = new SchemaInfo(tables);

            // Collect table statistics (row counts, etc.)
            for (String tableName : tableNames) {
                // In a real implementation, we would get more detailed statistics
                long rowCount = metadataCollector.getRowCount(tableName, null);
                tableStatistics.put(tableName, Map.of(
                        "rowCount", rowCount));
            }
        } else {
            schemaMetadata = null;
        }

        // Select appropriate AI provider based on request type
        AnalysisRequest providerSelectionRequest = AnalysisRequest.builder()
                .requestType("analyse") // We're doing analysis in this method
                .build();
        AiAnalysisProvider selectedProvider = selectAiAnalysisProvider(providerSelectionRequest);

        // Run AI analysis asynchronously (if configured)
        AiAnalysisResult aiAnalysis = null;
        if (selectedProvider != null) {
            AnalysisRequest aiRequest = AnalysisRequest.builder()
                    .sqlAnalysis(sqlAnalysis)
                    .explainAnalysis(explainAnalysis != null ? convertToExplainAnalysisResult(explainAnalysis) : null)
                    .schemaMetadata(schemaMetadata)
                    .tableStatistics(tableStatistics)
                    .sqlQuery(sqlQuery)
                    .requestType("analyse")
                    .build();

            aiAnalysis = selectedProvider.analyze(aiRequest);
        }

        // Generate the final report
        AnalysisReport report = generateReport(sqlAnalysis, explainAnalysis, aiAnalysis, schemaMetadata,
                tableStatistics);
        // Set the request identifier (query hash) so it can be used to retrieve stored
        // components
        report.setRequestIdentifier(queryHash);

        // Save to history (async fire-and-forget)
        CompletableFuture.runAsync(() -> {
            try {
                String sqlAnalysisJson = null;
                String explainAnalysisJson = null;
                String schemaMetadataJson = null;
                String tableStatisticsJson = null;
                boolean serializationFailed = false;

                try {
                    // Serialize SQL Analysis
                    if (sqlAnalysis != null) {
                        try {
                            sqlAnalysisJson = objectMapper.writeValueAsString(sqlAnalysis);
                        } catch (Exception e) {
                            log.error("Failed to serialize SQL analysis for request identifier {}: {}",
                                    queryHash, e.getMessage(), e);
                            serializationFailed = true;
                        }
                    }

                    // Explain analysis is already a JSON string from the database
                    if (explainJson != null && !explainJson.isBlank()) {
                        explainAnalysisJson = explainJson;
                    }

                    // Serialize Schema Metadata
                    if (schemaMetadata != null) {
                        try {
                            schemaMetadataJson = objectMapper.writeValueAsString(schemaMetadata);
                        } catch (Exception e) {
                            log.error("Failed to serialize schema metadata for request identifier {}: {}",
                                    queryHash, e.getMessage(), e);
                            serializationFailed = true;
                        }
                    }

                    // Serialize Table Statistics
                    if (tableStatistics != null && !tableStatistics.isEmpty()) {
                        try {
                            tableStatisticsJson = objectMapper.writeValueAsString(tableStatistics);
                        } catch (Exception e) {
                            log.error("Failed to serialize table statistics for request identifier {}: {}",
                                    queryHash, e.getMessage(), e);
                            serializationFailed = true;
                        }
                    }
                } catch (Exception e) {
                    log.error("Unexpected error during serialization for request identifier {}: {}",
                            queryHash, e.getMessage(), e);
                    serializationFailed = true;
                }

                // If serialization failed for critical components, try to regenerate and
                // serialize again
                if (serializationFailed) {
                    log.warn("Serialization failed for request identifier {}, attempting regeneration", queryHash);
                    try {
                        // Regenerate SQL Analysis if needed
                        if (sqlAnalysis == null || sqlAnalysisJson == null) {
                            SqlAnalysisResponse regeneratedSqlAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);
                            if (regeneratedSqlAnalysis != null) {
                                sqlAnalysisJson = objectMapper.writeValueAsString(regeneratedSqlAnalysis);
                            }
                        }

                        // Regenerate EXPLAIN analysis if needed
                        if ((explainAnalysisJson == null || explainAnalysisJson.isBlank()) &&
                                sqlAnalysis != null) {
                            try {
                                String explainSql = "EXPLAIN FORMAT=JSON " + sqlQuery.trim().replaceAll(";$", "");
                                String regeneratedExplainJson = jdbcTemplate.queryForObject(explainSql, String.class);
                                if (regeneratedExplainJson != null && !regeneratedExplainJson.isBlank()) {
                                    explainAnalysisJson = regeneratedExplainJson;
                                }
                            } catch (Exception e) {
                                log.debug("Failed to regenerate EXPLAIN for request identifier {}: {}",
                                        queryHash, e.getMessage());
                                // Keep explainAnalysisJson as null
                            }
                        }

                        // Regenerate Schema Metadata if needed
                        if (schemaMetadata == null || schemaMetadataJson == null) {
                            if (sqlAnalysis != null) {
                                Set<String> regeneratedTableNames = sqlAnalysis.getDetectedTables().stream()
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
                                if (!regeneratedTableNames.isEmpty()) {
                                    Set<TableInfo> tables = metadataCollector.collectMetadata(regeneratedTableNames)
                                            .getAllTables();
                                    schemaMetadataJson = objectMapper.writeValueAsString(new SchemaInfo(tables));
                                } else {
                                    schemaMetadataJson = objectMapper
                                            .writeValueAsString(new SchemaInfo(Collections.emptySet()));
                                }
                            } else {
                                schemaMetadataJson = objectMapper
                                        .writeValueAsString(new SchemaInfo(Collections.emptySet()));
                            }
                        }

                        // Regenerate Table Statistics if needed
                        if ((tableStatistics == null || tableStatistics.isEmpty()) &&
                                sqlAnalysis != null) {
                            Set<String> regeneratedTableNames = sqlAnalysis.getDetectedTables().stream()
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toSet());
                            if (!regeneratedTableNames.isEmpty()) {
                                Map<String, Object> regeneratedTs = new HashMap<>();
                                for (String tableName : regeneratedTableNames) {
                                    long rowCount = metadataCollector.getRowCount(tableName, null);
                                    regeneratedTs.put(tableName, Map.of("rowCount", rowCount));
                                }
                                tableStatisticsJson = objectMapper.writeValueAsString(regeneratedTs);
                            } else {
                                // Note: We don't serialize empty tableStatistics as it would be "{}"
                                // which is valid JSON but we'll leave it null to indicate empty
                            }
                        }
                    } catch (Exception e) {
                        log.error(
                                "Failed to regenerate and serialize analysis components for request identifier {}: {}",
                                queryHash, e.getMessage(), e);
                        // If regeneration also fails, we'll proceed with what we have (possibly null
                        // values)
                    }
                }

                AnalysisHistory history = AnalysisHistory.builder()
                        .queryHash(queryHash)
                        .queryText(sqlQuery)
                        .sqlAnalysisJson(sqlAnalysisJson)
                        .explainAnalysisJson(explainAnalysisJson)
                        .schemaMetadataJson(schemaMetadataJson)
                        .tableStatisticsJson(tableStatisticsJson)
                        .analysisResult(serializeToJson(report)) // In a real app, we'd use a JSON library
                        .requestIdentifier(queryHash)
                        .build();
                historyRepository.save(history);
            } catch (Exception e) {
                // Log the error but don't fail the request
                // In a real app, we would use proper logging
                System.err.println("Failed to save analysis history: " + e.getMessage());
            }
        });

        return report;
    }

    /**
     * Selects the appropriate AI analysis provider based on the request type.
     *
     * @param request the analysis request containing the request type
     * @return the selected AI analysis provider
     */
    private AiAnalysisProvider selectAiAnalysisProvider(AnalysisRequest request) {
        if (request != null) {
            String requestType = request.getRequestType();
            if (requestType != null) {
                switch (requestType.toLowerCase()) {
                    case "rewrite":
                        // Prefer ClaudeApiProvider for rewrite operations
                        return aiAnalysisProviders.stream()
                                .filter(provider -> provider.getClass().getSimpleName().equals("ClaudeApiProvider"))
                                .findFirst()
                                .orElseGet(() -> aiAnalysisProviders.get(0)); // Fallback to first

                    case "analyse":
                    case "analysis":
                        // Prefer ClaudeAnalysisService for analysis operations if available
                        return aiAnalysisProviders.stream()
                                .filter(provider -> provider.getClass().getSimpleName().equals("ClaudeAnalysisService"))
                                .findFirst()
                                .orElseGet(() -> aiAnalysisProviders.get(0)); // Fallback to first

                    default:
                        // For any other type, use the first available provider
                        return aiAnalysisProviders.get(0);
                }
            }
        }

        // No request type specified, return first provider
        return aiAnalysisProviders.get(0);
    }

    /**
     * Generates the final analysis report by combining all sources of information.
     */
    private AnalysisReport generateReport(SqlAnalysisResponse sqlAnalysis,
            ExplainAnalysisResponse explainAnalysis,
            AiAnalysisResult aiAnalysis,
            SchemaInfo schemaMetadata,
            Map<String, Object> tableStatistics) {
        // Start with the score from SQL analysis
        int score = sqlAnalysis.getComplexityScore();

        // Adjust score based on EXPLAIN analysis (if available)
        if (explainAnalysis != null) {
            // In a real implementation, we would have a more sophisticated scoring
            // algorithm
            // For now, we'll just take the minimum of the two scores
            score = Math.min(score, explainAnalysis.getScore());
        }

        // Further adjust based on AI analysis confidence (if available)
        // This is just an example - in reality, we would have a more complex algorithm
        double confidence = 0.0;
        if (aiAnalysis != null) {
            confidence = aiAnalysis.getConfidence();
            // We could adjust the score based on AI confidence, but for now we'll just
            // report it
        }

        // Determine severity based on the final score
        String severity;
        if (score >= 90) {
            severity = "LOW";
        } else if (score >= 75) {
            severity = "MEDIUM";
        } else if (score >= 50) {
            severity = "HIGH";
        } else {
            severity = "CRITICAL";
        }

        // Combine recommendations from all sources using the recommendation engine
        List<RecommendationDto> recommendations = recommendationEngine.combineRecommendations(sqlAnalysis,
                explainAnalysis, aiAnalysis);

        // Combine issues from all sources
        List<IssueDto> allIssues = new ArrayList<>();
        allIssues.addAll(sqlAnalysis.getIssues());
        if (explainAnalysis != null) {
            allIssues.addAll(explainAnalysis.getIssues());
        }
        // AI analysis doesn't return issues in our current model, but we could extend
        // it

        // Generate executive summary
        String executiveSummary = generateExecutiveSummary(sqlAnalysis, explainAnalysis, aiAnalysis, score);

        // Estimate impact (simplified)
        String estimatedImpact = estimateImpact(score, sqlAnalysis.getDetectedTables().size());

        // Build the basic report
        AnalysisReport.AnalysisReportBuilder reportBuilder = AnalysisReport.builder()
                .score(score)
                .severity(severity)
                .executiveSummary(executiveSummary)
                .issues(allIssues)
                .recommendations(recommendations)
                .estimatedImpact(estimatedImpact)
                .confidence(confidence)
                .aiRawResponse(aiAnalysis != null ? aiAnalysis.getRawResponse() : null);

        return reportBuilder.build();
    }

    /**
     * Generates an executive summary based on the analysis results.
     */
    private String generateExecutiveSummary(SqlAnalysisResponse sqlAnalysis,
            ExplainAnalysisResponse explainAnalysis,
            AiAnalysisResult aiAnalysis,
            int score) {
        StringBuilder summary = new StringBuilder();
        summary.append("SQL query analysis completed. ");

        if (score >= 90) {
            summary.append("The query appears to be well-optimized with minimal performance issues.");
        } else if (score >= 75) {
            summary.append("The query has some minor performance issues that could be improved.");
        } else if (score >= 50) {
            summary.append("The query has moderate performance issues that should be addressed.");
        } else {
            summary.append("The query has significant performance issues that require immediate attention.");
        }

        if (sqlAnalysis.getIssues() != null && !sqlAnalysis.getIssues().isEmpty()) {
            summary.append(" Found ").append(sqlAnalysis.getIssues().size())
                    .append(" issue(s) from rule-based analysis.");
        }

        if (explainAnalysis != null && explainAnalysis.getIssues() != null && !explainAnalysis.getIssues().isEmpty()) {
            summary.append(" Found ").append(explainAnalysis.getIssues().size())
                    .append(" issue(s) from EXPLAIN analysis.");
        }

        if (aiAnalysis != null) {
            summary.append(" AI analysis confidence: ")
                    .append(String.format("%.0f%%", aiAnalysis.getConfidence() * 100));
        }

        return summary.toString();
    }

    /**
     * Estimates the potential performance impact of implementing recommendations.
     */
    private String estimateImpact(int score, int tableCount) {
        if (score >= 90) {
            return "Low - Minor improvements possible";
        } else if (score >= 75) {
            return "Moderate - Noticeable performance improvement expected";
        } else if (score >= 50) {
            return "High - Significant performance improvement expected";
        } else {
            return "Critical - Major performance improvement expected";
        }
    }

    /**
     * Converts ExplainAnalysisResponse to ExplainAnalysisResult for AI processing.
     */
    private ExplainAnalysisResult convertToExplainAnalysisResult(ExplainAnalysisResponse response) {
        return ExplainAnalysisResult.builder()
                .fullScanDetected(response.isFullScanDetected())
                .tempTableDetected(response.isTempTableDetected())
                .fileSortDetected(response.isFileSortDetected())
                .nestedLoopDetected(response.isNestedLoopDetected())
                .rowExplosionDetected(response.isRowExplosionDetected())
                .issues(response.getIssues())
                .recommendations(response.getRecommendations())
                .build();
    }

    /**
     * Calculates a hash of the query for caching purposes.
     */
    private String getQueryHash(String query) {
        return DigestUtils.md5DigestAsHex(query.getBytes());
    }

    /**
     * Serializes an AnalysisReport to JSON string.
     * In a real application, we would use a JSON library like Jackson.
     */
    private String serializeToJson(AnalysisReport report) {
        // This is a simplified implementation - in reality, use a proper JSON
        // serializer
        return String.format(
                "{\"score\": %d, \"severity\": \"%s\", \"executiveSummary\": \"%s\", \"estimatedImpact\": \"%s\", \"confidence\": %f, \"aiRawResponse\": \"%s\"}",
                report.getScore(),
                report.getSeverity() != null ? report.getSeverity().replace("\"", "\\\"") : "",
                report.getExecutiveSummary() != null ? report.getExecutiveSummary().replace("\"", "\\\"") : "",
                report.getEstimatedImpact() != null ? report.getEstimatedImpact().replace("\"", "\\\"") : "",
                report.getConfidence(),
                report.getAiRawResponse() != null ? report.getAiRawResponse().replace("\"", "\\\"") : "");
    }

    /**
     * Rewrites SQL query based on selected issue or recommendation.
     * If no selection provided, lets AI consider all available options for
     * optimization.
     */
    public RewriteSqlResponse rewriteSql(RewriteSqlRequest request) {
        // Validate the request and collect metadata if needed
        validateRequestAndCollectMetadata(request);

        // Create AI analysis request (same pattern as in analyzeReport)
        AnalysisRequest aiRequest = AnalysisRequest.builder()
                .sqlAnalysis(request.getAnalysisResult())
                .explainAnalysis(request.getExecutionPlan())
                .schemaMetadata(request.getSchemaMetadata())
                .tableStatistics(request.getTableStatistics())
                .sqlQuery(request.getSql())
                .requestType("rewrite")
                .build();

        // Select AI provider based on request type
        AiAnalysisProvider selectedProvider = selectAiAnalysisProvider(aiRequest);

        // Invoke AI analysis provider (same method used in analyzeReport)
        AiAnalysisResult aiAnalysis = null;
        if (selectedProvider != null) {
            aiAnalysis = selectedProvider.analyze(aiRequest);
        }

        // Extract optimized query from AI result
        String optimizedQuery = request.getSql(); // Default to original query
        if (aiAnalysis != null) {
            // First, check the direct recommendedQuery field in AiAnalysisResult
            String directRecommendedQuery = aiAnalysis.getRecommendedQuery();
            if (directRecommendedQuery != null && !directRecommendedQuery.isEmpty()) {
                optimizedQuery = directRecommendedQuery;
            } else {
                // Check recommendations first (preferred source for rewrite suggestions)
                if (aiAnalysis.getClaudeRecommendations() != null && !aiAnalysis.getClaudeRecommendations().isEmpty()) {
                    for (ClaudeRecommendation recommendation : aiAnalysis.getClaudeRecommendations()) {
                        if (recommendation.getRecommendedQuery() != null
                                && !recommendation.getRecommendedQuery().isEmpty()) {
                            optimizedQuery = recommendation.getRecommendedQuery();
                            break;
                        }
                    }
                }
                // If no recommendation has a query, check issues
                else if (aiAnalysis.getClaudeIssues() != null && !aiAnalysis.getClaudeIssues().isEmpty()) {
                    for (ClaudeIssue issue : aiAnalysis.getClaudeIssues()) {
                        if (issue.getRecommendedQuery() != null && !issue.getRecommendedQuery().isEmpty()) {
                            optimizedQuery = issue.getRecommendedQuery();
                            break;
                        }
                    }
                }
                // If still no query found, check raw response as fallback
                else if (aiAnalysis.getRawResponse() != null && !aiAnalysis.getRawResponse().isEmpty()) {
                    // Try to extract query from raw response if it's JSON
                    try {
                        // Check if raw response contains optimizedQuery field
                        if (aiAnalysis.getRawResponse().contains("\"optimizedQuery\"")) {
                            // Simple extraction - in a real app, use proper JSON parsing
                            int startIdx = aiAnalysis.getRawResponse().indexOf("\"optimizedQuery\"") + 16;
                            int endIdx = aiAnalysis.getRawResponse().indexOf('"', startIdx + 2);
                            if (endIdx > startIdx) {
                                String extracted = aiAnalysis.getRawResponse().substring(startIdx, endIdx);
                                if (!extracted.isEmpty() && !extracted.equalsIgnoreCase("null")) {
                                    optimizedQuery = extracted;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // If parsing fails, keep original query
                        log.debug("Failed to extract optimizedQuery from raw response: {}", e.getMessage());
                    }
                }
            }
        }

        // Build the response
        RewriteSqlResponse response = new RewriteSqlResponse(optimizedQuery);

        return response;
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
    }

    /**
     * Collects metadata for tables detected in the SQL query if not already
     * provided.
     * Uses the same approach as ReportAnalysisService.analyzeReport method.
     */
    private void collectMetadataIfNeeded(RewriteSqlRequest request) {
        // If we already have schema metadata and table statistics, skip collection
        if (request.getSchemaMetadata() != null &&
                request.getTableStatistics() != null &&
                !request.getTableStatistics().isEmpty()) {
            return;
        }

        // Get detected tables from analysis result (same approach as analyzeReport)
        Set<String> tableNames = new HashSet<>();
        if (request.getAnalysisResult() != null) {
            tableNames.addAll(request.getAnalysisResult().getDetectedTables().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        }

        // If no tables detected in analysis result, try to analyze the SQL directly
        if (tableNames.isEmpty()) {
            try {
                SqlAnalysisResponse sqlAnalysis = sqlAnalysisService.analyzeSql(request.getSql());
                tableNames.addAll(sqlAnalysis.getDetectedTables().stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
            } catch (Exception e) {
                // If analysis fails, leave tableNames empty - will get handled below
            }
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

        // Get schema metadata using the metadata collector (same as analyzeReport)
        Set<TableInfo> tables = metadataCollector.collectMetadata(tableNames).getAllTables();
        SchemaInfo schemaMetadata = new SchemaInfo(tables);
        request.setSchemaMetadata(schemaMetadata);

        // Collect table statistics (row counts, etc.) using the metadata collector
        // (same as analyzeReport)
        Map<String, Object> tableStatistics = new HashMap<>();
        for (String tableName : tableNames) {
            // In a real implementation, we would get more detailed statistics
            long rowCount = metadataCollector.getRowCount(tableName, null);
            tableStatistics.put(tableName, Map.of(
                    "rowCount", rowCount));
        }
        request.setTableStatistics(tableStatistics);
    }

    /**
     * Parses the Claude response into a RewriteSqlResponse object.
     * If parsing fails, returns a fallback response.
     */
    private RewriteSqlResponse parseClaudeResponse(String response, String originalSql) {
        try {
            // Clean the response - remove markdown code fences if present
            String cleanedResponse = response.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            // Parse JSON
            RewriteSqlResponse rewriteResponse = objectMapper.readValue(cleanedResponse, RewriteSqlResponse.class);

            // Ensure required fields are present
            if (rewriteResponse.getOptimizedQuery() == null || rewriteResponse.getOptimizedQuery().isBlank()) {
                rewriteResponse.setOptimizedQuery(originalSql);
            }
            return rewriteResponse;
        } catch (Exception e) {
            log.error("Failed to parse Claude response: {}", e.getMessage(), e);
            // Return fallback response
            return new RewriteSqlResponse(originalSql);
        }
    }

    /**
     * Rewrites SQL query based on a previous analysis request identifier.
     *
     * @param requestIdentifier the identifier of the previous analysis request
     * @return the SQL rewrite response containing the optimized query
     * @throws IllegalArgumentException if requestIdentifier is null or blank
     * @throws IllegalStateException    if no analysis record is found for the
     *                                  identifier
     */
    public RewriteSqlResponse rewriteSqlByRequestIdentifier(String requestIdentifier) {
        // Validate requestIdentifier
        if (requestIdentifier == null || requestIdentifier.isBlank()) {
            throw new IllegalArgumentException("Request identifier cannot be null or blank");
        }

        // Fetch the analysis history record
        Optional<AnalysisHistory> historyOptional = historyRepository.findByRequestIdentifier(requestIdentifier);
        if (historyOptional.isEmpty()) {
            throw new IllegalStateException("No analysis record found for request identifier: " + requestIdentifier);
        }

        AnalysisHistory history = historyOptional.get();

        // Deserialize the stored JSON fields to build the rewrite request
        String sqlQuery = history.getQueryText();
        SqlAnalysisResponse sqlAnalysis = null;
        ExplainAnalysisResult explainAnalysis = null;
        SchemaInfo schemaMetadata = null;
        Map<String, Object> tableStatistics = new HashMap<>();

        boolean deserializationFailed = false;

        try {
            // Deserialize SQL Analysis
            if (history.getSqlAnalysisJson() != null &&
                    !history.getSqlAnalysisJson().isBlank()) {
                Map<String, Object> response = objectMapper.readValue(history.getSqlAnalysisJson(),
                        new TypeReference<Map<String, Object>>() {
                        });
                sqlAnalysis = convertToSqlAnalysisResponse(response);
            } else {
                sqlAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);
            }

            // Deserialize EXPLAIN Analysis
            if (history.getExplainAnalysisJson() != null && !history.getExplainAnalysisJson().isBlank()) {
                // We need to convert the stored JSON to ExplainAnalysisResult
                // First deserialize to ExplainAnalysisResponse, then convert
                ExplainAnalysisResponse explainResponse = objectMapper.readValue(
                        history.getExplainAnalysisJson(), ExplainAnalysisResponse.class);
                explainAnalysis = new ExplainAnalysisResult(
                        explainResponse.isFullScanDetected(),
                        explainResponse.isTempTableDetected(),
                        explainResponse.isFileSortDetected(),
                        explainResponse.isNestedLoopDetected(),
                        explainResponse.isRowExplosionDetected(),
                        explainResponse.getIssues(),
                        explainResponse.getRecommendations());
            }

            // Deserialize Schema Metadata
            // if (history.getSchemaMetadataJson() != null &&
            // !history.getSchemaMetadataJson().isBlank()) {
            // schemaMetadata = objectMapper.readValue(history.getSchemaMetadataJson(),
            // SchemaInfo.class);
            // }

            // Deserialize Table Statistics
            if (history.getTableStatisticsJson() != null && !history.getTableStatisticsJson().isBlank()) {
                tableStatistics = objectMapper.readValue(history.getTableStatisticsJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                        });
            }
        } catch (Exception e) {
            log.error("Failed to deserialize analysis history for request identifier {}: {}",
                    requestIdentifier, e.getMessage(), e);
            deserializationFailed = true;
            // If deserialization fails, we'll proceed with null values and let the
            // rebuild logic handle missing data
        }

        // Rebuild missing components if deserialization failed or resulted in null
        // values
        if (deserializationFailed || sqlAnalysis == null) {
            try {
                sqlAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);
            } catch (Exception e) {
                log.error("Failed to regenerate SQL analysis for request identifier {}: {}",
                        requestIdentifier, e.getMessage(), e);
                // If we can't generate fresh analysis, we'll proceed with null
            }
        }

        // Rebuild missing explain analysis
        if (explainAnalysis == null) {
            try {
                String explainSql = "EXPLAIN FORMAT=JSON " + sqlQuery.trim().replaceAll(";$", "");
                String explainJson = jdbcTemplate.queryForObject(explainSql, String.class);
                if (explainJson != null && !explainJson.isBlank()) {
                    ExplainAnalysisResponse explainResponse = explainAnalysisService.analyzeExplain(explainJson);
                    explainAnalysis = new ExplainAnalysisResult(
                            explainResponse.isFullScanDetected(),
                            explainResponse.isTempTableDetected(),
                            explainResponse.isFileSortDetected(),
                            explainResponse.isNestedLoopDetected(),
                            explainResponse.isRowExplosionDetected(),
                            explainResponse.getIssues(),
                            explainResponse.getRecommendations());
                }
            } catch (Exception e) {
                log.debug("Failed to generate EXPLAIN analysis for request identifier {}: {}",
                        requestIdentifier, e.getMessage());
                // Continue without explain analysis
            }
        }

        // Rebuild missing schema metadata
        if (schemaMetadata == null) {
            try {
                Set<String> tableNames = new HashSet<>();
                if (sqlAnalysis != null) {
                    tableNames.addAll(sqlAnalysis.getDetectedTables().stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }

                if (tableNames.isEmpty()) {
                    // Try to get tables from SQL directly
                    SqlAnalysisResponse tempAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);
                    tableNames.addAll(tempAnalysis.getDetectedTables().stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }

                if (!tableNames.isEmpty()) {
                    Set<TableInfo> tables = metadataCollector.collectMetadata(tableNames).getAllTables();
                    schemaMetadata = new SchemaInfo(tables);
                } else {
                    schemaMetadata = new SchemaInfo(Collections.emptySet());
                }
            } catch (Exception e) {
                log.debug("Failed to collect schema metadata for request identifier {}: {}",
                        requestIdentifier, e.getMessage());
                schemaMetadata = new SchemaInfo(Collections.emptySet());
            }
        }

        // Rebuild missing table statistics
        if (tableStatistics == null || tableStatistics.isEmpty()) {
            try {
                Set<String> tableNames = new HashSet<>();
                if (sqlAnalysis != null) {
                    tableNames.addAll(sqlAnalysis.getDetectedTables().stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }

                if (tableNames.isEmpty()) {
                    // Try to get tables from SQL directly
                    SqlAnalysisResponse tempAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);
                    tableNames.addAll(tempAnalysis.getDetectedTables().stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }

                if (!tableNames.isEmpty()) {
                    Map<String, Object> ts = new HashMap<>();
                    for (String tableName : tableNames) {
                        long rowCount = metadataCollector.getRowCount(tableName, null);
                        ts.put(tableName, Map.of("rowCount", rowCount));
                    }
                    tableStatistics = ts;
                } else {
                    tableStatistics = new HashMap<>();
                }
            } catch (Exception e) {
                log.debug("Failed to collect table statistics for request identifier {}: {}",
                        requestIdentifier, e.getMessage());
                tableStatistics = new HashMap<>();
            }
        }

        // Build the rewrite request
        RewriteSqlRequest request = new RewriteSqlRequest();
        request.setSql(sqlQuery);
        request.setAnalysisResult(sqlAnalysis);
        request.setExecutionPlan(explainAnalysis);
        request.setSchemaMetadata(schemaMetadata);
        request.setTableStatistics(tableStatistics);

        // Use the existing rewriteSql method to process the request
        return rewriteSql(request);
    }

    @SuppressWarnings("unchecked")
    private SqlAnalysisResponse convertToSqlAnalysisResponse(Map<String, Object> response) {

        if (response == null || response.isEmpty()) {
            return SqlAnalysisResponse.builder().build();
        }

        SqlAnalysisResponse result = new SqlAnalysisResponse();

        // Complexity Score (85/100 -> 85)
        String complexityScore = (String) response.get("complexity_score");
        if (complexityScore != null && complexityScore.contains("/")) {
            try {
                result.setComplexityScore(Integer.parseInt(complexityScore.split("/")[0].trim()));
            } catch (Exception ignored) {
                result.setComplexityScore(0);
            }
        }

        // Estimated Severity
        if (result.getComplexityScore() >= 80) {
            result.setSeverity(Severity.HIGH);
        } else if (result.getComplexityScore() >= 50) {
            result.setSeverity(Severity.MEDIUM);
        } else {
            result.setSeverity(Severity.LOW);
        }

        // Issues
        List<IssueDto> issueDtos = new ArrayList<>();

        List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");

        if (issues != null) {

            for (Map<String, Object> issue : issues) {

                IssueDto dto = new IssueDto();

                dto.setIssue((String) issue.get("issue"));

                String severity = String.valueOf(issue.get("severity_contribution"));

                if (severity != null) {
                    try {
                        dto.setSeverity(
                                Severity.valueOf(severity.toUpperCase()));
                    } catch (Exception ignored) {
                    }
                }

                issueDtos.add(dto);
            }
        }

        result.setIssues(issueDtos);

        // Recommendations
        List<RecommendationDto> recommendationDtos = new ArrayList<>();

        List<Map<String, Object>> recommendations = (List<Map<String, Object>>) response.get("recommendations");

        if (recommendations != null) {
            for (Map<String, Object> recommendation : recommendations) {
                RecommendationDto dto = new RecommendationDto();
                dto.setMessage(
                        (String) recommendation.get("recommendation"));
                recommendationDtos.add(dto);
            }
        }
        result.setRecommendations(recommendationDtos);

        return result;
    }

    public ReportAnalysisResponse analyzeReportUsingMcp(ReportAnalysisRequest request) {
        try {
            String sqlQuery;
            if (request != null && request.getQuery() != null && !request.getQuery().isBlank()) {
                sqlQuery = request.getQuery();
            } else if (request != null && request.getQueryId() != null) {
                String queryOpt = queryLookupService.getQueryById(request.getQueryId());
                sqlQuery = queryOpt;
            } else if (request != null && request.getReportName() != null && !request.getReportName().isBlank()) {
                String queryOpt = queryLookupService.getQueryByName(request.getReportName());
                sqlQuery = queryOpt;
            } else {
                throw new BadRequestException("Either query, queryId or reportName is required");
            }

            SqlAnalysisResponse sqlAnalysis = sqlAnalysisService.analyzeSql(sqlQuery);

            // 2. Get execution plan
            ExplainAnalysisResponse explainResponse = explainAnalysisService.analyzeExplain(sqlQuery);

            // 3. Get schema for ONLY the tables in this query
            List<String> tableNamesList = sqlAnalysis.getDetectedTables();
            Set<String> tableNames = new HashSet<>(tableNamesList);
            Set<TableInfo> tableInfos = schemaService.getSchemaForTables(tableNames);
            SchemaInfo schemaMetadata = new SchemaInfo(tableInfos);

            // 4. Get table statistics
            Map<String, Object> tableStatistics = schemaService.getTableStatistics(tableNames);

            // 5. Select AI provider based on request type (analyse)
            AnalysisRequest providerSelectionRequest = AnalysisRequest.builder()
                    .requestType("analyse") // We're doing analysis
                    .build();
            AiAnalysisProvider selectedProvider = selectAiAnalysisProvider(providerSelectionRequest);

            AnalysisRequest aiRequest = AnalysisRequest.builder()
                    .sqlQuery(sqlQuery)
                    .sqlAnalysis(sqlAnalysis)
                    .explainAnalysis(new ExplainAnalysisResult(
                            explainResponse.isFullScanDetected(),
                            explainResponse.isTempTableDetected(),
                            explainResponse.isFileSortDetected(),
                            explainResponse.isNestedLoopDetected(),
                            explainResponse.isRowExplosionDetected(),
                            explainResponse.getIssues(),
                            explainResponse.getRecommendations()))
                    .schemaMetadata(schemaMetadata)
                    .tableStatistics(tableStatistics)
                    .build();

            AiAnalysisResult aiResult = selectedProvider != null ? selectedProvider.analyze(aiRequest) : null;

            // 7. Build response in requested format
            Map<String, Object> response = new LinkedHashMap<>();

            final String requestIdentifier = UUID.randomUUID().toString();

            // Format complexity score
            String complexityScore = aiResult != null && aiResult.getClaudeComplexityScore() != null
                    ? aiResult.getClaudeComplexityScore()
                    : calculateFallbackScore(sqlAnalysis);
            response.put("complexity_score", complexityScore);
            // sqlAnalysis.setComplexityScore(complexityScore);
            // Use AI comments or fallback
            String comments = aiResult != null && aiResult.getClaudeComments() != null
                    && !aiResult.getClaudeComments().isEmpty()
                            ? aiResult.getClaudeComments()
                            : generateFallbackComments(sqlAnalysis, explainResponse);

            response.put("comments", comments);

            List<IssueDto> issues = new ArrayList<>();
            if (aiResult != null && aiResult.getClaudeIssues()!=null) {
                for (ClaudeIssue issue : aiResult.getClaudeIssues()) {

                    IssueDto dto = new IssueDto();
                    dto.setIssue(issue.getIssue());
                    String severity = issue.getSeverityContribution();
                    if (severity == null || severity.isBlank()) {
                        dto.setSeverity(Severity.MEDIUM);
                    } else {
                        try {
                            dto.setSeverity(Severity.valueOf(severity.trim().toUpperCase()));
                        } catch (IllegalArgumentException ex) {
                            dto.setSeverity(Severity.MEDIUM);
                        }
                    }
                    issues.add(dto);
                }
            }

            sqlAnalysis.setIssues(issues);
            response.put("issues", issues);
            List<RecommendationDto> recommendations = new ArrayList<>();
            if (aiResult != null && aiResult.getRecommendations() != null) {

                for (ClaudeRecommendation rec : aiResult.getClaudeRecommendations()) {
                    RecommendationDto dto = new RecommendationDto();
                    dto.setMessage(rec.getRecommendation());
                    recommendations.add(dto);

                }
            }
            sqlAnalysis.setRecommendations(recommendations);

            response.put("recommendations", recommendations);
            response.put("request-identifier", requestIdentifier);

            AnalysisHistory analysisHistory = AnalysisHistory.builder()
                    .queryText(sqlQuery)
                    .requestIdentifier(requestIdentifier)
                    .complexityScore(complexityScore)
                    .analysisResult(getJsonString(response))
                    .tableStatisticsJson(getJsonString(tableStatistics))
                    .schemaMetadataJson(getJsonString(schemaMetadata))
                    .explainAnalysisJson(getJsonString(explainResponse))
                    .sqlAnalysisJson(getJsonString(sqlAnalysis))
                    .build();

            if (analysisHistory != null) {
                this.analysisHistoryRepository.save(analysisHistory);
            }
            ReportAnalysisResponse reportAnalysisResponse = ReportAnalysisResponse.builder()
                    .complexityScore(complexityScore)
                    .comments(comments)
                    .issues(issues)
                    .recommendations(recommendations)
                    .requestIdentifier(requestIdentifier).build();

            return reportAnalysisResponse;
        } catch (Exception e) {
            // Surface the real error instead of returning an empty response
            return ReportAnalysisResponse.builder()
                    .complexityScore("0/100")
                    .comments("Error during analysis: " + e.getMessage())
                    .issues(Collections.emptyList())
                    .recommendations(Collections.emptyList())
                    .build();
        }
    }

    private String calculateFallbackScore(SqlAnalysisResponse sqlAnalysis) {
        int score = sqlAnalysis.getComplexityScore();
        return score + "/100";
    }

    private String generateFallbackComments(SqlAnalysisResponse sqlAnalysis,
            ExplainAnalysisResponse explainAnalysis) {
        StringBuilder comments = new StringBuilder();
        comments.append("SQL query analysis completed. ");

        int score = sqlAnalysis.getComplexityScore();
        if (score >= 90) {
            comments.append(" The query appears to be well-optimized with minimal performance issues.");
        } else if (score >= 75) {
            comments.append(" The query has some minor performance issues that could be improved.");
        } else if (score >= 50) {
            comments.append(" The query has moderate performance issues that should be addressed.");
        } else {
            comments.append(" The query has significant performance issues that require immediate attention.");
        }

        if (sqlAnalysis.getIssues() != null && !sqlAnalysis.getIssues().isEmpty()) {
            comments.append(" Found ").append(sqlAnalysis.getIssues().size())
                    .append(" issue(s) from rule-based analysis.");
        }

        if (explainAnalysis.getIssues() != null && !explainAnalysis.getIssues().isEmpty()) {
            comments.append(" Found ").append(explainAnalysis.getIssues().size())
                    .append(" issue(s) from EXPLAIN analysis.");
        }

        return comments.toString();
    }

    /**
     * Converts an object to its JSON string representation using ObjectMapper.
     * Returns null if the input is null or if serialization fails.
     */
    private String getJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            // Log the error in a real application
            System.err.println("Failed to serialize object to JSON: " + e.getMessage());
            return null;
        }
    }
}