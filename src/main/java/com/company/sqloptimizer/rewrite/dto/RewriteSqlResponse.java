package com.company.sqloptimizer.rewrite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Response DTO for the SQL Rewrite API.
 */
@Data
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class RewriteSqlResponse {
    private String optimizedQuery;
}