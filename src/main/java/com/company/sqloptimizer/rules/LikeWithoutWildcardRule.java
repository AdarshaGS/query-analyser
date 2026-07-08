package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect LIKE patterns without wildcards (which could use = instead).
 */
@Component
public class LikeWithoutWildcardRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check WHERE clauses for LIKE patterns without wildcards
        boolean hasLikeWithoutWildcard = context.getParsedQuery().getWhereClauses().stream()
                .anyMatch(clause -> {
                    String upperClause = clause.toUpperCase();
                    // Look for LIKE 'value' patterns (no % or _)
                    return upperClause.matches(".*LIKE\\s*['\"][^'%]*['\"]");
                });

        if (hasLikeWithoutWildcard) {
            return RuleResult.of(
                    true,
                    "LIKE pattern without wildcards detected. Consider using = instead.",
                    Severity.LOW,
                    true,
                    "LIKE patterns without wildcards (%) or (_) are equivalent to = but slower. " +
                    "Replace LIKE 'value' with = 'value' for better performance..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}