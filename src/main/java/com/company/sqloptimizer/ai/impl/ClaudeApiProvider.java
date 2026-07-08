package com.company.sqloptimizer.ai.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.company.sqloptimizer.ai.AiAnalysisProvider;
import com.company.sqloptimizer.ai.AiAnalysisResult;
import com.company.sqloptimizer.ai.AnalysisRequest;
import com.company.sqloptimizer.ai.ClaudeAnalysisResult;
import com.company.sqloptimizer.ai.ClaudeIssue;
import com.company.sqloptimizer.ai.ClaudeRecommendation;
import com.company.sqloptimizer.ai.PromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI provider that uses Anthropic's Claude API via REST (compatible with custom
 * base URL and auth token).
 * Configured via environment variables for use with local proxies/gateways.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Configuration
public class ClaudeApiProvider implements AiAnalysisProvider {

    private final PromptBuilder promptBuilder;
    private final RestTemplate restTemplate;
    // private final ClaudeSdkClient claudeSdkClient;

    @Value("${anthropic.base.url}")
    private String baseUrl;

    @Value("${anthropic.auth.token}")
    private String authToken;

    @Value("${ai.model.list:}")
    private String modelListString;

    @Value("${ai.model.max-attempts:3}")
    private int maxAttemptsPerModel;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public AiAnalysisResult analyze(AnalysisRequest request) {
        AnthropicClient client = AnthropicOkHttpClient.fromEnv();

        List<String> modelList = Arrays.stream(modelListString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        // Default fallback if list empty
        if (modelList.isEmpty()) {
            modelList = List.of("claude-3-5-sonnet-20241022");
        }

        Exception lastException = null;
        for (String model : modelList) {
            for (int attempt = 1; attempt <= maxAttemptsPerModel; attempt++) {
                try {
                    if (request.getRequestType().equalsIgnoreCase("rewrite")) {
                        return attemptRewrite(request, model);
                    } else {
                        return attemptAnalysis(request, model);
                    }
                } catch (Exception e) {
                    lastException = e;
                    log.warn("Attempt {}/{} failed for model '{}': {}", attempt, maxAttemptsPerModel, model,
                            e.getMessage());
                    if (attempt < maxAttemptsPerModel) {
                        // optional: small delay before retry
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            // if we exhausted attempts for this model, continue to next model
            log.warn("All attempts exhausted for model '{}'", model);
        }
        // All models and attempts failed
        log.error("=== ALL MODEL ATTEMPTS FAILED ===", lastException);
        return AiAnalysisResult.builder()
                .executiveSummary("Error during AI analysis: All models failed after retries. Last error: "
                        + (lastException != null ? lastException.getMessage() : "unknown"))
                .rootCauses(Collections.singletonList("Failed to get AI analysis from all configured models"))
                .recommendations(Collections
                        .singletonList("Check your AI API configuration, network connection, and model availability"))
                .confidence(0.0)
                .rawResponse("Error: All models failed after retries")
                // Claude-specific fields for error case (keep structure)
                .claudeComplexityScore("0/100")
                .claudeComments("Error during AI analysis: All models failed after retries")
                .claudeIssues(Collections.emptyList())
                .claudeRecommendations(Collections.emptyList())
                .build();
    }

    private AiAnalysisResult attemptAnalysis(AnalysisRequest request, String model) throws Exception {
        String systemPrompt = promptBuilder.buildSystemPrompt();
        String userPrompt = promptBuilder.buildUserPrompt(
                request.getSqlAnalysis(),
                request.getExplainAnalysis(),
                request.getSchemaMetadata(),
                request.getTableStatistics(),
                request.getSqlQuery());

        // Prepare request to Claude API
        String url = baseUrl.endsWith("/") ? baseUrl + "v1/messages" : baseUrl + "/v1/messages";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 4000);
        requestBody.put("temperature", 0.0);
        requestBody.put("top_p", 0.9);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Anthropic API uses x-api-key header
        headers.set("x-api-key", authToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // LOG: Show the request being sent
        log.info("=== SENDING REQUEST TO CLAUDE API (model={}) ===", model);
        log.info("URL: {}", url);
        log.info("Headers: {}", headers);
        log.info("Request Body: {}", requestBody);
        log.info("=== END REQUEST ===");

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class);

        String responseBody = responseEntity.getBody();

        if (responseBody == null) {
            throw new RuntimeException("Empty response from Claude API");
        }

        // LOG: Show the raw response
        log.info("=== RECEIVED RESPONSE FROM CLAUDE API (model={}) ===", model);
        log.info("Response Body: {}", responseBody);
        log.info("=== END RESPONSE ===");

        // Parse JSON response to extract text (handle both JSON and SSE formats)
        String responseText = extractTextFromClaudeResponse(responseBody);

        // LOG: Show the extracted text
        log.info("=== EXTRACTED TEXT FROM CLAUDE RESPONSE (model={}) ===", model);
        log.info("Response Text: {}", responseText);
        log.info("=== END EXTRACTED TEXT ===");

        // Check if the response indicates an error from the upstream provider
        if (isProviderErrorResponse(responseText)) {
            // Return error result instead of trying to parse as JSON
            return handleProviderError(responseText, model);
        }

        // Parse the JSON response according to the requested format
        Map<String, Object> parsedResponse = parseClaudeJsonResponse(responseText);

        // Extract values from parsed response
        String complexityScore = (String) parsedResponse.getOrDefault("complexity_score", "0/100");
        String comments = (String) parsedResponse.getOrDefault("comments", "Analysis completed via Claude API");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> issuesList = (List<Map<String, Object>>) parsedResponse.getOrDefault("issues",
                Collections.emptyList());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recommendationsList = (List<Map<String, Object>>) parsedResponse
                .getOrDefault("recommendations", Collections.emptyList());

        // Convert complexity score to confidence (extract X from X/100)
        double confidence = extractConfidenceFromScore(complexityScore);

        // Format the response for backward compatibility with existing code
        String executiveSummary = formatExecutiveSummary(comments, complexityScore);
        List<String> rootCauses = formatRootCauses(issuesList);
        List<String> recommendations = formatRecommendations(recommendationsList);

        // Convert to Claude-specific objects
        ClaudeAnalysisResult claudeResult = convertToClaudeResult(parsedResponse);

        // Fallback if parsing failed
        if ((executiveSummary == null || executiveSummary.isEmpty()) &&
                (rootCauses == null || rootCauses.isEmpty()) &&
                (recommendations == null || recommendations.isEmpty())) {
            executiveSummary = "AI analysis completed via Claude API";
            confidence = 0.8;
        }

        return AiAnalysisResult.builder()
                .executiveSummary(executiveSummary)
                .rootCauses(rootCauses)
                .recommendations(recommendations)
                .confidence(confidence)
                .rawResponse(responseText)
                // Claude-specific fields
                .claudeComplexityScore(claudeResult.getComplexityScore())
                .claudeComments(claudeResult.getComments())
                .claudeIssues(claudeResult.getIssues())
                .claudeRecommendations(claudeResult.getRecommendations())
                .build();
    }

    /**
     * Extracts the text content from Claude's response.
     * Handles both regular JSON response and Server-Sent Events (SSE) format.
     *
     * For SSE format, looks for content_block_delta events with text or thinking
     * deltas.
     */
    private String extractTextFromClaudeResponse(String response) {
        try {
            // Check if this is SSE format (starts with "event:" or contains "data:")
            if (response.contains("event:") && response.contains("data:")) {
                return extractTextFromSseResponse(response);
            }

            // Otherwise, treat as regular JSON
            JsonNode rootNode = objectMapper.readTree(response);
            JsonNode contentNode = rootNode.path("content");
            if (contentNode.isArray() && contentNode.size() > 0) {
                // Iterate through content blocks to find the first text block
                for (JsonNode block : contentNode) {
                    if ("text".equals(block.path("type").asText())) {
                        return block.path("text").asText();
                    }
                }
            }
            // Fallback: return raw JSON if structure unexpected
            return response;
        } catch (Exception e) {
            // If parsing fails, try SSE extraction as fallback
            if (response.contains("event:") && response.contains("data:")) {
                try {
                    return extractTextFromSseResponse(response);
                } catch (Exception sseEx) {
                    log.warn("Failed to extract text from SSE response: {}", sseEx.getMessage());
                }
            }
            // If all else fails, return raw string
            return response;
        }
    }

    /**
     * Parses the Claude response as JSON according to the requested format.
     * Tries multiple possible field names for robustness.
     */
    private Map<String, Object> parseClaudeJsonResponse(String responseText) {
        try {
            // Extract JSON from the response text (handle cases where there's extra text
            // before/after)
            String jsonText = extractJsonFromText(responseText);

            // Parse JSON
            JsonNode jsonNode = objectMapper.readTree(jsonText);
            Map<String, Object> result = new HashMap<>();

            // Helper to get string value with fallback
            java.util.function.Function<JsonNode, String> getString = node -> node == null || node.isNull()
                    || node.isMissingNode() ? "" : node.asText();

            // Extract complexity_score - try multiple possible field names
            String complexityScore = "";
            if (jsonNode.has("complexity_score")) {
                complexityScore = getString.apply(jsonNode.get("complexity_score"));
            } else if (jsonNode.has("complexityScore")) {
                complexityScore = getString.apply(jsonNode.get("complexityScore"));
            } else if (jsonNode.has("score")) {
                complexityScore = getString.apply(jsonNode.get("score"));
            }
            // Ensure complexity score is in "X/100" format
            if (!complexityScore.isEmpty()) {
                // If it's already in "X/Y" format, use as-is
                if (complexityScore.contains("/")) {
                    result.put("complexity_score", complexityScore);
                } else {
                    // Otherwise, treat as a numeric score and convert to percentage
                    try {
                        double score = Double.parseDouble(complexityScore);
                        // If score is already 0-1, multiply by 100
                        if (score >= 0 && score <= 1) {
                            score = score * 100;
                        }
                        // Ensure it's in reasonable range
                        score = Math.max(0, Math.min(100, score));
                        result.put("complexity_score", String.format("%.0f/100", score));
                    } catch (NumberFormatException e) {
                        // If parsing fails, use as-is (should be rare)
                        result.put("complexity_score", complexityScore);
                    }
                }
            }

            // Extract comments - try multiple possible field names
            String comments = "";
            if (jsonNode.has("comments")) {
                comments = getString.apply(jsonNode.get("comments"));
            } else if (jsonNode.has("comment")) {
                comments = getString.apply(jsonNode.get("comment"));
            } else if (jsonNode.has("explanation")) {
                comments = getString.apply(jsonNode.get("explanation"));
            } else if (jsonNode.has("analysis")) {
                comments = getString.apply(jsonNode.get("analysis"));
            } else if (jsonNode.has("summary")) {
                comments = getString.apply(jsonNode.get("summary"));
            }
            if (!comments.isEmpty()) {
                result.put("comments", comments);
            }

            // Extract issues - try multiple possible field names
            List<Map<String, Object>> issuesList = new ArrayList<>();
            if (jsonNode.has("issues") && jsonNode.get("issues").isArray()) {
                JsonNode issuesNode = jsonNode.get("issues");
                for (JsonNode issueNode : issuesNode) {
                    Map<String, Object> issueMap = new HashMap<>();
                    // issue
                    String issueVal = "";
                    if (issueNode.has("issue")) {
                        issueVal = getString.apply(issueNode.get("issue"));
                    } else if (issueNode.has("description")) {
                        issueVal = getString.apply(issueNode.get("description"));
                    }
                    if (!issueVal.isEmpty()) {
                        issueMap.put("issue", issueVal);
                    }
                    // severity_contribution
                    String severityVal = "";
                    if (issueNode.has("severity_contribution")) {
                        severityVal = getString.apply(issueNode.get("severity_contribution"));
                    } else if (issueNode.has("severity")) {
                        severityVal = getString.apply(issueNode.get("severity"));
                    } else if (issueNode.has("severityLevel")) {
                        severityVal = getString.apply(issueNode.get("severityLevel"));
                    }
                    if (!severityVal.isEmpty()) {
                        issueMap.put("severity_contribution", severityVal);
                    }
                    // recommendation
                    String recVal = "";
                    if (issueNode.has("recommendation")) {
                        recVal = getString.apply(issueNode.get("recommendation"));
                    } else if (issueNode.has("advice")) {
                        recVal = getString.apply(issueNode.get("advice"));
                    }
                    if (!recVal.isEmpty()) {
                        issueMap.put("recommendation", recVal);
                    }
                    // recommended_query
                    String queryVal = "";
                    if (issueNode.has("recommended_query")) {
                        queryVal = getString.apply(issueNode.get("recommended_query"));
                    } else if (issueNode.has("recommendedQuery")) {
                        queryVal = getString.apply(issueNode.get("recommendedQuery"));
                    } else if (issueNode.has("suggestedQuery")) {
                        queryVal = getString.apply(issueNode.get("suggestedQuery"));
                    } else if (issueNode.has("recommended_query_sql")) {
                        queryVal = getString.apply(issueNode.get("recommended_query_sql"));
                    }
                    if (!queryVal.isEmpty()) {
                        issueMap.put("recommended_query", queryVal);
                    }
                    issuesList.add(issueMap);
                }
            }
            if (!issuesList.isEmpty()) {
                result.put("issues", issuesList);
            }

            // Extract recommendations - try multiple possible field names
            List<Map<String, Object>> recommendationsList = new ArrayList<>();
            if (jsonNode.has("recommendations") && jsonNode.get("recommendations").isArray()) {
                JsonNode recommendationsNode = jsonNode.get("recommendations");
                for (JsonNode recNode : recommendationsNode) {
                    Map<String, Object> recMap = new HashMap<>();
                    // recommendation
                    String recVal = "";
                    if (recNode.has("recommendation")) {
                        recVal = getString.apply(recNode.get("recommendation"));
                    } else if (recNode.has("advice")) {
                        recVal = getString.apply(recNode.get("advice"));
                    } else if (recNode.has("suggestion")) {
                        recVal = getString.apply(recNode.get("suggestion"));
                    }
                    if (!recVal.isEmpty()) {
                        recMap.put("recommendation", recVal);
                    }
                    // recommended_query
                    String queryVal = "";
                    if (recNode.has("recommended_query")) {
                        queryVal = getString.apply(recNode.get("recommended_query"));
                    } else if (recNode.has("recommendedQuery")) {
                        queryVal = getString.apply(recNode.get("recommendedQuery"));
                    } else if (recNode.has("suggestedQuery")) {
                        queryVal = getString.apply(recNode.get("suggestedQuery"));
                    } else if (recNode.has("recommended_query_sql")) {
                        queryVal = getString.apply(recNode.get("recommended_query_sql"));
                    }
                    if (!queryVal.isEmpty()) {
                        recMap.put("recommended_query", queryVal);
                    }
                    recommendationsList.add(recMap);
                }
            }
            if (!recommendationsList.isEmpty()) {
                result.put("recommendations", recommendationsList);
            }

            return result;
        } catch (Exception e) {
            log.warn("Failed to parse JSON response from Claude: {}", e.getMessage());
            // Return empty map if parsing fails
            return new HashMap<>();
        }
    }

    /**
     * Converts parsed JSON response to ClaudeAnalysisResult object.
     */
    private ClaudeAnalysisResult convertToClaudeResult(Map<String, Object> parsedResponse) {
        String complexityScore = (String) parsedResponse.getOrDefault("complexity_score", "0/100");
        String comments = (String) parsedResponse.getOrDefault("comments", "");

        List<ClaudeIssue> issues = new ArrayList<>();
        List<Map<String, Object>> issuesList = (List<Map<String, Object>>) parsedResponse.getOrDefault("issues",
                Collections.emptyList());
        for (Map<String, Object> issueMap : issuesList) {
            String issueDesc = (String) issueMap.getOrDefault("issue", "");
            String severityContribution = (String) issueMap.getOrDefault("severity_contribution", "");
            String recommendation = (String) issueMap.getOrDefault("recommendation", "");
            String recommendedQuery = (String) issueMap.getOrDefault("recommended_query", "");
            if (isPlaceholderText(recommendedQuery)) {
                recommendedQuery = "";
            }
            ClaudeIssue issue = new ClaudeIssue(issueDesc, severityContribution, recommendation, recommendedQuery);
            issues.add(issue);
        }

        List<ClaudeRecommendation> recommendations = new ArrayList<>();
        List<Map<String, Object>> recommendationsList = (List<Map<String, Object>>) parsedResponse
                .getOrDefault("recommendations", Collections.emptyList());
        for (Map<String, Object> recMap : recommendationsList) {
            String recommendationText = (String) recMap.getOrDefault("recommendation", "");
            String recommendedQuery = (String) recMap.getOrDefault("recommended_query", "");
            if (isPlaceholderText(recommendedQuery)) {
                recommendedQuery = "";
            }
            ClaudeRecommendation recommendation = new ClaudeRecommendation(recommendationText, recommendedQuery);
            recommendations.add(recommendation);
        }

        // For rewrite requests, if we have a top-level recommended_query but no
        // recommendations,
        // create a default recommendation with that query
        if (recommendations.isEmpty() && parsedResponse.containsKey("recommended_query")) {
            String recommendedQuery = (String) parsedResponse.get("recommended_query");
            if (recommendedQuery != null && !isPlaceholderText(recommendedQuery) && !recommendedQuery.isEmpty()) {
                recommendations.add(new ClaudeRecommendation("Rewrite successful", recommendedQuery));
            }
        }

        return new ClaudeAnalysisResult(complexityScore, comments, issues, recommendations);
    }

    /**
     * Extracts confidence score from complexity_score string.
     * Handles formats like: "85/100", "0.85", "85" (treated as percentage)
     */
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
            log.warn("Could not parse complexity score '{}': {}", complexityScore, e.getMessage());
        }
        return 0.5;
    }

    /**
     * Formats executive summary from comments and complexity score.
     */
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

    /**
     * Formats root causes from issues list for backward compatibility.
     */
    private List<String> formatRootCauses(List<Map<String, Object>> issuesList) {
        if (issuesList == null || issuesList.isEmpty()) {
            return Collections.emptyList();
        }
        return issuesList.stream()
                .map(issue -> {
                    String issueDesc = (String) issue.getOrDefault("issue", "");
                    String severity = (String) issue.getOrDefault("severity_contribution", "");
                    String recommendation = (String) issue.getOrDefault("recommendation", "");
                    String recommendedQuery = (String) issue.getOrDefault("recommended_query", "");

                    StringBuilder sb = new StringBuilder();
                    if (!issueDesc.isEmpty()) {
                        sb.append(issueDesc);
                    }
                    if (!severity.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Severity Contribution: ").append(severity);
                    }
                    if (!recommendation.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Recommendation: ").append(recommendation);
                    }
                    if (!recommendedQuery.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Suggested Query: ").append(recommendedQuery);
                    }
                    return sb.toString();
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * Formats recommendations list for backward compatibility.
     */
    private List<String> formatRecommendations(List<Map<String, Object>> recommendationsList) {
        if (recommendationsList == null || recommendationsList.isEmpty()) {
            return Collections.emptyList();
        }
        return recommendationsList.stream()
                .map(rec -> {
                    String recommendation = (String) rec.getOrDefault("recommendation", "");
                    String recommendedQuery = (String) rec.getOrDefault("recommended_query", "");

                    StringBuilder sb = new StringBuilder();
                    if (!recommendation.isEmpty()) {
                        sb.append(recommendation);
                    }
                    if (!recommendedQuery.isEmpty()) {
                        if (sb.length() > 0)
                            sb.append(" - ");
                        sb.append("Suggested Query: ").append(recommendedQuery);
                    }
                    return sb.toString();
                })
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // Helper methods (same as in Ollama provider) - kept for compatibility
    private String extractSection(String text, String startPattern, String endPattern) {
        if (text == null || startPattern == null || endPattern == null) {
            return "";
        }
        int startIndex = text.indexOf(startPattern);
        if (startIndex == -1)
            return "";
        startIndex += startPattern.length();
        int endIndex = text.indexOf(endPattern, startIndex);
        if (endIndex == -1) {
            endIndex = text.length();
        }
        return text.substring(startIndex, endIndex).trim();
    }

    private List<String> extractListSection(String text, String startPattern, String endPattern) {
        String section = extractSection(text, startPattern, endPattern);
        if (section == null || section.isEmpty()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(section.split("\\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty() &&
                        (line.matches("^[\\d\\.]+\\s+.+") || // Numbered list: 1. Item, 2. Item
                                line.matches("^[-*]\\s+.+") || // Bullet list: - Item, * Item
                                line.matches("^[•▪▫‣⁃]\\s+.+") || // Other bullet characters
                                line.matches("^[a-zA-Z][.)]\\s+.+"))) // Lettered list: a. Item, b) Item
                .map(line -> line.replaceAll("^[\\d\\.]+\\s+", "") // Remove numbering
                        .replaceAll("^[-*]\\s+", "") // Remove bullets
                        .replaceAll("^[•▪▫‣⁃]\\s+", "") // Remove other bullets
                        .replaceAll("^[a-zA-Z][.)]\\s+", "")) // Remove lettered markers
                .collect(Collectors.toList());
    }

    /**
     * Checks if the given text appears to be a placeholder value rather than actual
     * content.
     * Common placeholders include "...", "etc.", "see above", etc.
     *
     * @param text the text to check
     * @return true if the text appears to be a placeholder, false otherwise
     */
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

    /**
     * Checks if the response indicates an error from the upstream provider (like
     * NIM).
     * 
     * @param responseText The extracted text from the Claude API response
     * @return true if this appears to be a provider error response, false otherwise
     */
    private boolean isProviderErrorResponse(String responseText) {
        if (responseText == null || responseText.isEmpty()) {
            return false;
        }

        // Check for common error indicators in the response text
        String lower = responseText.toLowerCase();
        return lower.contains("upstream provider") ||
                lower.contains("provider api request failed") ||
                lower.contains("resourceexhausted") ||
                lower.contains("worker local total request limit reached") ||
                lower.contains("internal_server_error");
    }

    /**
     * Handles provider error responses by returning an appropriate AI analysis
     * result.
     * 
     * @param responseText The error response text from the provider
     * @param model        The model that was being used
     * @return An AiAnalysisResult indicating the provider error
     */
    private AiAnalysisResult handleProviderError(String responseText, String model) {
        // Extract meaningful error message from the response
        String errorMessage = extractErrorMessage(responseText);

        log.error("Provider error from model {}: {}", model, errorMessage);

        return AiAnalysisResult.builder()
                .executiveSummary("AI analysis failed due to provider error: " + errorMessage)
                .rootCauses(List.of("Upstream provider (NIM) returned an error"))
                .recommendations(List.of("Please try again later or check your AI provider configuration"))
                .confidence(0.0)
                .rawResponse(responseText)
                // Claude-specific fields for error case
                .claudeComplexityScore("0/100")
                .claudeComments("AI analysis failed due to provider error: " + errorMessage)
                .claudeIssues(Collections.emptyList())
                .claudeRecommendations(Collections.emptyList())
                .build();
    }

    /**
     * Extracts a concise error message from the provider error response.
     * 
     * @param responseText The full error response text
     * @return A cleaned error message suitable for display
     */
    private String extractErrorMessage(String responseText) {
        if (responseText == null || responseText.isEmpty()) {
            return "Unknown provider error";
        }

        // Try to extract the core error message
        String[] lines = responseText.split("\\r?\\n");
        StringBuilder errorMsg = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("Mapped message:") ||
                    trimmed.contains("Worker local total request limit reached") ||
                    trimmed.contains("ResourceExhausted") ||
                    trimmed.contains("internal_server_error")) {
                // Clean up the line
                String cleanLine = trimmed.replaceAll(".*Mapped message:\\s*", "")
                        .replaceAll(".*Worker local total request limit reached.*",
                                "Worker local total request limit reached")
                        .replaceAll(".*ResourceExceeded:.*", "Resource exhausted")
                        .replaceAll(".*internal_server_error.*", "Internal server error");
                if (!cleanLine.isEmpty() && !cleanLine.equals(trimmed)) {
                    errorMsg.append(cleanLine).append(" ");
                } else if (!trimmed.isEmpty()) {
                    errorMsg.append(trimmed).append(" ");
                }
            }
        }

        // If we didn't find specific patterns, return first meaningful line
        if (errorMsg.length() == 0) {
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() &&
                        !trimmed.startsWith("{") &&
                        !trimmed.startsWith("Request ID:") &&
                        !trimmed.equals("Upstream provider NIM returned an error.") &&
                        !trimmed.equals("Category: internal_server_error")) {
                    errorMsg.append(trimmed).append(" ");
                    break;
                }
            }
        }

        // Final fallback
        if (errorMsg.length() == 0) {
            errorMsg.append("Provider API request failed");
        }

        return errorMsg.toString().trim();
    }

    private AiAnalysisResult attemptRewrite(AnalysisRequest request, String model) throws Exception {
        // Build the prompt (same as Ollama provider)
        String systemPrompt = promptBuilder.buildSystemPromptForRewrite();
        String userPrompt = promptBuilder.buildUserPromptForRewrite(
                request.getSqlAnalysis(),
                request.getExplainAnalysis(),
                request.getSchemaMetadata(),
                request.getTableStatistics(),
                request.getSqlQuery());

        // Prepare request to Claude API
        String url = baseUrl.endsWith("/") ? baseUrl + "v1/messages" : baseUrl + "/v1/messages";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 12000);
        requestBody.put("temperature", 0.0);
        requestBody.put("top_p", 0.9);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Anthropic API uses x-api-key header
        headers.set("x-api-key", authToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        // LOG: Show the request being sent
        log.info("=== SENDING REQUEST TO CLAUDE API (model={}) ===", model);
        log.info("URL: {}", url);
        log.info("Headers: {}", headers);
        log.info("Request Body: {}", requestBody);
        log.info("=== END REQUEST ===");

        ResponseEntity<String> responseEntity = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class);

        String responseBody = responseEntity.getBody();

        if (responseBody == null) {
            throw new RuntimeException("Empty response from Claude API");
        }

        // LOG: Show the raw response
        log.info("=== RECEIVED RESPONSE FROM CLAUDE API (model={}) ===", model);
        log.info("Response Body: {}", responseBody);
        log.info("=== END RESPONSE ===");

        // Parse JSON response to extract text (handle both JSON and SSE formats)
        String responseText = extractTextFromSseResponseForRewrite(responseBody);

        // LOG: Show the extracted text
        log.info("=== EXTRACTED TEXT FROM CLAUDE RESPONSE (model={}) ===", model);
        log.info("Response Text: {}", responseText);
        log.info("=== END EXTRACTED TEXT ===");

        // responseText should be made a valid JSon
        // String responseTextJson = validateJson(responseText);

        // Check if the response indicates an error from the upstream provider
        if (isProviderErrorResponse(responseText)) {
            // Return error result instead of trying to parse as JSON
            return handleProviderError(responseText, model);
        }
        // Parse the JSON response according to the requested format
        String recommendedQuery = parseRewriteResponse(responseText);
        return AiAnalysisResult.builder()
                .recommendedQuery(recommendedQuery)
                .rawResponse(responseText)
                .build();
    }

    private String extractTextFromSseResponseForRewrite(String response) {

        StringBuilder answer = new StringBuilder();

        String currentEvent = null;
        StringBuilder currentData = new StringBuilder();

        String[] lines = response.split("\\R");

        for (String line : lines) {

            if (line.startsWith("event:")) {

                if (currentEvent != null && currentData.length() > 0) {
                    answer.append(processSseEventForRewrite(currentEvent, currentData.toString()));
                }

                currentEvent = line.substring(6).trim();
                currentData.setLength(0);
            }

            else if (line.startsWith("data:")) {

                if (currentData.length() > 0) {
                    currentData.append("\n");
                }

                currentData.append(line.substring(5).trim());
            }

            else if (line.isBlank()) {

                if (currentEvent != null && currentData.length() > 0) {
                    answer.append(processSseEventForRewrite(currentEvent, currentData.toString()));
                }

                currentEvent = null;
                currentData.setLength(0);
            }
        }

        if (currentEvent != null && currentData.length() > 0) {
            answer.append(processSseEventForRewrite(currentEvent, currentData.toString()));
        }

        return answer.toString().trim();
    }

    private String extractJsonFromText(String text) {

        if (text == null || text.isBlank()) {
            return "{}";
        }

        String cleaned = text.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7).trim();
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
        }

        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception ignored) {
        }

        String json = findLastValidJsonObject(cleaned);

        if (json != null) {
            return json;
        }

        return "{}";
    }

    private String findLastValidJsonObject(String text) {

        int end = text.lastIndexOf('}');

        while (end >= 0) {

            int braces = 1;

            for (int start = end - 1; start >= 0; start--) {

                char c = text.charAt(start);

                if (c == '}') {
                    braces++;
                }

                else if (c == '{') {
                    braces--;

                    if (braces == 0) {

                        String candidate = text.substring(start, end + 1);

                        try {

                            objectMapper.readTree(candidate);

                            return candidate;

                        } catch (Exception ignored) {
                        }

                        break;
                    }
                }
            }

            end = text.lastIndexOf('}', end - 1);
        }

        return null;
    }

    private String extractTextFromSseResponse(String response) {

        StringBuilder answer = new StringBuilder();

        String currentEvent = null;
        StringBuilder currentData = new StringBuilder();

        String[] lines = response.split("\\R");

        for (String line : lines) {

            if (line.startsWith("event:")) {

                if (currentEvent != null && currentData.length() > 0) {
                    answer.append(processSseEvent(currentEvent, currentData.toString()));
                }

                currentEvent = line.substring(6).trim();
                currentData.setLength(0);
            }

            else if (line.startsWith("data:")) {

                if (currentData.length() > 0) {
                    currentData.append("\n");
                }

                currentData.append(line.substring(5).trim());
            }

            else if (line.isBlank()) {

                if (currentEvent != null && currentData.length() > 0) {
                    answer.append(processSseEvent(currentEvent, currentData.toString()));
                }

                currentEvent = null;
                currentData.setLength(0);
            }
        }

        if (currentEvent != null && currentData.length() > 0) {
            answer.append(processSseEvent(currentEvent, currentData.toString()));
        }

        return answer.toString().trim();
    }

    private String processSseEvent(String eventType, String data) {

        try {

            if (!"content_block_delta".equals(eventType)) {
                return "";
            }

            JsonNode node = objectMapper.readTree(data);
            JsonNode delta = node.path("delta");

            String deltaType = delta.path("type").asText();

            switch (deltaType) {

                case "text_delta":
                    return delta.path("text").asText("");

                case "thinking_delta":
                    return "";

                case "redacted_thinking_delta":
                    return "";

                default:
                    return "";
            }

        } catch (Exception e) {
            log.warn("Failed to parse SSE analysis event: {}", e.getMessage());
            return "";
        }
    }

    private String processSseEventForRewrite(String eventType, String data) {

        try {

            if (!"content_block_delta".equals(eventType)) {
                return "";
            }

            JsonNode node = objectMapper.readTree(data);
            JsonNode delta = node.path("delta");

            String deltaType = delta.path("type").asText();

            switch (deltaType) {

                case "text_delta":
                    return delta.path("text").asText("");

                case "thinking_delta":
                    return "";

                case "redacted_thinking_delta":
                    return "";

                case "thinking":
                    return "";

                default:
                    return "";
            }

        } catch (Exception e) {
            log.warn("Failed to parse SSE rewrite event: {}", e.getMessage());
            return "";
        }
    }

    private String parseRewriteResponse(String response) {

        response = response.trim();

        // Case 1 : Valid JSON
        try {
            JsonNode root = objectMapper.readTree(response);

            if (root.has("recommended_query")) {
                return root.get("recommended_query").asText();
            }

            if (root.has("recommendedQuery")) {
                return root.get("recommendedQuery").asText();
            }

            if (root.has("query")) {
                return root.get("query").asText();
            }

        } catch (Exception ignored) {
        }

        // Case 2 : Starts with WITH or SELECT
        String upper = response.toUpperCase();

        if (upper.startsWith("WITH")
                || upper.startsWith("SELECT")) {
            return response;
        }

        // Case 3 : malformed JSON
        Pattern p = Pattern.compile(
                "(recommended_query|recommendedQuery|query|recomonneded_query)\\s*:?\\s*(.*)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Matcher m = p.matcher(response);

        if (m.find()) {
            return m.group(2)
                    .replaceAll("^[\"']+", "")
                    .replaceAll("[\"'}]+$", "")
                    .trim();
        }

        throw new IllegalArgumentException(
                "Unable to extract rewritten SQL.\n\n" + response);
    }


    // public AiAnalysisResult analyzeUsingClaudeSDK(){

    // } 
}