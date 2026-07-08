package com.company.sqloptimizer.dto;

import com.company.sqloptimizer.ai.ClaudeIssue;
import com.company.sqloptimizer.ai.ClaudeRecommendation;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

/**
 * Data Transfer Object for the analysis report.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnalysisReport {

    private int score;
    private String severity;
    private String executiveSummary;
    private List<IssueDto> issues;
    private List<RecommendationDto> recommendations;
    private String estimatedImpact;
    private double confidence;
    private String aiRawResponse;

    /**
     * Request identifier for retrieving stored analysis components.
     */
    private String requestIdentifier;

    // Claude-specific structured data fields
    private String claudeComplexityScore;
    private String claudeComments;
    private List<ClaudeIssue> claudeIssues;
    private List<ClaudeRecommendation> claudeRecommendations;

}