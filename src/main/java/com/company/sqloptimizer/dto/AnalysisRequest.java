package com.company.sqloptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisRequest {

    private String query;
    private String explainJson;

}