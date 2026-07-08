package com.company.sqloptimizer.rewrite.ai;

import com.company.sqloptimizer.rewrite.dto.RewriteSqlResponse;
import com.company.sqloptimizer.rewrite.api.RewritePromptBuilder;
import com.company.sqloptimizer.ai.impl.ClaudeClient;
import com.company.sqloptimizer.ai.impl.ClaudeSseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * ponytail: service for SQL rewrite using Claude API.
 * Handles rewrite prompt building, calling Claude, parsing SSE, parsing JSON response.
 * Mimics the original ClaudeClient interface for easy substitution.
 */
@Service
public class ClaudeRewriteService {

    private final ClaudeClient claudeClient;
    private final RewritePromptBuilder promptBuilder;
    private final ClaudeSseParser sseParser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeRewriteService(ClaudeClient claudeClient,
                                RewritePromptBuilder promptBuilder,
                                ClaudeSseParser sseParser) {
        this.claudeClient = claudeClient;
        this.promptBuilder = promptBuilder;
        this.sseParser = sseParser;
    }

    /**
     * Rewrites the SQL query using Claude AI.
     * Takes system and user prompts like the original ClaudeClient.invoke method.
     *
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @return the optimized query string
     * @throws Exception if the rewrite fails
     */
    public String rewriteSql(String systemPrompt, String userPrompt) throws Exception {
        // Call Claude API
        String responseBody = claudeClient.sendRequest(systemPrompt, userPrompt);

        // Parse SSE response to get text
        String responseText = sseParser.parse(responseBody);

        // Parse JSON response to extract optimized query
        return parseRewriteResponse(responseText);
    }

    /**
     * Parses the Claude response to extract the optimized query.
     * Expects the response to contain an "optimizedQuery" field as per the prompt.
     *
     * @param responseText the extracted text from Claude's response
     * @return the optimized query string
     * @throws Exception if parsing fails or optimized_query is missing
     */
    private String parseRewriteResponse(String responseText) throws Exception {
        String jsonText = extractJsonFromText(responseText);
        JsonNode root = objectMapper.readTree(jsonText);

        if (root.has("optimizedQuery")) {
            return root.get("optimizedQuery").asText();
        }
        // Fallback: try other common field names for robustness
        if (root.has("optimized_query")) {
            return root.get("optimized_query").asText();
        }
        if (root.has("query")) {
            return root.get("query").asText();
        }

        throw new IllegalArgumentException(
                "Claude response does not contain optimizedQuery.\nResponse:\n" + responseText);
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