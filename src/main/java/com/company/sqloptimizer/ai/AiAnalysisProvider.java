package com.company.sqloptimizer.ai;

/**
 * Interface for AI analysis providers.
 */
public interface AiAnalysisProvider {

    /**
     * Analyzes the given request and returns an AI analysis result.
     *
     * @param request the analysis request containing SQL analysis, explain analysis, schema metadata, and table statistics
     * @return the AI analysis result
     */
    AiAnalysisResult analyze(AnalysisRequest request);


}