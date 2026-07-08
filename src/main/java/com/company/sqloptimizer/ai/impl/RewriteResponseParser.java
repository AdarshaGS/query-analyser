package com.company.sqloptimizer.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * ponytail: parses Claude's JSON response for SQL rewrite format.
 * Extracts only the recommended_query field.
 */
@Component
public class RewriteResponseParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses the Claude response to extract the recommended query.
     *
     * @param responseText the extracted text from Claude's response
     * @return the recommended query string
     * @throws Exception if parsing fails or recommended_query is missing
     */
    public String parse(String responseText) throws Exception {
        String jsonText = extractJsonFromText(responseText);
        JsonNode root = objectMapper.readTree(jsonText);

        if (root.has("recommended_query")) {
            return root.get("recommended_query").asText();
        }
        if (root.has("recommendedQuery")) {
            return root.get("recommendedQuery").asText();
        }
        if (root.has("query")) {
            return root.get("query").asText();
        }

        throw new IllegalArgumentException(
                "Claude response does not contain recommended_query.\nResponse:\n" + responseText);
    }

    /**
     * Extracts a JSON object or array from text that may contain other content
     * before or after it.
     * Looks for the last valid JSON object in the text.
     */
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
                } else if (c == '{') {
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
}