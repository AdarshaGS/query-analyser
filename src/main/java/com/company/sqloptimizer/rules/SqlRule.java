package com.company.sqloptimizer.rules;

import com.company.sqloptimizer.analyzer.QueryContext;

/**
 * Interface for a SQL optimization rule.
 */
public interface SqlRule {

    /**
     * Evaluates the rule against the given query context.
     * @param context the query context
     * @return the result of the rule evaluation
     */
    RuleResult evaluate(QueryContext context);

}
