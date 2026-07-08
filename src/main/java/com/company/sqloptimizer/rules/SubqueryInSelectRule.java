package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect subqueries in the SELECT clause.
 */
@Component
public class SubqueryInSelectRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check for subqueries in SELECT clause
        boolean hasSubqueryInSelect = context.getParsedQuery().getSelectFields().stream()
                .anyMatch(selectField -> {
                    String upperField = selectField.toUpperCase();
                    return upperField.contains("(SELECT");
                });

        if (hasSubqueryInSelect) {
            return RuleResult.of(
                    true,
                    "Subquery detected in SELECT clause. Consider rewriting as JOIN for better performance.",
                    Severity.MEDIUM,
                    true,
                    "Subqueries in SELECT clause can cause performance issues as they execute for each row. " +
                    "Consider rewriting as JOIN or using window functions if applicable..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}