package com.company.sqloptimizer.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SchemaRegistrationResponse {

    private Long tableId;
    private String tableName;
    private String schemaName;
    private String message;

}
