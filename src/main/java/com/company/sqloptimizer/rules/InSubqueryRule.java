package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect IN subqueries that may be inefficient.
 */
@Component
public class InSubqueryRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check WHERE clauses for IN subqueries
        boolean hasInSubquery = context.getParsedQuery().getWhereClauses().stream()
                .anyMatch(clause -> {
                    String upperClause = clause.toUpperCase();
                    // Look for IN (SELECT ...) patterns
                    return upperClause.contains("IN (SELECT") ||
                           upperClause.contains("IN (SELECT") ||
                           upperClause.matches(".*\\bIN\\s*\\([^)]*SELECT[^)]*\\).*");
                });

        if (hasInSubquery) {
            return RuleResult.of(
                    true,
                    "IN subquery detected. May be inefficient; consider using EXISTS or JOIN.",
                    Severity.MEDIUM,
                    true,
                    "IN subqueries can be inefficient, especially with large subquery results. " +
                    "Consider rewriting as EXISTS or JOIN for better performance..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}