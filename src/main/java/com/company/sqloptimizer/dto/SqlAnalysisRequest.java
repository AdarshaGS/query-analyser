package com.company.sqloptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SqlAnalysisRequest {

    private String query;

}
