package com.company.sqloptimizer.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaRegistrationRequest {

    private String createTableStatement;
    private List<String> indexStatements;

}
