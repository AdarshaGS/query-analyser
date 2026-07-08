package com.company.sqloptimizer.ai;

/**
 * Represents an issue in the Claude AI analysis response.
 */
public class ClaudeIssue {
    private String issue;
    private String severityContribution;
    private String recommendation;
    private String recommendedQuery;

    public ClaudeIssue() {}

    public ClaudeIssue(String issue, String severityContribution, String recommendation, String recommendedQuery) {
        this.issue = issue;
        this.severityContribution = severityContribution;
        this.recommendation = recommendation;
        this.recommendedQuery = recommendedQuery;
    }

    public String getIssue() {
        return issue;
    }

    public void setIssue(String issue) {
        this.issue = issue;
    }

    public String getSeverityContribution() {
        return severityContribution;
    }

    public void setSeverityContribution(String severityContribution) {
        this.severityContribution = severityContribution;
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