package com.company.sqloptimizer.recommendation;

import com.company.sqloptimizer.dto.RecommendationDto;
import com.company.sqloptimizer.dto.Severity;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.company.sqloptimizer.dto.ExplainAnalysisResponse;
import com.company.sqloptimizer.ai.AiAnalysisResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Engine for combining and prioritizing recommendations from multiple sources.
 */
@Component
public class RecommendationEngine {

    /**
     * Combines recommendations from SQL analysis, EXPLAIN analysis, and AI analysis.
     *
     * @param sqlAnalysis    the SQL analysis results
     * @param explainAnalysis the EXPLAIN analysis results
     * @param aiAnalysis     the AI analysis results
     * @return a list of deduplicated, prioritized recommendations
     */
    public List<RecommendationDto> combineRecommendations(SqlAnalysisResponse sqlAnalysis,
                                                          ExplainAnalysisResponse explainAnalysis,
                                                          AiAnalysisResult aiAnalysis) {
        // Collect all recommendations with their sources
        List<RecommendationWithSource> allRecommendations = new ArrayList<>();

        // Add SQL analysis recommendations
        if (sqlAnalysis.getRecommendations() != null) {
            allRecommendations.addAll(sqlAnalysis.getRecommendations().stream()
                    .map(rec -> new RecommendationWithSource(rec.getMessage(), "SQL_ANALYSIS"))
                    .collect(Collectors.toList()));
        }

        // Add EXPLAIN analysis recommendations
        if (explainAnalysis != null && explainAnalysis.getRecommendations() != null) {
            allRecommendations.addAll(explainAnalysis.getRecommendations().stream()
                    .map(rec -> new RecommendationWithSource(rec.getMessage(), "EXPLAIN_ANALYSIS"))
                    .collect(Collectors.toList()));
        }

        // Add AI analysis recommendations
        if (aiAnalysis != null && aiAnalysis.getRecommendations() != null) {
            allRecommendations.addAll(aiAnalysis.getRecommendations().stream()
                    .map(rec -> new RecommendationWithSource(rec, "AI_ANALYSIS"))
                    .collect(Collectors.toList()));
        }

        // Deduplicate by message (case-insensitive)
        Map<String, RecommendationWithSource> uniqueMap = new LinkedHashMap<>();
        for (RecommendationWithSource rec : allRecommendations) {
            String key = rec.getMessage().toLowerCase().trim();
            if (!uniqueMap.containsKey(key)) {
                uniqueMap.put(key, rec);
            }
        }

        // Convert back to RecommendationDto list
        return uniqueMap.values().stream()
                .map(rec -> RecommendationDto.builder()
                        .message(rec.getMessage())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Helper class to track the source of a recommendation.
     */
    private static class RecommendationWithSource {
        private final String message;
        private final String source;

        public RecommendationWithSource(String message, String source) {
            this.message = message;
            this.source = source;
        }

        public String getMessage() {
            return message;
        }

        public String getSource() {
            return source;
        }
    }
}