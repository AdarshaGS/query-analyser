package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.dto.Severity;
import lombok.*;

@Getter
@Builder
public class RuleResult {

    private boolean issue;
    private String message;
    private Severity severity;
    private boolean recommendation;
    private String recommendationMessage;

    public static RuleResult of(boolean issue, String message, Severity severity) {
        return RuleResult.builder()
                .issue(issue)
                .message(message)
                .severity(severity)
                .recommendation(false)
                .recommendationMessage(null)
                .build();
    }

    public static RuleResult of(boolean issue, String message, Severity severity, boolean recommendation, String recommendationMessage) {
        return RuleResult.builder()
                .issue(issue)
                .message(message)
                .severity(severity)
                .recommendation(recommendation)
                .recommendationMessage(recommendationMessage)
                .build();
    }

    public boolean isIssue() {
        return issue;
    }

    public boolean hasRecommendation() {
        return recommendation;
    }

    public String getRecommendation() {
        return recommendationMessage;
    }
}
