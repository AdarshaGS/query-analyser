package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect joins without apparent indexes.
 */
@Component
public class NoIndexJoinRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // This is a simplified rule - in reality, we'd need to check the schema
        // to see if join columns are indexed
        // For now, we'll flag all joins as potentially missing indexes
        boolean hasJoins = !context.getParsedQuery().getJoins().isEmpty();

        if (hasJoins) {
            return RuleResult.of(
                    true,
                    "Join detected. Verify that join columns are properly indexed.",
                    Severity.MEDIUM,
                    true,
                    "Ensure that columns used in JOIN conditions are indexed. " +
                    "Missing indexes on join columns can lead to full table scans and poor performance..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}