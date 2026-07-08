package com.company.sqloptimizer.ai;

/**
 * Represents a recommendation in the Claude AI analysis response.
 */
public class ClaudeRecommendation {
    private String recommendation;
    private String recommendedQuery;

    public ClaudeRecommendation() {}

    public ClaudeRecommendation(String recommendation, String recommendedQuery) {
        this.recommendation = recommendation;
        this.recommendedQuery = recommendedQuery;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(String recommendation) {
        this.recommendation = recommendation;
    }

    public String getRecommendedQuery() {
        return recommendedQuery;
    }

    public void setRecommendedQuery(String recommendedQuery) {
        this.recommendedQuery = recommendedQuery;
    }
}