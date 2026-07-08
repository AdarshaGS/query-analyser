package com.company.sqloptimizer.rewrite.ai;

/**
 * Client for interacting with the Claude AI model.
 */
public interface ClaudeClient {

    /**
     * Sends a prompt to Claude and returns the response.
     *
     * @param systemPrompt    the system prompt
     * @param userPrompt      the user prompt
     * @return the response from Claude
     */
    String invoke(String systemPrompt, String userPrompt);
}