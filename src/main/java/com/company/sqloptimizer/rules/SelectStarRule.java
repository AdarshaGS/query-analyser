package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect SELECT * usage.
 */
@Component
public class SelectStarRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check if the SELECT clause contains "*"
        String selectClause = context.getParsedQuery().getSelectFields().stream()
                .findFirst()
                .orElse("");

        if (selectClause.trim().equals("*") || selectClause.trim().equalsIgnoreCase("*")) {
            return RuleResult.of(
                    true,
                    "SELECT * detected. Consider specifying only the columns you need.",
                    Severity.MEDIUM,
                    true,
                    "Replace SELECT * with specific column names to reduce network traffic and improve query performance..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}