package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;
import com.company.sqloptimizer.dto.Severity;
import org.springframework.stereotype.Component;

/**
 * Rule to detect full table scans from EXPLAIN output.
 */
@Component
public class FullTableScanRule implements SqlRule {

    @Override
    public RuleResult evaluate(QueryContext context) {
        if (!context.hasExplainAnalysisResult()) {
            return RuleResult.of(false, null, null);
        }

        if (context.getExplainAnalysisResult().isFullScanDetected()) {
            return RuleResult.of(
                    true,
                    "Full table scan detected in EXPLAIN output. Consider adding appropriate indexes.",
                    Severity.HIGH,
                    true,
                    "Review the WHERE clause and JOIN conditions to ensure they can utilize existing indexes. " +
                    "Consider adding indexes on columns used in WHERE, JOIN, ORDER BY, and GROUP BY clauses..."
            );
        }

        return RuleResult.of(false, null, null);
    }

}