package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect unnecessary DISTINCT usage.
 */
@Component
public class UnnecessaryDistinctRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check if SELECT DISTINCT is used when it's not necessary
        // This is a simplified check - in reality, we'd need to check for duplicates
        String selectFields = context.getParsedQuery().getSelectFields().stream()
                .findFirst()
                .orElse("");

        boolean hasDistinct = selectFields.toUpperCase().startsWith("DISTINCT");

        // For now, we'll flag all DISTINCT usage as potentially unnecessary
        // A more sophisticated implementation would check if the column(s) are already unique
        if (hasDistinct) {
            return RuleResult.of(
                    true,
                    "DISTINCT keyword detected. May be unnecessary if columns are already unique.",
                    Severity.LOW,
                    true,
                    "Review if DISTINCT is actually needed. If the columns in SELECT are already unique (e.g., primary key), " +
                    "removing DISTINCT can improve performance by eliminating the duplicate removal step..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}