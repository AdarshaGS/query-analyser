package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect UNION (without ALL) which removes duplicates unnecessarily.
 */
@Component
public class UnionVsUnionAllRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check if the SQL contains UNION (but not UNION ALL)
        String originalSql = context.getParsedQuery().getOriginalSql().toUpperCase();

        // Check for UNION but not UNION ALL
        boolean hasUnion = originalSql.contains("UNION");
        boolean hasUnionAll = originalSql.contains("UNION ALL");

        if (hasUnion && !hasUnionAll) {
            return RuleResult.of(
                    true,
                    "UNION detected without ALL. This removes duplicates which may be unnecessary.",
                    Severity.MEDIUM,
                    true,
                    "Use UNION ALL instead of UNION if you don't need to remove duplicates. " +
                    "UNION ALL is significantly faster as it doesn't perform the duplicate removal step..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}