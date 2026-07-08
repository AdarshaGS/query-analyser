package com.company.sqloptimizer.rules;

import org.springframework.stereotype.Component;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;

/**
 * Rule to detect Cartesian products (joins without WHERE conditions).
 */
@Component
public class CartesianProductRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check if we have multiple tables but no WHERE clause that could be a join condition
        int tableCount = context.getParsedQuery().getTables().size();
        int joinCount = context.getParsedQuery().getJoins().size();

        // If we have more than one table but no joins, it's a Cartesian product
        // Or if we have joins but they might not be properly conditioned (simplified check)
        boolean isCartesian = tableCount > 1 && joinCount == 0;

        // Another check: if we have WHERE clauses but none look like join conditions
        if (!isCartesian && tableCount > 1) {
            boolean hasJoinConditionInWhere = context.getParsedQuery().getWhereClauses().stream()
                    .anyMatch(clause -> {
                        String upperClause = clause.toUpperCase();
                        // Look for common join patterns in WHERE clauses
                        return upperClause.contains("=") &&
                               (upperClause.contains(".") ||
                                upperClause.contains(" JOIN ") ||
                                upperClause.contains(" INNER JOIN ") ||
                                upperClause.contains(" LEFT JOIN ") ||
                                upperClause.contains(" RIGHT JOIN "));
                    });
            isCartesian = !hasJoinConditionInWhere;
        }

        if (isCartesian) {
            return RuleResult.of(
                    true,
                    "Potential Cartesian product detected. Missing join conditions between tables.",
                    Severity.CRITICAL,
                    true,
                    "Review your query for missing join conditions. " +
                    "A Cartesian product combines every row from one table with every row from another, " +
                    "which can result in extremely large result sets and poor performance..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}