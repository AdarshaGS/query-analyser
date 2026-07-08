package com.company.sqloptimizer.service.exceptiontranslator;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

/**
 * Translates low-level SQL exceptions into meaningful domain exceptions.
 * Never exposes raw SQL exceptions to callers.
 */
@Component
public class ExceptionTranslator {

    /**
     * Translates a DataAccessException into a SqlAnalysisException with meaningful error information.
     *
     * @param ex the DataAccessException to translate
     * @param sql the SQL that caused the exception (for context in logging)
     * @return a SqlAnalysisException with error code, message, and suggestion
     */
    public SqlAnalysisException translate(DataAccessException ex, String sql) {
        // Log the original exception for debugging
        if (ex.getCause() != null) {
            // Log the root cause
            Throwable cause = getRootCause(ex);
            // In a real implementation, you would log this appropriately
            // logger.debug("SQL exception root cause: {}", cause.toString(), cause);
        }

        // Extract the underlying SQLException from the DataAccessException
        Throwable cause = getRootCause(ex);
        SQLException sqlEx = null;
        if (cause instanceof SQLException) {
            sqlEx = (SQLException) cause;
        }

        // Extract meaningful error information
        String errorCode = getErrorCode(sqlEx);
        String message = getMessage(sqlEx);
        String suggestion = getSuggestion(sqlEx, sql);

        return new SqlAnalysisException(errorCode, message, suggestion);
    }

    /**
     * Gets the root cause of an exception by walking the cause chain.
     *
     * @param ex the exception to examine
     * @return the root cause Throwable
     */
    private Throwable getRootCause(Throwable ex) {
        Throwable cause = ex.getCause();
        while (cause != null && cause != ex && !cause.getClass().getName().equals(ex.getClass().getName())) {
            if (cause.getCause() == null) {
                return cause;
            }
            cause = cause.getCause();
        }
        return ex;
    }

    /**
     * Extracts an error code from the SQLException.
     *
     * @param sqlEx the SQLException
     * @return the error code as a string
     */
    private String getErrorCode(SQLException sqlEx) {
        if (sqlEx == null) {
            return "UNKNOWN_ERROR";
        }
        String errorCode = String.valueOf(sqlEx.getErrorCode());
        return !errorCode.equals("0") ? errorCode : "SQL_ERROR";
    }

    /**
     * Extracts a meaningful message from the SQLException.
     *
     * @param sqlEx the SQLException
     * @return the error message
     */
    private String getMessage(SQLException sqlEx) {
        if (sqlEx == null) {
            return "Unknown SQL error occurred";
        }
        String message = sqlEx.getMessage();
        return (message != null && !message.isEmpty()) ? message : "SQL error occurred";
    }

    /**
     * Provides a helpful suggestion based on the SQL error type.
     *
     * @param sqlEx the SQLException
     * @param sql   the SQL that was being executed
     * @return a suggestion for resolving the error
     */
    private String getSuggestion(SQLException sqlEx, String sql) {
        if (sqlEx == null) {
            return "Check the SQL syntax and database connection";
        }

        String sqlState = sqlEx.getSQLState();
        int errorCode = sqlEx.getErrorCode();
        String message = sqlEx.getMessage() != null ? sqlEx.getMessage().toUpperCase() : "";

        // Handle specific error cases
        if (sqlState.equals("42000") || errorCode == 1064) { // Syntax error
            if (message.contains("FORMAT=JSON") || message.contains("FORMAT JSON")) {
                return "FORMAT=JSON is not supported on this database version. Try without FORMAT clause or upgrade your database.";
            }
            return "Check SQL syntax near: " + extractProblematicSqlFragment(sql, sqlEx);
        }

        if (message.contains("UNKNOWN TABLE") || message.contains("TABLE_DOESNT_EXIST") ||
            message.contains("NO_SUCH_TABLE") || sqlState.equals("42S02")) {
            return "Table does not exist. Check table name spelling and ensure you're connected to the correct database.";
        }

        if (message.contains("UNKNOWN COLUMN") || message.contains("BAD_FIELD") ||
            message.contains("ILLEGAL_COLUMN") || sqlState.equals("42S22")) {
            return "Column does not exist. Check column name spelling and table structure.";
        }

        if (sqlState.equals("08001") || sqlState.equals("08004")) { // Connection refused
            return "Database connection failed. Check network connectivity, database server status, and connection parameters.";
        }

        if (sqlState.equals("08003") || sqlState.equals("08006")) { // Connection failed
            return "Database connection was not established or was terminated unexpectedly.";
        }

        if (sqlState.equals("28000")) { // Access denied
            return "Access denied. Check username/password and database permissions.";
        }

        if (sqlState.equals("HY000")) { // General error
            if (message.contains("TIMEOUT")) {
                return "Query execution timed out. Consider optimizing the query or increasing timeout settings.";
            }
            if (message.contains("LOCK") || message.contains("DEADLOCK")) {
                return "Database lock detected. Try again later or investigate locking issues.";
            }
        }

        // Default suggestion
        return "Verify your SQL query and database connection settings.";
    }

    /**
     * Extracts a fragment of SQL around where the error likely occurred.
     * This is a simplified implementation - in production you might want
     * to parse the SQL more carefully based on the error details.
     *
     * @param sql    the original SQL
     * @param sqlEx  the SQLException (may contain position info)
     * @return a snippet of the SQL around the problem area
     */
    private String extractProblematicSqlFragment(String sql, SQLException sqlEx) {
        // This is a simplified implementation
        // A more sophisticated version would parse line/column info from the exception
        if (sql == null || sql.isEmpty()) {
            return "";
        }
        int length = Math.min(50, sql.length());
        return sql.length() > length ? sql.substring(0, length) + "..." : sql;
    }
}