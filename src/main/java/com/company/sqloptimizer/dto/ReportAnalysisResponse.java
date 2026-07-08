package com.company.sqloptimizer.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class ReportAnalysisResponse {
    private String complexityScore;
    private String comments;
    private List<IssueDto> issues;
    private List<RecommendationDto> recommendations;
    private String requestIdentifier;
}
