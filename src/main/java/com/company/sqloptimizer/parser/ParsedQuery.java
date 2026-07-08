package com.company.sqloptimizer.parser;

import java.util.List;
import java.util.Set;

/**
 * Represents the parsed SQL query.
 */
public class ParsedQuery {

    private final String originalSql;
    private final Set<String> tables;
    private final List<String> joins;
    private final List<String> whereClauses;
    private final List<String> aggregations;
    private final List<String> selectFields;

    public ParsedQuery(String originalSql, Set<String> tables, List<String> joins,
                       List<String> whereClauses, List<String> aggregations, List<String> selectFields) {
        this.originalSql = originalSql;
        this.tables = tables;
        this.joins = joins;
        this.whereClauses = whereClauses;
        this.aggregations = aggregations;
        this.selectFields = selectFields;
    }

    public String getOriginalSql() {
        return originalSql;
    }

    public Set<String> getTables() {
        return tables;
    }

    public List<String> getJoins() {
        return joins;
    }

    public List<String> getWhereClauses() {
        return whereClauses;
    }

    public List<String> getAggregations() {
        return aggregations;
    }

    public List<String> getSelectFields() {
        return selectFields;
    }

}
