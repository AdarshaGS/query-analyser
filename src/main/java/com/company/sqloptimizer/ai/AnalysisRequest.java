package com.company.sqloptimizer.ai;

import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.company.sqloptimizer.analyzer.SchemaInfo;

import java.util.Map;

/**
 * Request object for AI analysis containing all the analysis data.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRequest {

    private SqlAnalysisResponse sqlAnalysis;
    private ExplainAnalysisResult explainAnalysis;
    private SchemaInfo schemaMetadata;
    private Map<String, Object> tableStatistics;
    private String sqlQuery;
    private String requestType;

}