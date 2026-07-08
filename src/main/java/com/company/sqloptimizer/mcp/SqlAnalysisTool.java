package com.company.sqloptimizer.mcp;

import org.springframework.stereotype.Component;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

import com.company.sqloptimizer.dto.ReportAnalysisRequest;
import com.company.sqloptimizer.dto.ReportAnalysisResponse;
import com.company.sqloptimizer.service.ReportAnalysisService;

@Component
public class SqlAnalysisTool {

    private final ReportAnalysisService reportAnalysisService;

    public SqlAnalysisTool(ReportAnalysisService reportAnalysisService) {
        this.reportAnalysisService = reportAnalysisService;
    }

    @McpTool(name = "analyze_sql", description = "Run SQL analysis: parsing, EXPLAIN, rule engine, and AI recommendations. Returns a request identifier for later rewrite.")
    public ReportAnalysisResponse analyzeSql(
            @McpToolParam(description = "The analysis request: raw SQL query, or a stored queryId/reportName", required = true) ReportAnalysisRequest reportAnalysisRequest) {

        return this.reportAnalysisService.analyzeReportUsingMcp(reportAnalysisRequest);

    }
}