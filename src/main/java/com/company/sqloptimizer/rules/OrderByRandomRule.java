package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect ORDER BY RAND() or similar random functions.
 */
@Component
public class OrderByRandomRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        // Check if ORDER BY contains random functions
        String originalSql = context.getParsedQuery().getOriginalSql().toUpperCase();

        boolean hasOrderByRandom = originalSql.contains("ORDER BY RAND") ||
                                   originalSql.contains("ORDER BY RANDOM") ||
                                   originalSql.contains("ORDER BY UUID") ||
                                   originalSql.contains("ORDER BY NEWID");

        if (hasOrderByRandom) {
            return RuleResult.of(
                    true,
                    "ORDER BY with random function detected. This is extremely inefficient.",
                    Severity.CRITICAL,
                    true,
                    "Avoid using ORDER BY RAND() or similar functions as they require sorting the entire result set. " +
                    "Consider alternative approaches for random sampling..."
            );
        }

        return RuleResult.of(false, null, null);
    }
}