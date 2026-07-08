package com.company.sqloptimizer.rewrite.dto;

/**
 * Request DTO for SQL Rewrite by ID API.
 */
public class SqlRewriteByIdRequest {

    private String requestIdentifier;

    public String getRequestIdentifier() {
        return requestIdentifier;
    }

    public void setRequestIdentifier(String requestIdentifier) {
        this.requestIdentifier = requestIdentifier;
    }
}