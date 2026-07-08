package com.company.sqloptimizer.ai.impl;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.company.sqloptimizer.ai.AiAnalysisProvider;
import com.company.sqloptimizer.ai.AiAnalysisResult;
import com.company.sqloptimizer.ai.AnalysisRequest;
import com.company.sqloptimizer.ai.ClaudeAnalysisResult;
import com.company.sqloptimizer.ai.ClaudeIssue;
import com.company.sqloptimizer.ai.ClaudeRecommendation;
import com.company.sqloptimizer.ai.PromptBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ponytail: service for SQL analysis using Claude API.
 * Handles analysis prompt building, calling Claude, parsing SSE, parsing JSON
 * response.
 */
@Component
public class ClaudeAnalysisService implements AiAnalysisProvider {

    private final ClaudeClient claudeClient;
    private final PromptBuilder promptBuilder;
    private final ClaudeSseParser sseParser;
    private final AnalysisResponseParser responseParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeAnalysisService(ClaudeClient claudeClient,
            PromptBuilder promptBuilder,
            ClaudeSseParser sseParser,
            AnalysisResponseParser responseParser) {
        this.claudeClient = claudeClient;
        this.promptBuilder = promptBuilder;
        this.sseParser = sseParser;
        this.responseParser = responseParser;
    }

    @Override
    public AiAnalysisResult analyze(AnalysisRequest request) {
        try {
            // Build prompts
            String systemPrompt = promptBuilder.buildSystemPrompt();
            String userPrompt = promptBuilder.buildUserPrompt(
                    request.getSqlAnalysis(),
                    request.getExplainAnalysis(),
                    request.getSchemaMetadata(),
                    request.getTableStatistics(),
                    request.getSqlQuery());

            // Call Claude API
            String responseBody = claudeClient.sendRequest(systemPrompt, userPrompt);

            // Parse SSE response to get text
            String responseText = sseParser.parse(responseBody);

            // Try to deserialize directly to ClaudeAnalysisResponse
            ClaudeAnalysisResponse claudeResponse = null;
            try {
                claudeResponse = objectMapper.readValue(responseText, ClaudeAnalysisResponse.class);
            } catch (Exception e) {
                // Fallback to manual parsing
                Map<String, Object> parsedResponse = responseParser.parse(responseText);
                // Convert to ClaudeAnalysisResult using existing logic
                return convertFromParsedResponse(parsedResponse);
            }

            // If we have a deserialized response, convert it
            if (claudeResponse != null) {
                return convertFromClaudeResponse(claudeResponse);
            }

            // Fallback (should not reach here)
            return AiAnalysisResult.builder()
                    .executiveSummary("Error during AI analysis: Failed to parse response")
                    .rootCauses(Collections.singletonList("Failed to parse AI response"))
                    .recommendations(Collections.singletonList("Check AI response format"))
                    .confidence(0.0)
                    .rawResponse(responseBody)
                    .build();
        } catch (Exception e) {
            // Fallback error handling
            return AiAnalysisResult.builder()
                    .executiveSummary("Error during AI analysis: " + e.getMessage())
                    .rootCauses(Collections.singletonList("Failed to get AI analysis"))
                    .recommendations(Collections.singletonList("Check your AI API configuration and try again"))
                    .confidence(0.0)
                    .rawResponse("Error: " + e.getMessage())
                    .claudeComplexityScore("0/100")
                    .claudeComments("Error during AI analysis: " + e.getMessage())
                    .claudeIssues(Collections.emptyList())
                    .claudeRecommendations(Collections.emptyList())
                    .build();
        }
    }

    private AiAnalysisResult convertFromClaudeResponse(ClaudeAnalysisResponse response) {
        String complexityScore = response.getComplexityScore() != null ? response.getComplexityScore() : "0/100";
        String comments = response.getComments() != null ? response.getComments() : "Analysis completed via Claude API";
        List<ClaudeIssue> issues = response.getIssues() != null ? response.getIssues() : Collections.emptyList();
        List<ClaudeRecommendation> recommendations = response.getRecommendations() != null
                ? response.getRecommendations()
                : Collections.emptyList();

        // Convert to ClaudeAnalysisResult
        ClaudeAnalysisResult claudeResult = new ClaudeAnalysisResult(
                complexityScore,
                comments,
                issues,
                recommendations);

        // Format for backward compatibility
        String executiveSummary = formatExecutiveSummary(comments, complexityScore);
        List<String> rootCauses = formatRootCauses(issues);
        List<String> recommendationsList = formatRecommendations(recommendations);
        double confidence = extractConfidenceFromScore(complexityScore);

        return AiAnalysisResult.builder()
                .executiveSummary(executiveSummary)
                .rootCauses(rootCauses)
                .recommendations(recommendationsList)
                .confidence(confidence)
                .rawResponse("") // Note: rawResponse is not available here, but we can set it if needed
                // Claude-specific fields
                .claudeComplexityScore(claudeResult.getComplexityScore())
                .claudeComments(claudeResult.getComments())
                .claudeIssues(claudeResult.getIssues())
                .claudeRecommendations(claudeResult.getRecommendations())
                .build();
    }

    private AiAnalysisResult convertFromParsedResponse(Map<String, Object> parsedResponse) {
        String complexityScore = (String) parsedResponse.getOrDefault("complexity_score", "0/100");
        String comments = (String) parsedResponse.getOrDefault("comments", "Analysis completed via Claude API");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issuesList = (List<Map<String, Object>>) parsedResponse.getOrDefault("issues",
                Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendationMaps = (List<Map<String, Object>>) parsedResponse
                .getOrDefault("recommendations", Collections.emptyList());

        // Convert to Claude-specific objects
        ClaudeAnalysisResult claudeResult = new ClaudeAnalysisResult();
        claudeResult.setComplexityScore(complexityScore);
        claudeResult.setComments(comments);

        java.util.List<ClaudeIssue> issues = new java.util.ArrayList<>();
        for (Map<String, Object> issueMap : issuesList) {
            String issueDesc = (String) issueMap.getOrDefault("issue", "");
            String severityContribution = (String) issueMap.getOrDefault("severity_contribution", "");
            String recommendation = (String) issueMap.getOrDefault("recommendation", "");
            String recommendedQuery = (String) issueMap.getOrDefault("recommended_query", "");
            ClaudeIssue issue = new ClaudeIssue(issueDesc, severityContribution, recommendation, recommendedQuery);
            issues.add(issue);
        }
        claudeResult.setIssues(issues);

        java.util.List<ClaudeRecommendation> recommendations = new java.util.ArrayList<>();
        for (Map<String, Object> recMap : recommendationMaps) {
            String recommendationText = (String) recMap.getOrDefault("recommendation", "");
            String recommendedQuery = (String) recMap.getOrDefault("recommended_query", "");
            ClaudeRecommendation recommendation = new ClaudeRecommendation(recommendationText, recommendedQuery);
            recommendations.add(recommendation);
        }
        claudeResult.setRecommendations(recommendations);

        // For rewrite requests, if we have a top-level recommended_query but no
        // recommendations,
        // create a default recommendation with that query
        if (recommendations.isEmpty() && parsedResponse.containsKey("recommended_query")) {
            String recommendedQuery = (String) parsedResponse.get("recommended_query");
            if (recommendedQuery != null && !recommendedQuery.isEmpty() && !isPlaceholderText(recommendedQuery)) {
                claudeResult.getRecommendations().add(new ClaudeRecommendation("Rewrite successful", recommendedQuery));
            }
        }

        // Convert to AiAnalysisResult for backward compatibility
        String executiveSummary = formatExecutiveSummary(comments, complexityScore);
        List<String> rootCauses = formatRootCauses(claudeResult.getIssues());
        List<String> recommendationsList = formatRecommendations(claudeResult.getRecommendations());
        double confidence = extractConfidenceFromScore(complexityScore);

        return AiAnalysisResult.builder()
                .executiveSummary(executiveSummary)
                .rootCauses(rootCauses)
                .recommendations(recommendationsList)
                .confidence(confidence)
                .rawResponse("") // Note: rawResponse is not available here, but we can set it if needed
                // Claude-specific fields
                .claudeComplexityScore(claudeResult.getComplexityScore())
                .claudeComments(claudeResult.getComments())
                .claudeIssues(claudeResult.getIssues())
                .claudeRecommendations(claudeResult.getRecommendations())
                .build();
    }

    private String formatExecutiveSummary(String comments, String complexityScore) {
        StringBuilder sb = new StringBuilder();
        if (comments != null && !comments.isEmpty()) {
            sb.append(comments);
        }
        if (complexityScore != null && !complexityScore.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("(Complexity Score: ").append(complexityScore).append(")");
        }
        return sb.toString().trim();
    }

    private java.util.List<String> formatRootCauses(java.util.List<ClaudeIssue> issuesList) {
        if (issuesList == null || issuesList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return issuesList.stream()
                .map(issue -> {
                    String issueDesc = issue.getIssue();
                    String severity = issue.getSeverityContribution();
                    String recommendation = issue.getRecommendation();
                    String recommendedQuery = issue.getRecommendedQuery();

                    StringBuilder sb = new StringBuilder();
                    if (issueDesc != null && !issueDesc.isEmpty()) {
                        sb.append(issueDesc);
                    }
                    if (severity != null && !severity.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Severity Contribution: ").append(severity);
                    }
                    if (recommendation != null && !recommendation.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Recommendation: ").append(recommendation);
                    }
                    if (recommendedQuery != null && !recommendedQuery.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Suggested Query: ").append(recommendedQuery);
                    }
                    return sb.toString();
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private java.util.List<String> formatRecommendations(java.util.List<ClaudeRecommendation> recommendationsList) {
        if (recommendationsList == null || recommendationsList.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return recommendationsList.stream()
                .map(rec -> {
                    String recommendation = rec.getRecommendation();

                    StringBuilder sb = new StringBuilder();
                    if (recommendation != null && !recommendation.isEmpty()) {
                        sb.append(recommendation);
                    }
                    return sb.toString();
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private double extractConfidenceFromScore(String complexityScore) {
        try {
            if (complexityScore == null || complexityScore.isEmpty()) {
                return 0.5;
            }

            if (complexityScore.contains("/")) {
                // Format is "X/Y" - extract X and compute X/Y
                String[] parts = complexityScore.split("/");
                if (parts.length >= 2) {
                    double numerator = Double.parseDouble(parts[0].trim());
                    double denominator = Double.parseDouble(parts[1].trim());
                    if (denominator != 0) {
                        double ratio = numerator / denominator;
                        return Math.min(Math.max(ratio, 0.0), 1.0);
                    }
                }
                // If parsing fails, fall through to default handling below
            }

            // No "/" found - treat as raw value
            double score = Double.parseDouble(complexityScore.trim());
            if (score >= 0 && score <= 1) {
                // Already in 0-1 range
                return score;
            } else if (score > 1 && score <= 100) {
                // Treat as percentage
                return score / 100.0;
            } else {
                // Out of expected range, clamp to 0-1
                return Math.min(Math.max(score, 0.0), 1.0);
            }
        } catch (NumberFormatException e) {
            // If parsing fails, return default
            return 0.5;
        }
    }

    private boolean isPlaceholderText(String text) {
        if (text == null || text.isEmpty()) {
            return true;
        }

        String trimmed = text.trim();

        // Check for common placeholder patterns
        if (trimmed.equals("...") ||
                trimmed.equals("..") ||
                trimmed.equals("....") ||
                trimmed.equalsIgnoreCase("etc") ||
                trimmed.equalsIgnoreCase("et cetera") ||
                trimmed.equalsIgnoreCase("and so on") ||
                trimmed.equalsIgnoreCase("blah blah") ||
                trimmed.equalsIgnoreCase("yada yada") ||
                trimmed.equalsIgnoreCase("lorem ipsum") ||
                trimmed.matches("\\.{3,}")) { // Three or more dots
            return true;
        }

        // Check for phrases indicating incomplete content
        String lower = trimmed.toLowerCase();
        if (lower.contains("...") &&
                (lower.contains("above") ||
                        lower.contains("below") ||
                        lower.contains("here") ||
                        lower.contains("query") ||
                        lower.contains("code") ||
                        lower.contains("text") ||
                        lower.contains("content") ||
                        lower.contains("details") ||
                        lower.contains("information") ||
                        lower.contains("rest"))) {
            return true;
        }

        // Check for very short content that's likely not a real query
        if (trimmed.length() < 10) {
            return true;
        }

        return false;
    }
}