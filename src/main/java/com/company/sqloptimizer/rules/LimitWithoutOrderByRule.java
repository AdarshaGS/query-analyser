package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect LIMIT without ORDER BY.
 */
@Component
public class LimitWithoutOrderByRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check if LIMIT is used without ORDER BY
        // This is a simplified check - we'd need to parse the full SQL to be accurate
        String originalSql = context.getParsedQuery().getOriginalSql().toUpperCase();

        boolean hasLimit = originalSql.contains("LIMIT");
        boolean hasOrderBy = originalSql.contains("ORDER BY");

        if (hasLimit && !hasOrderBy) {
            return RuleResult.of(
                    true,
                    "LIMIT used without ORDER BY. Results are non-deterministic.",
                    Severity.MEDIUM,
                    true,
                    "Always use ORDER BY with LIMIT to ensure deterministic results. " +
                    "Without ORDER BY, the rows returned are arbitrary and may change between executions..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}