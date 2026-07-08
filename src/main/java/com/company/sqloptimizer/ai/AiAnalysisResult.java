package com.company.sqloptimizer.ai;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Result of AI analysis.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAnalysisResult {

    private String executiveSummary;
    private List<String> rootCauses;
    private List<String> recommendations;
    private double confidence;
    private String rawResponse;

    // Fields for Claude-specific structured data
    private String claudeComplexityScore;
    private String claudeComments;
    private List<ClaudeIssue> claudeIssues;
    private List<ClaudeRecommendation> claudeRecommendations;
    private String recommendedQuery;

}