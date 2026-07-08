package com.company.sqloptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueDto {

    private String issue;
    private Severity severity;

}
