package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * RuleResult evaluate(QueryContext context);
 */
@Component
public class NestedSubqueryRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check for subqueries in WHERE clause (simple pattern matching)
        boolean hasNestedSubqueryInWhere = context.getParsedQuery().getWhereClauses().stream()
                .anyMatch(clause -> {
                    String upperClause = clause.toUpperCase();
                    // Look for SELECT inside parentheses which might indicate a subquery
                    return upperClause.contains("(SELECT") || upperClause.contains("EXISTS (SELECT");
                });

        // Check for subqueries in SELECT clause
        boolean hasNestedSubqueryInSelect = context.getParsedQuery().getSelectFields().stream()
                .anyMatch(selectField -> {
                    String upperField = selectField.toUpperCase();
                    return upperField.contains("(SELECT");
                });

        if (hasNestedSubqueryInWhere || hasNestedSubqueryInSelect) {
            return RuleResult.of(
                    true,
                    "Nested subquery detected. Consider rewriting as JOIN for better performance.",
                    Severity.MEDIUM,
                    true,
                    "Nested subqueries can be inefficient. Consider rewriting as JOIN or using CTE (Common Table Expression) for better readability and performance..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}