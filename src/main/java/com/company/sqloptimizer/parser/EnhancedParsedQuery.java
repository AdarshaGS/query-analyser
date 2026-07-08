package com.company.sqloptimizer.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;

/**
 * Enhanced Parsed Query that extends the base ParsedQuery with additional fields.
 */
public class EnhancedParsedQuery extends ParsedQuery {

    private final List<String> groupByClauses;
    private final List<String> orderByClauses;
    private final List<String> subqueries;

    public EnhancedParsedQuery(String originalSql, Set<String> tables, List<String> joins,
                               List<String> whereClauses, List<String> aggregations, List<String> selectFields,
                               List<String> groupByClauses, List<String> orderByClauses, List<String> subqueries) {
        super(originalSql, tables, joins, whereClauses, aggregations, selectFields);
        this.groupByClauses = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(groupByClauses, Collections.emptyList())));
        this.orderByClauses = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(orderByClauses, Collections.emptyList())));
        this.subqueries = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNullElse(subqueries, Collections.emptyList())));
    }

    public List<String> getGroupByClauses() {
        return groupByClauses;
    }

    public List<String> getOrderByClauses() {
        return orderByClauses;
    }

    public List<String> getSubqueries() {
        return subqueries;
    }
}