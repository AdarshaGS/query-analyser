package com.company.sqloptimizer.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * ponytail: generic SSE parser that extracts text content from Claude's Server-Sent Events response.
 * Ignores thinking and redacted_thinking deltas, only accumulates text deltas.
 */
@Component
public class ClaudeSseParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parses the SSE response and returns the accumulated text content.
     *
     * @param sseResponse the raw SSE response string
     * @return the extracted text content
     */
    public String parse(String sseResponse) {
        StringBuilder accumulatedText = new StringBuilder();

        String currentEventType = null;
        StringBuilder currentData = new StringBuilder();

        String[] lines = sseResponse.split("\\R");

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
                String deltaType = delta.path("type").asText();
                if ("text_delta".equals(deltaType)) {
                    return delta.get("text").asText();
                }
                // Ignore thinking_delta, redacted_thinking_delta, and others
                return "";
            } else if ("message_delta".equals(eventType)) {
                // Ignore
                return "";
            } else {
                // Unknown event type, ignore
                return "";
            }
        } catch (Exception e) {
            // If parsing fails, return empty string for this event
            return "";
        }
    }
}