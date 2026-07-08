package com.company.sqloptimizer.rewrite.api;

import com.company.sqloptimizer.ai.PromptBuilder;
import com.company.sqloptimizer.analyzer.SchemaInfo;
import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Builds prompts for the SQL Rewrite AI model.
 */
@Service
public class RewritePromptBuilder {

    private final ObjectMapper objectMapper;

    public RewritePromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Builds the system prompt.
     */
    public String buildSystemPrompt() {
        return """
                You are SQL Rewrite AI, an expert SQL query rewrite engine.

                Your sole responsibility is to rewrite SQL queries by implementing exactly one optimization recommendation supplied in the input.

                Your output is consumed directly by a backend service.

                General Rules

                - Perform all reasoning internally.
                - Never explain your reasoning.
                - Never analyze the SQL again.
                - Never invent optimizations.
                - Trust the supplied analysis and selected recommendation.
                - If the supplied recommendation cannot be safely implemented, return the original SQL unchanged.
                - Never return markdown.
                - Never return code fences.
                - Never return conversational text.
                - Never return anything outside the required JSON object.

                Rewrite Rules

                - Implement only the selected recommendation.
                - Do not perform unrelated optimizations.
                - Preserve business logic.
                - Preserve result set, aliases, column order, JOIN semantics,
                  NULL behaviour, GROUP BY, HAVING, ORDER BY,
                  aggregate behaviour and row count.

                Allowed transformations include:
                - Correlated subquery → Derived table
                - Correlated subquery → CTE
                - Predicate pushdown
                - EXISTS / IN rewrite
                - Aggregate pre-computation
                - Expression simplification
                - Derived table extraction
                - CTE extraction

                Always return exactly one JSON object:

                {
                  "optimizedQuery": ""
                }

                Return nothing except this JSON.
                """;
    }

    /**
     * Builds the user prompt.
     */
    public String buildUserPrompt(
            String sql,
            ExplainAnalysisResult executionPlan,
            SchemaInfo schemaMetadata,
            Map<String, Object> tableStatistics,
            SqlAnalysisResponse analysisResult) {

        String executionPlanText = PromptBuilder.formatExecutionPlan(executionPlan);
        String schemaText = PromptBuilder.formatSchema(schemaMetadata);
        String tableStatsText = PromptBuilder.formatTableStatistics(tableStatistics);

        String analysisJson = toJson(analysisResult);

        return """
                Rewrite the SQL query by implementing exactly one optimization recommendation.

                Do not perform additional optimizations.

                If the recommendation cannot be implemented safely, return the original SQL.

                ============================
                INPUT
                ============================

                Original SQL

                {{SQL_QUERY}}

                ----------------------------

                Execution Plan

                {{EXECUTION_PLAN}}

                ----------------------------

                Database Schema

                {{SCHEMA}}

                ----------------------------

                Table Statistics

                {{TABLE_STATS}}

                ----------------------------

                Analysis

                {{ANALYSIS}}

                ============================
                OUTPUT
                ============================

                Return exactly one JSON object.

                {
                  "optimizedQuery": ""
                }
                """
                .replace("{{SQL_QUERY}}", safe(sql))
                .replace("{{EXECUTION_PLAN}}", safe(executionPlanText))
                .replace("{{SCHEMA}}", safe(schemaText))
                .replace("{{TABLE_STATS}}", safe(tableStatsText))
                .replace("{{ANALYSIS}}", analysisJson);
    }

    /**
     * Converts an object into pretty JSON.
     */
    private String toJson(Object object) {
        try {
            if (object == null) {
                return "{}";
            }
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize prompt data.", e);
        }
    }

    /**
     * Returns an empty string for null values.
     */
    private String safe(String value) {
        return value == null ? "" : value;
    }
}