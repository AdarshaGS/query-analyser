package com.company.sqloptimizer.service.exceptiontranslator;

/**
 * Domain-specific exception for SQL analysis operations.
 * Contains structured error information instead of raw SQL exceptions.
 */
public class SqlAnalysisException extends RuntimeException {

    private final String errorCode;
    private final String message;
    private final String suggestion;

    public SqlAnalysisException(String errorCode, String message, String suggestion) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
        this.suggestion = suggestion;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    /**
     * Returns a formatted error message including suggestion if available.
     *
     * @return formatted error message
     */
    public String getDetailedMessage() {
        if (suggestion != null && !suggestion.isEmpty()) {
            return message + ". Suggestion: " + suggestion;
        }
        return message;
    }
}