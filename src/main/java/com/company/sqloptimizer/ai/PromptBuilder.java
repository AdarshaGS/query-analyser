package com.company.sqloptimizer.ai;

import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.IssueDto;
import com.company.sqloptimizer.dto.RecommendationDto;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.company.sqloptimizer.entity.TableInfo;
import com.company.sqloptimizer.util.SqlMinifier;
import com.company.sqloptimizer.analyzer.SchemaInfo;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for building prompts for AI analysis.
 */
@Component
public class PromptBuilder {

    /**
     * Builds the system prompt for the AI model.
     *
     * @return the system prompt
     */
    public String buildSystemPrompt() {
        return """
                You are SQL Optimizer AI, an expert MySQL query performance analysis engine.

                Your purpose is to analyze SQL queries using only the supplied SQL query, execution plan, schema metadata, table statistics, and index metadata.

                You are NOT a conversational assistant.

                You are NOT an educator.

                You are NOT allowed to explain your reasoning.

                Your output is consumed directly by a backend Java service.

                Therefore:

                - Never output reasoning.
                - Never output thoughts.
                - Never output analysis steps.
                - Never output markdown.
                - Never output code fences.
                - Never output introductory text.
                - Never output concluding text.
                - Never output anything outside the requested JSON.

                Perform all reasoning internally.

                Return only the final result.

                Never invent:

                - indexes
                - constraints
                - statistics
                - relationships
                - cardinality
                - execution plan details
                - optimizer behavior

                If evidence is unavailable, explicitly state that the evidence is insufficient.

                Never modify the business logic of the SQL.

                Never recommend optimizations that change query results.

                Always optimize while preserving semantics.

                Your analysis must be evidence-driven.

                If a recommendation cannot be justified from the supplied metadata, do not make it.

                Your entire response must be a single valid JSON object.
                """;
    }

    /**
     * Builds the user prompt for the AI model.
     *
     * @param sqlAnalysis     the SQL analysis results
     * @param explainAnalysis the EXPLAIN analysis results
     * @param schemaMetadata  the schema metadata
     * @param tableStatistics the table statistics
     * @param sqlQuery        the original SQL query
     * @return the user prompt
     */
    public String buildUserPrompt(SqlAnalysisResponse sqlAnalysis,
            ExplainAnalysisResult explainAnalysis,
            SchemaInfo schemaMetadata,
            Map<String, Object> tableStatistics,
            String sqlQuery) {
        String executionPlanText = formatExecutionPlan(explainAnalysis);
        String schemaText = formatSchema(schemaMetadata);
        String tableStatsText = formatTableStatistics(tableStatistics);

        String prompt = """
                Analyze the following SQL query.

                ## INPUT

                ### SQL Query

                {{SQL_QUERY}}

                ---

                ### EXPLAIN ANALYZE

                {{EXECUTION_PLAN}}

                ---

                ### Database Schema

                {{SCHEMA}}

                ---

                ### Table Statistics

                {{TABLE_STATS}}

                ---

                ### Index Metadata

                {{INDEX_METADATA}}

                ---

                ### Database Version

                {{DATABASE_VERSION}}

                ------------------------------------------------------------

                ## ANALYSIS REQUIREMENTS

                Perform a complete SQL performance review.

                ------------------------------------------------------------

                ## RULES

                Only report issues that are supported by the supplied SQL, execution plan or metadata.

                Never assume missing indexes.

                Never assume table relationships.

                Never invent execution plan information.

                If evidence is insufficient, mention that in comments.

                Do not report false positives.

                Each issue must include:

                - issue
                - severity
                - evidence

                Each recommendation must directly correspond to one issue.

                If possible include a rewritten SQL snippet.

                If rewriting would change semantics, return null.

                ------------------------------------------------------------

                ## COMPLEXITY SCORE

                Compute a score from 0-100.

                Scoring should consider:

                - joins
                - subqueries
                - derived tables
                - execution plan
                - grouping
                - sorting
                - row counts
                - optimizer complexity
                - maintainability

                ------------------------------------------------------------

                ## OUTPUT FORMAT

                Return EXACTLY one JSON object.

                {
                  "complexity_score": "0/100",
                  "comments": "",
                  "issues": [
                    {
                      "issue": "",
                      "severity": "High"
                    }
                  ],
                  "recommendations": [
                    {
                    }
                  ]
                }

                Return ONLY JSON.

                Do not output any explanation.
                Do not output reasoning.
                Do not output markdown.
                Do not output code fences.
                Do not output introductory text.
                Do not output concluding text.
                Do not output anything outside the requested JSON.

                Perform all reasoning internally.

                Return only the final result.

                Never invent:

                - indexes
                - constraints
                - statistics
                - relationships
                - cardinality
                - execution plan details
                - optimizer behavior

                If evidence is unavailable, explicitly state that the evidence is insufficient.

                Never modify the business logic of the SQL.

                Never recommend optimizations that change query results.

                Always optimize while preserving semantics.

                Your analysis must be evidence-driven.

                If a recommendation cannot be justified from the supplied metadata, do not make it.

                Your entire response must be a single valid JSON object.
                """.replace("{{SQL_QUERY}}", sqlQuery != null ? sqlQuery : "")
                .replace("{{EXECUTION_PLAN}}", executionPlanText != null ? executionPlanText : "")
                .replace("{{SCHEMA}}", schemaText != null ? schemaText : "")
                .replace("{{TABLE_STATS}}", tableStatsText != null ? tableStatsText : "")
                .replace("{{INDEX_METADATA}}", "Index metadata not available in current implementation")
                .replace("{{DATABASE_VERSION}}", "Database version not available in current implementation");

        return prompt;
    }

    /**
     * Formats the ExplainAnalysisResult into a readable string.
     */
    public static String formatExecutionPlan(ExplainAnalysisResult explainAnalysis) {
        if (explainAnalysis == null) {
            return "No execution plan available.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Full Table Scan: ").append(explainAnalysis.isFullScanDetected()).append("\n");
        sb.append("Temporary Table: ").append(explainAnalysis.isTempTableDetected()).append("\n");
        sb.append("File Sort: ").append(explainAnalysis.isFileSortDetected()).append("\n");
        sb.append("Nested Loop: ").append(explainAnalysis.isNestedLoopDetected()).append("\n");
        sb.append("Row Explosion: ").append(explainAnalysis.isRowExplosionDetected()).append("\n");
        // Optionally add issues and recommendations from explainAnalysis if needed
        return sb.toString();
    }

    /**
     * Formats the SchemaInfo into a readable string.
     */
    public static String formatSchema(SchemaInfo schemaMetadata) {
        if (schemaMetadata == null) {
            return "No schema information available.";
        }
        Set<TableInfo> tables = schemaMetadata.getAllTables();
        if (tables.isEmpty()) {
            return "No tables in schema.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Number of tables: ").append(tables.size()).append("\n");
        sb.append("Tables: ");
        List<String> tableNames = tables.stream()
                .map(table -> {
                    // Try to get table name - assuming TableInfo has a getName() method
                    try {
                        java.lang.reflect.Method nameMethod = table.getClass().getMethod("getName");
                        return (String) nameMethod.invoke(table);
                    } catch (Exception e) {
                        // Fallback to toString if getName doesn't exist
                        return table.toString();
                    }
                })
                .collect(Collectors.toList());
        sb.append(String.join(", ", tableNames));
        return sb.toString();
    }

    /**
     * Formats the table statistics map into a readable string.
     */
    public static String formatTableStatistics(Map<String, Object> tableStatistics) {
        if (tableStatistics == null || tableStatistics.isEmpty()) {
            return "No table statistics available.";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : tableStatistics.entrySet()) {
            String tableName = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = (Map<String, Object>) value;
                Object rowCount = stats.get("row_count");
                Object indexCount = stats.get("index_count");
                sb.append("  ").append(tableName).append(": {row_count=")
                        .append(rowCount != null ? rowCount : "?")
                        .append(", index_count=")
                        .append(indexCount != null ? indexCount : "?")
                        .append("}\\n");
            } else {
                sb.append("  ").append(tableName).append(": ").append(value).append("\\n");
            }
        }
        return sb.toString().trim();
    }

    /**
     * Formats the SqlAnalysisResponse into a readable string.
     */
    private static String formatSqlAnalysisResponse(SqlAnalysisResponse sqlAnalysis) {

        if (sqlAnalysis == null) {
            return "No SQL analysis available.";
        }

        StringBuilder sb = new StringBuilder();

        sb.append("Score: ").append(sqlAnalysis.getComplexityScore()).append("/100\n");
        sb.append("Detected tables: ").append(sqlAnalysis.getDetectedTables()).append("\n");
        sb.append("Joins: ").append(sqlAnalysis.getJoins()).append("\n");
        sb.append("Where clauses: ").append(sqlAnalysis.getWhereClauses()).append("\n");
        sb.append("Group by clauses: ").append(sqlAnalysis.getGroupByClauses()).append("\n");
        sb.append("Order by clauses: ").append(sqlAnalysis.getOrderByClauses()).append("\n");
        sb.append("Subqueries: ").append(sqlAnalysis.getSubqueries()).append("\n");
        sb.append("Aggregations: ").append(sqlAnalysis.getAggregations()).append("\n");

        sb.append("\nIssues:\n");

        if (sqlAnalysis.getIssues() == null || sqlAnalysis.getIssues().isEmpty()) {
            sb.append("None\n");
        } else {
            int i = 1;
            for (IssueDto issue : sqlAnalysis.getIssues()) {
                sb.append(i++)
                        .append(". ")
                        .append(issue.getIssue());

                if (issue.getSeverity() != null) {
                    sb.append(" [")
                            .append(issue.getSeverity())
                            .append("]");
                }

                sb.append("\n");
            }
        }

        sb.append("\nRecommendations:\n");

        if (sqlAnalysis.getRecommendations() == null || sqlAnalysis.getRecommendations().isEmpty()) {
            sb.append("None\n");
        } else {
            int i = 1;
            for (RecommendationDto recommendation : sqlAnalysis.getRecommendations()) {
                sb.append(i++)
                        .append(". ")
                        .append(recommendation.getMessage());

                sb.append("\n");
            }
        }

        sb.append("\nEstimated complexity: ")
                .append(sqlAnalysis.getEstimatedComplexity())
                .append("\n");

        sb.append("Severity: ")
                .append(sqlAnalysis.getSeverity())
                .append("\n");

        sb.append("Select fields: ")
                .append(sqlAnalysis.getSelectFields())
                .append("\n");

        return sb.toString();
    }

    /**
     * Builds the system prompt for the AI model.
     *
     * @return the system prompt
     */
    public String buildSystemPromptForRewrite() {
        return """
                You are a deterministic MySQL query rewrite engine.

                Your task is to rewrite SQL for better performance while preserving identical semantics.

                Requirements:
                - Preserve business logic.
                - Preserve returned rows and values.
                - Do not invent schema objects.
                - Use only supplied metadata.
                - Produce valid MySQL SQL.

                No explanations.
                No reasoning.
                No markdown.
                                """;
    }

    public String buildUserPromptForRewrite(SqlAnalysisResponse sqlAnalysis,
            ExplainAnalysisResult explainAnalysis,
            SchemaInfo schemaMetadata,
            Map<String, Object> tableStatistics,
            String sqlQuery) {

        String executionPlanText = formatExecutionPlan(explainAnalysis);
        String schemaText = formatSchema(schemaMetadata);
        String tableStatsText = formatTableStatistics(tableStatistics);
        String sqlAnalysisText = formatSqlAnalysisResponse(sqlAnalysis);

        return """
                                <task>
                                Rewrite the supplied MySQL query to improve execution performance.
                                Preserve identical semantics.
                                </task>

                                <priority>
                                1. SQL Analysis
                                2. EXPLAIN Analysis
                                3. Schema Metadata
                                4. Table Statistics
                                </priority>

                                <input_sql>
                                {{SQL_QUERY}}
                                </input_sql>

                                <sql_analysis>
                                {{SQL_ANALYSIS}}
                                </sql_analysis>

                                <execution_plan>
                                {{EXECUTION_PLAN}}
                                </execution_plan>

                                <schema>
                                {{SCHEMA}}
                                </schema>

                                <table_statistics>
                                {{TABLE_STATS}}
                                </table_statistics>

                                <constraints>

                                - Preserve business logic.
                                - Preserve returned rows.
                                - Preserve returned values.
                                - Preserve NULL behavior.
                                - Preserve JOIN semantics.
                                - Preserve WHERE semantics.
                                - Preserve GROUP BY semantics.
                                - Preserve HAVING semantics.
                                - Preserve ORDER BY semantics.
                                - Preserve LIMIT semantics.

                                - Do not invent tables.
                                - Do not invent columns.
                                - Do not invent indexes.
                                - Do not invent relationships.
                                - Do not invent optimizer hints.

                                - Apply only optimizations supported by the supplied metadata.

                                - Prefer:
                                  * Removing correlated scalar subqueries.
                                  * Removing duplicate computations.
                                  * Converting repeated lookups into derived tables or CTEs.
                                  * Predicate pushdown.
                                  * Eliminating redundant scans.
                                  * Eliminating unnecessary derived tables.
                                  * Eliminating repeated JSON extraction.
                                  * Conditional aggregation.
                                  * Index-friendly predicates.

                                - If an optimization cannot be proven safe, keep the original SQL.

                                </constraints>

                                <output>

                                Return ONLY the rewritten SQL.

                                Do not return JSON.

                                Do not return markdown.

                                Do not return explanations.

                                Do not return reasoning.

                                The response must begin directly with:

                                Return only the SQL.

                                Start directly with

                                WITH

                                or

                                SELECT

                                Nothing is allowed before the SQL statement.

                                </output>
                                """
                .replace("{{SQL_QUERY}}", sqlQuery != null ? SqlMinifier.minify(sqlQuery) : "")
                .replace("{{SQL_ANALYSIS}}", sqlAnalysisText != null ? sqlAnalysisText : "")
                .replace("{{EXECUTION_PLAN}}", executionPlanText != null ? executionPlanText : "")
                .replace("{{SCHEMA}}", schemaText != null ? schemaText : "")
                .replace("{{TABLE_STATS}}", tableStatsText != null ? tableStatsText : "");
    }
}