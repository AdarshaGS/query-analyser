package com.company.sqloptimizer.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExplainAnalysisResponse {

    private int score;
    private Severity severity;
    private List<IssueDto> issues;
    private List<RecommendationDto> recommendations;
    private boolean fullScanDetected;
    private boolean tempTableDetected;
    private boolean fileSortDetected;
    private boolean nestedLoopDetected;
    private boolean rowExplosionDetected;

}
