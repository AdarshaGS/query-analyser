package com.company.sqloptimizer.ai.impl;

import com.company.sqloptimizer.ai.ClaudeIssue;
import com.company.sqloptimizer.ai.ClaudeRecommendation;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * ponytail: response model for Claude's analysis response.
 * Maps to the expected JSON structure for SQL analysis.
 */
public class ClaudeAnalysisResponse {

    @JsonProperty("complexity_score")
    private String complexityScore;

    private String comments;

    private List<ClaudeIssue> issues;

    private List<ClaudeRecommendation> recommendations;

    // Getters and setters
    public String getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(String complexityScore) {
        this.complexityScore = complexityScore;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public List<ClaudeIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ClaudeIssue> issues) {
        this.issues = issues;
    }

    public List<ClaudeRecommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<ClaudeRecommendation> recommendations) {
        this.recommendations = recommendations;
    }
}