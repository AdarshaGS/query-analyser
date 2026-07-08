package com.company.sqloptimizer.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SqlAnalysisResponse {

    private int complexityScore;
    private Severity severity;
    private List<IssueDto> issues;
    private List<RecommendationDto> recommendations;
    private List<String> detectedTables;
    private List<String> joins;
    private List<String> whereClauses;
    private List<String> aggregations;
    private List<String> groupByClauses;
    private List<String> orderByClauses;
    private List<String> subqueries;
    private List<String> selectFields;
    private EstimatedComplexity estimatedComplexity;

}