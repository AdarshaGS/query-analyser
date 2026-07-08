package com.company.sqloptimizer.rewrite.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Client for interacting with the Claude AI model.
 * This is a simplified version of the existing ClaudeApiProvider tailored for the rewrite task.
 */
@Component
@Slf4j
public class ClaudeClientImpl implements ClaudeClient {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String authToken;

    public ClaudeClientImpl(RestTemplate restTemplate,
                            @Value("${anthropic.base.url}") String baseUrl,
                            @Value("${anthropic.auth.token}") String authToken) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.authToken = authToken;
    }

    @Override
    public String invoke(String systemPrompt, String userPrompt) {
        String url = baseUrl.endsWith("/") ? baseUrl + "v1/messages" : baseUrl + "/v1/messages";

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", "claude-3-5-sonnet-20241022");
        requestBody.put("max_tokens", 4000);
        requestBody.put("temperature", 0.0);
        requestBody.put("top_p", 0.9);
        requestBody.put("system", systemPrompt);
        requestBody.put("messages", List.of(
                Map.of("role", "user", "content", userPrompt)
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", authToken);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> responseEntity = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            String responseBody = responseEntity.getBody();
            if (responseBody == null) {
                throw new RuntimeException("Empty response from Claude API");
            }

            String responseText = extractTextFromClaudeResponse(responseBody);
            return responseText;
        } catch (Exception e) {
            log.error("Error invoking Claude API", e);
            throw new RuntimeException("Failed to invoke Claude API: " + e.getMessage(), e);
        }
    }

    
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
     * Extracts text content from Server-Sent Events (SSE) format response.
     * Looks for content_block_delta events and accumulates text/thinking deltas.
     */
    private String extractTextFromSseResponse(String sseResponse) {
        StringBuilder accumulatedText = new StringBuilder();

        // Split by lines and process each line
        String[] lines = sseResponse.split("\n");
        String currentEventType = null;
        StringBuilder currentData = new StringBuilder();

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("event:")) {
                // Save previous event data if any
                if (currentEventType != null && !currentData.isEmpty()) {
                    accumulatedText.append(processSseEvent(currentEventType, currentData.toString()));
                }
                // Start new event
                currentEventType = line.substring(6).trim();
                currentData = new StringBuilder();
            } else if (line.startsWith("data:")) {
                // Accumulate data lines (can be multi-line)
                String dataLine = line.substring(5).trim();
                if (currentData.length() > 0) {
                    currentData.append("\n");
                }
                currentData.append(dataLine);
            } else if (line.isEmpty() && currentEventType != null) {
                // Empty line indicates end of event
                if (currentData.length() > 0) {
                    accumulatedText.append(processSseEvent(currentEventType, currentData.toString()));
                }
                currentEventType = null;
                currentData = new StringBuilder();
            }
        }

        // Process any remaining event
        if (currentEventType != null && currentData.length() > 0) {
            accumulatedText.append(processSseEvent(currentEventType, currentData.toString()));
        }

        return accumulatedText.toString().trim();
    }

    /**
     * Processes a single SSE event and extracts text content.
     */
    private String processSseEvent(String eventType, String data) {
        try {
            if ("[DONE]".equals(data)) {
                return "";
            }

            // Parse the JSON data
            JsonNode dataNode = objectMapper.readTree(data);

            // Handle different event types
            if ("message_start".equals(eventType)) {
                // Just initialization, no text to extract
                return "";
            } else if ("content_block_start".equals(eventType)) {
                // Just indicates start of content block
                return "";
            } else if ("content_block_delta".equals(eventType)) {
                // This is where the actual text appears
                JsonNode delta = dataNode.path("delta");
                if (delta.has("text")) {
                    return delta.get("text").asText();
                } else if (delta.has("thinking")) {
                    return delta.get("thinking").asText();
                }
            } else if ("message_delta".equals(eventType)) {
                // Ignore
                return "";
            } else {
                // Unknown event type, log for debugging
                log.debug("Unhandled SSE event type: {}", eventType);
                return "";
            }
        } catch (Exception e) {
            log.warn("Failed to parse SSE data: {} - {}", data, e.getMessage());
            return "";
        }
        return "";
    }
}