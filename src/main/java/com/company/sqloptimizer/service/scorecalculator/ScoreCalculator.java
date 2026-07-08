package com.company.sqloptimizer.service.scorecalculator;

import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.IssueDto;
import com.company.sqloptimizer.dto.RecommendationDto;
import com.company.sqloptimizer.dto.Severity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates scores and determines severity based on EXPLAIN analysis results.
 * Responsibilities:
 * - Calculate score (0-100) based on performance issues
 * - Determine severity level based on score
 * - Calculate penalty points for different issue types
 * - Avoid magic numbers by using named constants
 */
@Component
public class ScoreCalculator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScoreCalculator.class);

    // Score constants - avoid magic numbers
    private static final int MAX_SCORE = 100;
    private static final int FULL_SCAN_PENALTY = 25;      // Critical
    private static final int TEMP_TABLE_PENALTY = 15;     // High
    private static final int FILE_SORT_PENALTY = 15;      // High
    private static final int NESTED_LOOP_PENALTY = 8;     // Medium
    private static final int ROW_EXPLOSION_PENALTY = 8;   // Medium

    // Score thresholds for severity levels
    private static final int SCORE_THRESHOLD_LOW = 90;    // >= 90 = LOW severity
    private static final int SCORE_THRESHOLD_MEDIUM = 75; // 75-89 = MEDIUM severity
    private static final int SCORE_THRESHOLD_HIGH = 50;   // 50-74 = HIGH severity
    // < 50 = CRITICAL severity

    /**
     * Calculates the performance score based on EXPLAIN analysis results.
     *
     * @param result the EXPLAIN analysis result
     * @return score from 0-100 (higher is better)
     */
    public int calculateScore(ExplainAnalysisResult result) {
        int score = MAX_SCORE;

        if (result.isFullScanDetected()) {
            score -= FULL_SCAN_PENALTY;
            LOGGER.debug("Applied full scan penalty: -{} points", FULL_SCAN_PENALTY);
        }

        if (result.isTempTableDetected()) {
            score -= TEMP_TABLE_PENALTY;
            LOGGER.debug("Applied temp table penalty: -{} points", TEMP_TABLE_PENALTY);
        }

        if (result.isFileSortDetected()) {
            score -= FILE_SORT_PENALTY;
            LOGGER.debug("Applied file sort penalty: -{} points", FILE_SORT_PENALTY);
        }

        if (result.isNestedLoopDetected()) {
            score -= NESTED_LOOP_PENALTY;
            LOGGER.debug("Applied nested loop penalty: -{} points", NESTED_LOOP_PENALTY);
        }

        if (result.isRowExplosionDetected()) {
            score -= ROW_EXPLOSION_PENALTY;
            LOGGER.debug("Applied row explosion penalty: -{} points", ROW_EXPLOSION_PENALTY);
        }

        // Ensure score doesn't go below 0
        score = Math.max(0, score);

        LOGGER.debug("Final calculated score: {}", score);
        return score;
    }

    /**
     * Determines the severity level based on the score.
     *
     * @param score the calculated score (0-100)
     * @return the severity level
     */
    public Severity determineSeverity(int score) {
        if (score >= SCORE_THRESHOLD_LOW) {
            return Severity.LOW;
        } else if (score >= SCORE_THRESHOLD_MEDIUM) {
            return Severity.MEDIUM;
        } else if (score >= SCORE_THRESHOLD_HIGH) {
            return Severity.HIGH;
        } else {
            return Severity.CRITICAL;
        }
    }

    /**
     * Calculates penalties and builds issues and recommendations lists.
     * This method can be used if you need to build the lists from scratch.
     *
     * @param result the EXPLAIN analysis result
     * @return a pair containing the score and lists of issues/recommendations
     */
    public ScoreCalculationResult calculateScoreWithDetails(ExplainAnalysisResult result) {
        int score = calculateScore(result);
        Severity severity = determineSeverity(score);

        List<IssueDto> issues = new ArrayList<>();
        List<RecommendationDto> recommendations = new ArrayList<>();

        if (result.isFullScanDetected()) {
            issues.add(IssueDto.builder()
                    .issue("Full table scan detected")
                    .severity(Severity.HIGH)
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Consider adding appropriate indexes to avoid full table scans")
                    // .severity(Severity.HIGH)
                    .build());
        }

        if (result.isTempTableDetected()) {
            issues.add(IssueDto.builder()
                    .issue("Temporary table created")
                    .severity(Severity.MEDIUM)
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Optimize query to avoid temporary tables, consider indexing columns used in GROUP BY or ORDER BY")
                    // .severity(Severity.MEDIUM)
                    .build());
        }

        if (result.isFileSortDetected()) {
            issues.add(IssueDto.builder()
                    .issue("Filesort detected")
                    .severity(Severity.MEDIUM)
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Consider adding indexes to avoid filesort, especially on ORDER BY columns")
                    // .severity(Severity.MEDIUM)
                    .build());
        }

        if (result.isNestedLoopDetected()) {
            issues.add(IssueDto.builder()
                    .issue("Nested loop join detected")
                    .severity(Severity.MEDIUM)
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Review join conditions and ensure proper indexing for join columns")
                    // .severity(Severity.MEDIUM)
                    .build());
        }

        if (result.isRowExplosionDetected()) {
            issues.add(IssueDto.builder()
                    .issue("Potential row explosion in join")
                    .severity(Severity.HIGH)
                    .build());
            recommendations.add(RecommendationDto.builder()
                    .message("Review join conditions and consider adding WHERE clauses to limit rows early")
                    // .severity(Severity.HIGH)
                    .build());
        }

        return new ScoreCalculationResult(score, severity, issues, recommendations);
    }

    /**
     * Helper class to hold the result of score calculation with details.
     */
    public static class ScoreCalculationResult {
        private final int score;
        private final Severity severity;
        private final java.util.List<IssueDto> issues;
        private final java.util.List<RecommendationDto> recommendations;

        public ScoreCalculationResult(int score, Severity severity,
                                      java.util.List<IssueDto> issues,
                                      java.util.List<RecommendationDto> recommendations) {
            this.score = score;
            this.severity = severity;
            this.issues = issues;
            this.recommendations = recommendations;
        }

        public int getScore() {
            return score;
        }

        public Severity getSeverity() {
            return severity;
        }

        public java.util.List<IssueDto> getIssues() {
            return issues;
        }

        public java.util.List<RecommendationDto> getRecommendations() {
            return recommendations;
        }
    }
}