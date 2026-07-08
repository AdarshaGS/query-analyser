package com.company.sqloptimizer.rewrite.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.sqloptimizer.rewrite.dto.RewriteSqlResponse;
import com.company.sqloptimizer.rewrite.dto.SqlRewriteByIdRequest;
import com.company.sqloptimizer.service.ReportAnalysisService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rewrite")
@RequiredArgsConstructor
public class RewriteController {

    private final ReportAnalysisService reportAnalysisService;

    /**
     * Rewrites SQL based on a previous analysis request identifier.
     *
     * @param request the request containing the request identifier
     * @return the optimized query string
     */
    @PostMapping
    public RewriteSqlResponse rewriteSqlByRequestIdentifier(@RequestBody SqlRewriteByIdRequest request) {
        if (request == null || request.getRequestIdentifier() == null || request.getRequestIdentifier().isBlank()) {
            return RewriteSqlResponse.builder().build();
        }
        try {
            RewriteSqlResponse response = reportAnalysisService.rewriteSqlByRequestIdentifier(request.getRequestIdentifier());
            return RewriteSqlResponse.builder().optimizedQuery(response.getOptimizedQuery()).build();
        } catch (IllegalArgumentException e) {
            return RewriteSqlResponse.builder().build();
        } catch (IllegalStateException e) {
            return RewriteSqlResponse.builder().build();
        }
    }
}