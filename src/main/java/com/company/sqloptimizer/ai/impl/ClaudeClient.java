package com.company.sqloptimizer.ai.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ponytail: common Claude client handling HTTP calls, authentication, retries, model fallback, timeout handling.
 * Knows nothing about SQL.
 */
@Component
public class ClaudeClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String authToken;
    private final String modelListString;
    private final int maxAttemptsPerModel;

    public ClaudeClient(RestTemplate restTemplate,
                        @Value("${anthropic.base.url}") String baseUrl,
                        @Value("${anthropic.auth.token}") String authToken,
                        @Value("${ai.model.list:}") String modelListString,
                        @Value("${ai.model.max-attempts:3}") int maxAttemptsPerModel) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
        this.authToken = authToken;
        this.modelListString = modelListString;
        this.maxAttemptsPerModel = maxAttemptsPerModel;
    }

    /**
     * Sends a request to Claude API with given system and user prompts, trying models from the list with retries.
     *
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @return raw response string from Claude API
     * @throws Exception if all attempts fail
     */
    public String sendRequest(String systemPrompt, String userPrompt) throws Exception {
        List<String> modelList = new ArrayList<>();
        for (String s : modelListString.split(",")) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                modelList.add(trimmed);
            }
        }
        if (modelList.isEmpty()) {
            modelList = List.of("claude-3-5-sonnet-20241022");
        }

        Exception lastException = null;
        for (String model : modelList) {
            for (int attempt = 1; attempt <= maxAttemptsPerModel; attempt++) {
                try {
                    String url = baseUrl.endsWith("/") ? baseUrl + "v1/messages" : baseUrl + "/v1/messages";

                    Map<String, Object> requestBody = new java.util.HashMap<>();
                    requestBody.put("model", model);
                    requestBody.put("max_tokens", 4000);
                    requestBody.put("temperature", 0.0);
                    requestBody.put("top_p", 0.9);
                    requestBody.put("system", systemPrompt);
                    requestBody.put("messages", List.of(
                            Map.of("role", "user", "content", userPrompt)));

                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("x-api-key", authToken);

                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

                    ResponseEntity<String> responseEntity = restTemplate.exchange(
                            url,
                            HttpMethod.POST,
                            requestEntity,
                            String.class);

                    String responseBody = responseEntity.getBody();
                    if (responseBody == null) {
                        throw new RuntimeException("Empty response from Claude API");
                    }
                    return responseBody;
                } catch (Exception e) {
                    lastException = e;
                    if (attempt < maxAttemptsPerModel) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            // Exhausted attempts for this model, continue to next
        }
        throw new RuntimeException("All Claude API attempts failed", lastException);
    }
}
