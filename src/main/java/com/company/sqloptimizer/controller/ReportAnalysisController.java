package com.company.sqloptimizer.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.coyote.BadRequestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.sqloptimizer.ai.AiAnalysisProvider;
import com.company.sqloptimizer.ai.AiAnalysisResult;
import com.company.sqloptimizer.ai.AnalysisRequest;
import com.company.sqloptimizer.ai.ClaudeIssue;
import com.company.sqloptimizer.ai.ClaudeRecommendation;
import com.company.sqloptimizer.analyzer.SchemaInfo;
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
import com.company.sqloptimizer.repository.AnalysisHistoryRepository;
import com.company.sqloptimizer.service.ExplainAnalysisService;
import com.company.sqloptimizer.service.QueryLookupService;
import com.company.sqloptimizer.service.SchemaService;
import com.company.sqloptimizer.service.SqlAnalysisService;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/report")
@RequiredArgsConstructor
public class ReportAnalysisController {

    private final SqlAnalysisService sqlAnalysisService;
    private final ExplainAnalysisService explainAnalysisService;
    private final SchemaService schemaService;
    private final List<AiAnalysisProvider> aiAnalysisProviders;
    private final QueryLookupService queryLookupService;
    private final AnalysisHistoryRepository analysisHistoryRepository;
    private final ObjectMapper objectMapper;

    @PostMapping("/analyze")
    public ReportAnalysisResponse analyzeReport(@RequestBody ReportAnalysisRequest request) {
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
                            explainResponse.getRecommendations()
                    ))
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
            String comments = aiResult != null && aiResult.getClaudeComments() != null && !aiResult.getClaudeComments().isEmpty()
                    ? aiResult.getClaudeComments()
                    : generateFallbackComments(sqlAnalysis, explainResponse);
            
            response.put("comments", comments);

            List<IssueDto> issues = new ArrayList<>();

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

            sqlAnalysis.setIssues(issues);
            response.put("issues", issues);

            List<RecommendationDto> recommendations = new ArrayList<>();
            for (ClaudeRecommendation rec : aiResult.getClaudeRecommendations()) {
                RecommendationDto dto = new RecommendationDto();
                dto.setMessage(rec.getRecommendation());
                recommendations.add(dto);

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
            // Return error details in response
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("complexity_score", "0/100");
            response.put("comments", "Error during analysis: " + e.getMessage());
            response.put("issues", Collections.emptyList());
            response.put("recommendations", Collections.emptyList());
            ReportAnalysisResponse reportAnalysisResponse = ReportAnalysisResponse.builder().build();
            return reportAnalysisResponse;
        }
    }

    private Severity mapSeverity(String severity) {
        if (severity == null || severity.isBlank()) {
            return Severity.MEDIUM;
        }

        try {
            return Severity.valueOf(severity.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Severity.MEDIUM;
        }
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
            comments.append(" Found ").append(sqlAnalysis.getIssues().size()).append(" issue(s) from rule-based analysis.");
        }

        if (explainAnalysis.getIssues() != null && !explainAnalysis.getIssues().isEmpty()) {
            comments.append(" Found ").append(explainAnalysis.getIssues().size()).append(" issue(s) from EXPLAIN analysis.");
        }

        return comments.toString();
    }

    private List<Map<String, Object>> convertToIssueMap(List<IssueDto> issues) {
        List<Map<String, Object>> issueMaps = new ArrayList<>();
        if (issues != null) {
            for (IssueDto issue : issues) {
                Map<String, Object> issueMap = new LinkedHashMap<>();
                issueMap.put("issue", issue.getIssue());
                Severity severity = issue.getSeverity();
                String severityContribution = "Medium";
                if (severity != null) {
                    String name = severity.name();
                    // Convert enum like "HIGH" to "High"
                    if (!name.isEmpty()) {
                        severityContribution = Character.toUpperCase(name.charAt(0))
                                + name.substring(1).toLowerCase();
                    }
                }
                issueMap.put("severity_contribution", severityContribution);
                issueMaps.add(issueMap);
            }
        }
        return issueMaps;
    }

    private List<Map<String, Object>> convertToRecommendationMap(List<RecommendationDto> recommendations) {
        List<Map<String, Object>> recMaps = new ArrayList<>();
        if (recommendations != null) {
            for (RecommendationDto rec : recommendations) {
                Map<String, Object> recMap = new LinkedHashMap<>();
                recMap.put("recommendation", rec.getMessage());
                recMaps.add(recMap);
            }
        }
        return recMaps;
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