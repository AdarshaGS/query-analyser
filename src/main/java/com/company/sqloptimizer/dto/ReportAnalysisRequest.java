package com.company.sqloptimizer.dto;

import lombok.*;
import org.springframework.lang.Nullable;

/**
 * Request object for SQL analysis reports.
 * Supports multiple input types: direct SQL query, query ID, or query name.
 * The actual SQL query is determined in the following order:
 * 1. Direct query (if provided and not blank)
 * 2. Query by ID (if queryId is provided)
 * 3. Query by name (if queryName is provided and not blank)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportAnalysisRequest {

    /** The SQL query to analyze (used when provided and not blank) */
    @Nullable
    private String query;

    /** ID of a stored query to lookup (used when query is blank/null) */
    @Nullable
    private Long queryId;

    /** Name of a stored query to lookup (used when query and queryId are blank/null) */
    @Nullable
    private String reportName;

    /** Optional pre-computed EXPLAIN JSON to avoid re-execution */
    @Nullable
    private String explainJson;
}