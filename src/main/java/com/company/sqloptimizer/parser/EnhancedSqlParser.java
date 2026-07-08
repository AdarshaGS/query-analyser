package com.company.sqloptimizer.parser;

import org.jooq.*;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced SQL Parser using jOOQ that extracts comprehensive SQL components.
 */
@Component
public class EnhancedSqlParser implements SqlParser {

    private final Configuration configuration;
    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "(?i)\\\\b(COUNT|SUM|AVG|MIN|MAX|GROUP_CONCAT|STDDEV|VARIANCE)\\\\s*\\(.*\\)",
            Pattern.CASE_INSENSITIVE);

    public EnhancedSqlParser() {
        this.configuration = new DefaultConfiguration()
                .set(SQLDialect.MYSQL);
    }

    @Override
    public EnhancedParsedQuery parse(String sql) {
        try {
            // Preprocess SQL to replace placeholders like ${...} with a dummy string literal so that jOOQ can parse it
            String processedSql = sql.replaceAll("\\$\\{[^}]+\\}", "'dummy'");

            // Parse the SQL into Queries
            Queries queries = DSL.using(configuration).parser().parse(processedSql);
            List<Query> parts = new ArrayList<>();
            for (Query query : queries) {
                parts.add(query);
            }

            if (parts.isEmpty()) {
                return new EnhancedParsedQuery(sql, Collections.emptySet(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            }

            // We expect the first part to be a Select (for SELECT statements)
            Query part = parts.get(0);
            if (!(part instanceof Select)) {
                // Not a SELECT statement, we return empty info for now
                return new EnhancedParsedQuery(sql, Collections.emptySet(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                        Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            }

            Select<?> select = (Select<?>) part;

            // Extract various components from the SELECT statement
            String selectSql = select.toString();

            Set<String> tableNames = extractTableNamesFromSql(selectSql);
            List<String> joins = extractJoinsFromSql(selectSql);
            List<String> whereClauses = extractWhereFromSql(selectSql);
            List<String> selectFields = extractSelectFieldsFromSql(selectSql);
            List<String> aggregations = extractAggregations(selectFields);
            List<String> groupByClauses = extractGroupByFromSql(selectSql);
            List<String> orderByClauses = extractOrderByFromSql(selectSql);
            List<String> subqueries = extractSubqueriesFromSql(selectSql);

            return new EnhancedParsedQuery(sql, tableNames, joins, whereClauses, aggregations, selectFields,
                    groupByClauses, orderByClauses, subqueries);
        } catch (Exception e) {
            // If parsing fails, fall back to regex-based extraction
            String processedSql = sql.replaceAll("\\$\\{[^}]+\\}", "'dummy'");
            Set<String> tableNames = extractTableNamesFromSql(processedSql);
            List<String> joins = extractJoinsFromSql(processedSql);
            List<String> whereClauses = extractWhereFromSql(processedSql);
            List<String> selectFields = extractSelectFieldsFromSql(processedSql);
            List<String> aggregations = extractAggregations(selectFields);
            List<String> groupByClauses = extractGroupByFromSql(processedSql);
            List<String> orderByClauses = extractOrderByFromSql(processedSql);
            List<String> subqueries = extractSubqueriesFromSql(processedSql);
            return new EnhancedParsedQuery(sql, tableNames, joins, whereClauses, aggregations, selectFields,
                    groupByClauses, orderByClauses, subqueries);
        }
    }

    /**
     * Extracts table names from SQL string using regex.
     */
    private Set<String> extractTableNamesFromSql(String sql) {
        Set<String> names = new HashSet<>();
        String upperSql = sql.toUpperCase();

        // Find FROM clause
        int fromIndex = upperSql.indexOf(" FROM ");
        if (fromIndex >= 0) {
            // Find where the FROM clause ends (next WHERE, GROUP BY, etc.)
            int endIndex = findNextClauseStart(upperSql, fromIndex + 6);
            String fromClause = sql.substring(fromIndex + 6, endIndex).trim();

            // Split by commas and JOIN keywords (including optional INNER|LEFT|RIGHT|FULL|STRAIGHT) to get table references
            String[] tableParts = fromClause.split("(?:,|\\s+(?:INNER|LEFT|RIGHT|FULL|STRAIGHT)?\\s+JOIN\\s+)");
            for (String tablePart : tableParts) {
                String tableName = extractTableName(tablePart.trim());
                if (tableName != null && !tableName.isEmpty()) {
                    names.add(tableName);
                }
            }
        }

        // Also look for tables in JOIN clauses (in case they're not captured above)
        Pattern joinPattern = Pattern.compile("(?:INNER|LEFT|RIGHT|FULL|STRAIGHT)?\\s+JOIN\\s+([^,()\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher joinMatcher = joinPattern.matcher(sql);
        while (joinMatcher.find()) {
            String tableName = extractTableName(joinMatcher.group(1).trim());
            if (tableName != null && !tableName.isEmpty()) {
                names.add(tableName);
            }
        }

        return names;
    }

    /**
     * Extracts JOIN clauses from SQL string.
     */
    private List<String> extractJoinsFromSql(String sql) {
        List<String> joins = new ArrayList<>();
        // Match JOIN clauses
        Pattern joinPattern = Pattern.compile("(?:INNER|LEFT|RIGHT|FULL|STRAIGHT)?\\s+JOIN\\s+[^()]+(?:\\s+ON\\s+[^()]+)?", Pattern.CASE_INSENSITIVE);
        Matcher matcher = joinPattern.matcher(sql);
        while (matcher.find()) {
            joins.add(matcher.group().trim());
        }
        return joins;
    }

    /**
     * Extracts WHERE clause from SQL string.
     */
    private List<String> extractWhereFromSql(String sql) {
        List<String> wheres = new ArrayList<>();
        Pattern wherePattern = Pattern.compile("(?i)\\s+WHERE\\s+(.*?)(?=\\s+(?:ORDER BY|GROUP BY|HAVING|LIMIT)\\s+|$)", Pattern.DOTALL);
        Matcher matcher = wherePattern.matcher(sql);
        if (matcher.find()) {
            wheres.add(matcher.group(1).trim());
        }
        return wheres;
    }

    /**
     * Extracts SELECT fields from SQL string.
     */
    private List<String> extractSelectFieldsFromSql(String sql) {
        List<String> fields = new ArrayList<>();
        // Match everything between SELECT and FROM (DOTALL to handle newlines)
        Pattern selectPattern = Pattern.compile("(?si)^\\s*SELECT\\s+(.*?)\\s+FROM\\s+");
        Matcher matcher = selectPattern.matcher(sql);
        if (matcher.find()) {
            String selectPart = matcher.group(1);
            // Simple split by comma (not perfect but works for most cases)
            String[] fieldParts = selectPart.split(",");
            for (String field : fieldParts) {
                fields.add(field.trim());
            }
        }
        return fields;
    }

    /**
     * Extracts GROUP BY clause from SQL string.
     */
    private List<String> extractGroupByFromSql(String sql) {
        List<String> groupByClauses = new ArrayList<>();
        Pattern groupByPattern = Pattern.compile("(?i)\\s+GROUP BY\\s+(.*?)(?=\\s+(?:ORDER BY|HAVING|LIMIT)\\s+|$)", Pattern.DOTALL);
        Matcher matcher = groupByPattern.matcher(sql);
        if (matcher.find()) {
            groupByClauses.add(matcher.group(1).trim());
        }
        return groupByClauses;
    }

    /**
     * Extracts ORDER BY clause from SQL string.
     */
    private List<String> extractOrderByFromSql(String sql) {
        List<String> orderByClauses = new ArrayList<>();
        Pattern orderByPattern = Pattern.compile("(?i)\\s+ORDER BY\\s+(.*?)(?=\\s+LIMIT\\s+|$)", Pattern.DOTALL);
        Matcher matcher = orderByPattern.matcher(sql);
        if (matcher.find()) {
            orderByClauses.add(matcher.group(1).trim());
        }
        return orderByClauses;
    }

    /**
     * Extracts subqueries from SQL string.
     * Looks for "(SELECT ...)" patterns, handling nested parentheses.
     */
    private List<String> extractSubqueriesFromSql(String sql) {
        List<String> subqueries = new ArrayList<>();
        String upperSql = sql.toUpperCase();
        int len = sql.length();
        int i = 0;
        while (i < len) {
            // Look for the start of a subquery: "(SELECT"
            int openPos = upperSql.indexOf("(SELECT", i);
            if (openPos == -1) {
                break;
            }
            // Ensure the '(' is really at openPos (it is, because we searched for "(SELECT")
            int start = openPos; // include '('
            int count = 0;
            int j = start;
            boolean inString = false;
            char quoteChar = 0;
            while (j < len) {
                char ch = sql.charAt(j);
                // Handle string literals to avoid counting parentheses inside them
                if (!inString) {
                    if (ch == '\'' || ch == '"' || ch == '`') {
                        inString = true;
                        quoteChar = ch;
                    } else if (ch == '(') {
                        count++;
                    } else if (ch == ')') {
                        count--;
                        if (count == 0) {
                            // Found matching closing parenthesis
                            subqueries.add(sql.substring(start, j + 1));
                            i = j + 1;
                            break;
                        }
                    }
                } else {
                    // Inside a string literal
                    if (ch == quoteChar) {
                        // Check for escaped quote
                        if (j > 0 && sql.charAt(j - 1) == '\\') {
                            // escaped, stay in string
                        } else {
                            inString = false;
                        }
                    }
                }
                j++;
            }
            if (j >= len) {
                // No closing found, break to avoid infinite loop
                break;
            }
            // Continue searching after this subquery
            i = j;
        }
        return subqueries;
    }

    /**
     * Finds the start of the next SQL clause after a given position.
     */
    private int findNextClauseStart(String upperSql, int startPos) {
        int wherePos = upperSql.indexOf(" WHERE ", startPos);
        int groupPos = upperSql.indexOf(" GROUP BY ", startPos);
        int havingPos = upperSql.indexOf(" HAVING ", startPos);
        int orderPos = upperSql.indexOf(" ORDER BY ", startPos);
        int limitPos = upperSql.indexOf(" LIMIT ", startPos);

        int nextPos = upperSql.length(); // Default to end of string

        if (wherePos >= 0) nextPos = Math.min(nextPos, wherePos);
        if (groupPos >= 0) nextPos = Math.min(nextPos, groupPos);
        if (havingPos >= 0) nextPos = Math.min(nextPos, havingPos);
        if (orderPos >= 0) nextPos = Math.min(nextPos, orderPos);
        if (limitPos >= 0) nextPos = Math.min(nextPos, limitPos);

        return nextPos;
    }

    /**
     * Extracts a table name from a table reference string.
     * Handles aliases and schema prefixes.
     */
    private String extractTableName(String tableRef) {
        if (tableRef == null || tableRef.isEmpty()) {
            return null;
        }

        // Remove any alias (AS alias or just alias)
        String[] parts = tableRef.split("\\s+(AS\\s+)?", 2);
        String tablePart = parts[0].trim();

        // Remove schema prefix if present (schema.table)
        String[] tableParts = tablePart.split("\\.");
        String tableName = tableParts[tableParts.length - 1];

        // Remove quotes if present
        tableName = tableName.replaceAll("^['\"`]|['\"`]$", "");

        return tableName;
    }

    /**
     * Extracts aggregate functions from SELECT fields.
     */
    private List<String> extractAggregations(List<String> selectFields) {
        List<String> aggregations = new ArrayList<>();
        for (String field : selectFields) {
            if (AGGREGATE_PATTERN.matcher(field).find()) {
                aggregations.add(field);
            }
        }
        return aggregations;
    }
}