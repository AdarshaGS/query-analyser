package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect OR conditions that may prevent index usage.
 */
@Component
public class OrConditionRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check WHERE clauses for OR conditions
        boolean hasOr = context.getParsedQuery().getWhereClauses().stream()
                .anyMatch(clause -> clause.toUpperCase().contains(" OR "));

        if (hasOr) {
            return RuleResult.of(
                    true,
                    "OR condition detected in WHERE clause. May prevent index usage.",
                    Severity.MEDIUM,
                    true,
                    "Consider rewriting OR conditions as UNION queries or using IN clauses where appropriate. " +
                    "Ensure columns used in OR conditions are indexed..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}