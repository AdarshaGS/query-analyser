package com.company.sqloptimizer.exception;

/**
 * Base exception for the SQL Optimizer application.
 */
public class SqlOptimizerException extends RuntimeException {

    public SqlOptimizerException(String message) {
        super(message);
    }

    public SqlOptimizerException(String message, Throwable cause) {
        super(message, cause);
    }
}
