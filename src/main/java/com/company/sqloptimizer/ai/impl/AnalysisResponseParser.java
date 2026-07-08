package com.company.sqloptimizer.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * ponytail: parses Claude's JSON response for SQL analysis format.
 * Extracts complexity_score, comments, issues, recommendations.
 */
@Component
public class AnalysisResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses the Claude response as JSON according to the analysis format.
     * Tries multiple possible field names for robustness.
     *
     * @param responseText the extracted text from Claude's response
     * @return map containing complexity_score, comments, issues, recommendations
     */
    public Map<String, Object> parse(String responseText) {
        try {
            // Extract JSON from the response text (handle cases where there's extra text
            // before/after)
            String jsonText = extractJsonFromText(responseText);

            // Parse JSON
            JsonNode jsonNode = objectMapper.readTree(jsonText);
            Map<String, Object> result = new HashMap<>();

            // Helper to get string value with fallback
            Function<JsonNode, String> getString = node -> node == null || node.isNull()
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
            // Return empty map if parsing fails
            return new HashMap<>();
        }
    }

    /**
     * Extracts a JSON object or array from text that may contain other content
     * before or after it.
     * Looks for the first valid JSON object or array in the text that contains
     * expected fields.
     */
    private String extractJsonFromText(String text) {
        if (text == null || text.isBlank()) {
            return "{}";
        }

        String cleaned = text.trim();

        // Clean the response - remove any markdown code block markers if present
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        // Try to parse directly first
        try {
            objectMapper.readTree(cleaned);
            return cleaned;
        } catch (Exception ignored) {
            // If direct parsing fails, try to find JSON objects/arrays within the text
        }

        // Try to find valid JSON objects by looking for matching braces
        String jsonObject = findFirstValidJsonObjectWithExpectedFields(cleaned);
        if (jsonObject != null && !jsonObject.isEmpty()) {
            return jsonObject;
        }

        // Fallback to first valid JSON object (without field checking)
        jsonObject = findFirstValidJsonObject(cleaned);
        if (jsonObject != null && !jsonObject.isEmpty()) {
            return jsonObject;
        }

        // Try to find valid JSON arrays by looking for matching brackets
        String jsonArray = findFirstValidJsonArray(cleaned);
        if (jsonArray != null && !jsonArray.isEmpty()) {
            return jsonArray;
        }

        // If we can't find valid JSON, return empty object
        return "{}";
    }

    /**
     * Finds the first valid JSON object in the text that contains at least one
     * expected field.
     * Expected fields: complexity_score, complexityScore, score, comments, comment,
     * explanation, issues, recommendations
     *
     * @param text The text to search
     * @return The first valid JSON object found with expected fields, or null if
     *         none found
     */
    private String findFirstValidJsonObjectWithExpectedFields(String text) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '{') {
                // Found a potential start of JSON object
                int braceCount = 1;
                int j = i + 1;
                while (j < len && braceCount > 0) {
                    char c = text.charAt(j);
                    if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                    }
                    j++;
                }

                // If we found a matching closing brace
                if (braceCount == 0) {
                    String potentialJson = text.substring(i, j);
                    try {
                        // Validate that it's valid JSON
                        JsonNode jsonNode = objectMapper.readTree(potentialJson);

                        // Check if it contains any of the expected fields
                        if (hasExpectedFields(jsonNode)) {
                            return potentialJson;
                        }
                    } catch (Exception e2) {
                        // Try next potential start
                        continue;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Checks if a JSON node contains any of the expected fields for AI analysis
     * response.
     *
     * @param jsonNode The JSON node to check
     * @return true if the node contains at least one expected field, false otherwise
     */
    private boolean hasExpectedFields(JsonNode jsonNode) {
        // Check for complexity score fields
        if (jsonNode.has("complexity_score") || jsonNode.has("complexityScore") || jsonNode.has("score")) {
            return true;
        }
        // Check for comments fields
        if (jsonNode.has("comments") || jsonNode.has("comment") || jsonNode.has("explanation")) {
            return true;
        }
        // Check for issues field
        if (jsonNode.has("issues")) {
            return true;
        }
        // Check for recommendations field
        if (jsonNode.has("recommendations")) {
            return true;
        }
        return false;
    }

    /**
     * Finds the first valid JSON object in the text by looking for balanced braces.
     *
     * @param text The text to search
     * @return The first valid JSON object found, or null if none found
     */
    private String findFirstValidJsonObject(String text) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '{') {
                // Found a potential start of JSON object
                int braceCount = 1;
                int j = i + 1;
                while (j < len && braceCount > 0) {
                    char c = text.charAt(j);
                    if (c == '{') {
                        braceCount++;
                    } else if (c == '}') {
                        braceCount--;
                    }
                    j++;
                }

                // If we found a matching closing brace
                if (braceCount == 0) {
                    String potentialJson = text.substring(i, j);
                    try {
                        objectMapper.readTree(potentialJson);
                        return potentialJson;
                    } catch (Exception e2) {
                        // Try next potential start
                        continue;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the first valid JSON array in the text by looking for matching
     * brackets.
     *
     * @param text The text to search
     * @return The first valid JSON array found, or null if none found
     */
    private String findFirstValidJsonArray(String text) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '[') {
                // Found a potential start of JSON array
                int bracketCount = 1;
                int j = i + 1;
                while (j < len && bracketCount > 0) {
                    char c = text.charAt(j);
                    if (c == '[') {
                        bracketCount++;
                    } else if (c == ']') {
                        bracketCount--;
                    }
                    j++;
                }

                // If we found a matching closing bracket
                if (bracketCount == 0) {
                    String potentialJson = text.substring(i, j);
                    try {
                        objectMapper.readTree(potentialJson);
                        return potentialJson;
                    } catch (Exception e2) {
                        // Try next potential start
                        continue;
                    }
                }
            }
        }
        return null;
    }
}