package com.company.sqloptimizer.rewrite.dto;

import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.company.sqloptimizer.analyzer.SchemaInfo;

import java.util.Map;

/**
 * Request DTO for the SQL Rewrite API.
 */
public class RewriteSqlRequest {

    private String sql;
    private ExplainAnalysisResult executionPlan;
    private SchemaInfo schemaMetadata;
    private Map<String, Object> tableStatistics;
    private SqlAnalysisResponse analysisResult;
    private Integer selectedIssueIndex; // index in analysisResult.issues list
    private Integer selectedRecommendationIndex; // index in analysisResult.recommendations list

    // Getters and Setters
    public String getSql() {
        return sql;
    }

    public void setSql(String sql) {
        this.sql = sql;
    }

    public ExplainAnalysisResult getExecutionPlan() {
        return executionPlan;
    }

    public void setExecutionPlan(ExplainAnalysisResult executionPlan) {
        this.executionPlan = executionPlan;
    }

    public SchemaInfo getSchemaMetadata() {
        return schemaMetadata;
    }

    public void setSchemaMetadata(SchemaInfo schemaMetadata) {
        this.schemaMetadata = schemaMetadata;
    }

    public Map<String, Object> getTableStatistics() {
        return tableStatistics;
    }

    public void setTableStatistics(Map<String, Object> tableStatistics) {
        this.tableStatistics = tableStatistics;
    }

    public SqlAnalysisResponse getAnalysisResult() {
        return analysisResult;
    }

    public void setAnalysisResult(SqlAnalysisResponse analysisResult) {
        this.analysisResult = analysisResult;
    }

    public Integer getSelectedIssueIndex() {
        return selectedIssueIndex;
    }

    public void setSelectedIssueIndex(Integer selectedIssueIndex) {
        this.selectedIssueIndex = selectedIssueIndex;
    }

    public Integer getSelectedRecommendationIndex() {
        return selectedRecommendationIndex;
    }

    public void setSelectedRecommendationIndex(Integer selectedRecommendationIndex) {
        this.selectedRecommendationIndex = selectedRecommendationIndex;
    }
}