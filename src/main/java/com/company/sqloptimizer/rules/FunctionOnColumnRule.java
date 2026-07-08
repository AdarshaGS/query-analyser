package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect functions applied to indexed columns.
 */
@Component
public class FunctionOnColumnRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check WHERE clauses for functions on columns
        boolean hasFunctionOnColumn = context.getParsedQuery().getWhereClauses().stream()
                .anyMatch(clause -> {
                    String upperClause = clause.toUpperCase();
                    // Common SQL functions that prevent index usage when applied to columns
                    return upperClause.matches(".*\\b(UPPER|LOWER|TRIM|LTRIM|RTRIM|SUBSTRING|CONCAT|DATE|YEAR|MONTH|DAY)\\s*\\([^)]*\\).*");
                });

        if (hasFunctionOnColumn) {
            return RuleResult.of(
                    true,
                    "Function applied to column in WHERE clause. Prevents index usage.",
                    Severity.HIGH,
                    true,
                    "Avoid applying functions to indexed columns in WHERE clauses. " +
                    "Move the function to the other side of the comparison if possible, or consider using function-based indexes..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}