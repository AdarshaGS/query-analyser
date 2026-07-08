package com.company.sqloptimizer.ai.impl;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Standalone drop-in alternative to {@link ClaudeClient}, using the official Anthropic Java SDK
 * (com.anthropic:anthropic-java) instead of RestTemplate + manual JSON/SSE parsing.
 *
 * Not wired into anything yet - {@link ClaudeAnalysisService} and
 * {@link com.company.sqloptimizer.rewrite.ai.ClaudeRewriteService} still use {@link ClaudeClient}.
 *
 * Same public contract as ClaudeClient.sendRequest(systemPrompt, userPrompt): tries each model in
 * the configured list with retries, returns the extracted assistant text.
 *
 * Key difference from ClaudeClient: this returns plain extracted text directly (the SDK already
 * parses the JSON response), NOT a raw HTTP body. If you swap this in for ClaudeClient, remove the
 * ClaudeSseParser.parse(...) step in whichever service you wire it into - the text is already
 * extracted here, running it through the SSE parser again will return an empty string.
 *
 * To swap in:
 * 1. In ClaudeAnalysisService / ClaudeRewriteService, change the constructor param type from
 *    ClaudeClient to ClaudeSdkClient, drop the ClaudeSseParser param and the
 *    sseParser.parse(responseBody) line, use the returned string directly.
 * 2. Test end to end (analyze_sql / rewrite_sql flows) before deleting the old ClaudeClient.
 */
@Component
public class ClaudeSdkClient {

    private final AnthropicClient anthropicClient;
    private final String modelListString;
    private final int maxAttemptsPerModel;

    public ClaudeSdkClient(@Value("${anthropic.base.url}") String baseUrl,
                           @Value("${anthropic.auth.token}") String authToken,
                           @Value("${ai.model.list:}") String modelListString,
                           @Value("${ai.model.max-attempts:3}") int maxAttemptsPerModel) {
        // apiKey() sends the token as "x-api-key", matching ClaudeClient's manual header setup.
        // If your proxy expects "Authorization: Bearer <token>" instead, use .authToken(authToken).
        this.anthropicClient = AnthropicOkHttpClient.builder()
                .baseUrl(baseUrl)
                .apiKey(authToken)
                .build();
        this.modelListString = modelListString;
        this.maxAttemptsPerModel = maxAttemptsPerModel;
    }

    /**
     * Sends a request to Claude, trying each model in the configured list with retries.
     *
     * @param systemPrompt the system prompt
     * @param userPrompt   the user prompt
     * @return the extracted assistant text response (already parsed, no SSE step needed)
     * @throws Exception if all attempts across all models fail
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
                    MessageCreateParams params = MessageCreateParams.builder()
                            .model(model)
                            .maxTokens(4000L)
                            .temperature(0.0)
                            .topP(0.9)
                            .system(systemPrompt)
                            .addUserMessage(userPrompt)
                            .build();

                    Message message = anthropicClient.messages().create(params);

                    String text = message.content().stream()
                            .flatMap(block -> block.text().stream())
                            .map(TextBlock::text)
                            .collect(Collectors.joining());

                    if (text.isBlank()) {
                        throw new RuntimeException("Empty text response from Claude API");
                    }

                    return text;
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
