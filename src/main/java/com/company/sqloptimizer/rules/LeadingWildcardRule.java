package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect leading wildcards in LIKE patterns.
 */
@Component
public class LeadingWildcardRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check WHERE clauses for LIKE patterns with leading wildcards
        boolean hasLeadingWildcard = context.getParsedQuery().getWhereClauses().stream()
                .anyMatch(clause -> {
                    String upperClause = clause.toUpperCase();
                    // Look for LIKE '%...' patterns (not case sensitive)
                    return upperClause.contains("LIKE '%") ||
                           upperClause.contains("LIKE '_%");
                });

        if (hasLeadingWildcard) {
            return RuleResult.of(
                    true,
                    "Leading wildcard detected in LIKE pattern. Prevents index usage.",
                    Severity.HIGH,
                    true,
                    "Avoid leading wildcards (%) in LIKE patterns as they prevent index usage. " +
                    "Consider using full-text search or reversing the search string if applicable..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}