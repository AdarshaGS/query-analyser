package com.company.sqloptimizer.rewrite.ai.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ponytail: response model for Claude's rewrite response.
 * Maps to the expected JSON structure for SQL rewrite.
 */
public class ClaudeRewriteResponse {

    @JsonProperty("optimizedQuery")
    private String optimizedQuery;

    // Getter and setter
    public String getOptimizedQuery() {
        return optimizedQuery;
    }

    public void setOptimizedQuery(String optimizedQuery) {
        this.optimizedQuery = optimizedQuery;
    }
}