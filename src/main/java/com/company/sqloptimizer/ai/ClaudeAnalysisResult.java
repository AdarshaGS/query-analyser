package com.company.sqloptimizer.ai;

import java.util.Collections;
import java.util.List;

/**
 * Represents the structured response from Claude AI analysis.
 */
public class ClaudeAnalysisResult {
    private String complexityScore;
    private String comments;
    private List<ClaudeIssue> issues;
    private List<ClaudeRecommendation> recommendations;

    public ClaudeAnalysisResult() {}

    public ClaudeAnalysisResult(String complexityScore, String comments,
                                List<ClaudeIssue> issues, List<ClaudeRecommendation> recommendations) {
        this.complexityScore = (complexityScore != null) ? complexityScore : "0/100";
        this.comments = (comments != null) ? comments : "";
        this.issues = (issues != null) ? issues : Collections.emptyList();
        this.recommendations = (recommendations != null) ? recommendations : Collections.emptyList();
    }

    public String getComplexityScore() {
        return complexityScore;
    }

    public void setComplexityScore(String complexityScore) {
        this.complexityScore = (complexityScore != null) ? complexityScore : "0/100";
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = (comments != null) ? comments : "";
    }

    public List<ClaudeIssue> getIssues() {
        return issues;
    }

    public void setIssues(List<ClaudeIssue> issues) {
        this.issues = (issues != null) ? issues : Collections.emptyList();
    }

    public List<ClaudeRecommendation> getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(List<ClaudeRecommendation> recommendations) {
        this.recommendations = (recommendations != null) ? recommendations : Collections.emptyList();
    }
}