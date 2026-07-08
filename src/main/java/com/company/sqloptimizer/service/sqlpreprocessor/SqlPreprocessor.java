package com.company.sqloptimizer.service.sqlpreprocessor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handles SQL preprocessing tasks.
 * Responsibilities:
 * - Remove trailing semicolons
 * - Trim whitespace
 * - Replace ${...} placeholders safely
 * - Preserve quoted strings
 * - Convert identifier double quotes to MySQL backticks only when appropriate
 * - Normalize whitespace
 * - Fix common formatting mistakes
 * - Never modify string literals
 * - Never corrupt escaped characters
 */
@Component
public class SqlPreprocessor {

    private static final Logger logger = LoggerFactory.getLogger(SqlPreprocessor.class);

    /**
     * Preprocesses the SQL query for safe EXPLAIN execution.
     *
     * @param sql the raw SQL query
     * @return the preprocessed SQL query
     */
    public String preprocess(String sql) {
        if (sql == null) {
            return null;
        }

        logger.debug("Original SQL: {}", sql);

        String processed = sql;

        // 1. Remove trailing semicolons
        processed = removeTrailingSemicolons(processed);

        // 2. Trim whitespace
        processed = processed.trim();

        // 3. Replace ${...} placeholders safely
        processed = replacePlaceholders(processed);

        // 4. Convert identifier double quotes to MySQL backticks only when appropriate
        processed = convertDoubleQuotesToBackticks(processed);

        // 5. Normalize whitespace
        processed = normalizeWhitespace(processed);

        // 6. Fix common formatting mistakes
        processed = fixCommonFormattingMistakes(processed);

        logger.debug("Processed SQL: {}", processed);
        return processed;
    }

    /**
     * Removes trailing semicolons from the SQL.
     *
     * @param sql the SQL query
     * @return SQL without trailing semicolons
     */
    private String removeTrailingSemicolons(String sql) {
        if (sql == null) {
            return null;
        }
        return sql.replaceAll(";\\s*$", "");
    }

    /**
     * Replaces ${...} placeholders with empty string literals to maintain SQL syntax validity.
     *
     * @param sql the SQL query
     * @return SQL with placeholders replaced
     */
    private String replacePlaceholders(String sql) {
        if (sql == null) {
            return null;
        }
        // Replace ${...} with '' (empty string literal) to maintain syntax validity
        return sql.replaceAll("\\$\\{[^}]+\\}", "''");
    }

    /**
     * Converts double quotes used for identifiers to backticks, but only when outside of single quoted strings.
     * This helps make SQL more MySQL-compatible for EXPLAIN purposes.
     *
     * @param sql the SQL string to convert
     * @return converted SQL string
     */
    private String convertDoubleQuotesToBackticks(String sql) {
        if (sql == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(sql.length());
        boolean inSingleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (escaped) {
                escaped = false;
                sb.append(c);
            } else if (c == '\\') {
                escaped = true;
                // Do not append the backslash - it's an escape character
            } else if (c == '\'' && !escaped) {
                inSingleQuote = !inSingleQuote;
                sb.append(c);
            } else if (c == '"' && !inSingleQuote && !escaped) {
                sb.append('`');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Normalizes whitespace in the SQL (multiple spaces to single space).
     *
     * @param sql the SQL query
     * @return SQL with normalized whitespace
     */
    private String normalizeWhitespace(String sql) {
        if (sql == null) {
            return null;
        }
        return sql.replaceAll("\\s+", " ");
    }

    /**
     * Fixes common formatting mistakes in SQL.
     * Examples: ",col" → ", col", ")JOIN" → ") JOIN".
     *
     * @param sql the SQL query
     * @return SQL with fixed formatting
     */
    private String fixCommonFormattingMistakes(String sql) {
        if (sql == null) {
            return null;
        }

        StringBuilder result = new StringBuilder(sql.length() + 20); // Allow for extra spaces
        boolean inSingleQuote = false;
        boolean escaped = false;

        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);

            // Handle escape sequences
            if (escaped) {
                escaped = false;
                result.append(current);
                continue;
            }

            if (current == '\\') {
                escaped = true;
                result.append(current);
                continue;
            }

            // Handle quote toggling
            if (current == '\'' && !escaped) {
                inSingleQuote = !inSingleQuote;
                result.append(current);
                continue;
            }

            // Only apply spacing fixes outside of string literals
            if (!inSingleQuote) {
                // Look ahead to see what's coming (if anything)
                boolean hasNext = i + 1 < sql.length();
                char next = hasNext ? sql.charAt(i + 1) : '\0';

                // Add current character
                result.append(current);

                // Add space after ) if followed by a letter (start of keyword)
                if (current == ')' && hasNext && Character.isLetter(next)) {
                    result.append(' ');
                }
                // Add space after , if followed by a letter (start of identifier)
else if (current == ',' && hasNext && Character.isLetter(next)) {
                    result.append(' ');
                }
                // Add space after ' if followed by a letter (start of keyword, e.g., ' AND)
else if (current == '\'' && hasNext && Character.isLetter(next)) {
                    result.append(' ');
                }
            } else {
                // Inside string literals, just append as-is
                result.append(current);
            }
        }

        return result.toString();
    }
}