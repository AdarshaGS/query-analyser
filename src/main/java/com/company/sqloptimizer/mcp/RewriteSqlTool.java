package com.company.sqloptimizer.mcp;

import org.springframework.stereotype.Component;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

import com.company.sqloptimizer.rewrite.dto.RewriteSqlResponse;
import com.company.sqloptimizer.service.ReportAnalysisService;

@Component
public class RewriteSqlTool {

    private final ReportAnalysisService reportAnalysisService;

    public RewriteSqlTool(ReportAnalysisService reportAnalysisService) {
        this.reportAnalysisService = reportAnalysisService;
    }

    @McpTool(name = "rewrite_sql", description = "Generate an optimized rewrite of a previously analyzed query, using its request identifier.")
    public RewriteSqlResponse rewriteSql(
            @McpToolParam(description = "The request-identifier returned by analyze_sql", required = true) String requestId) {
        return reportAnalysisService.rewriteSqlByRequestIdentifier(requestId);
    }
}