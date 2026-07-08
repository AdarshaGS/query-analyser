package com.company.sqloptimizer.analyzer;

import com.company.sqloptimizer.dto.ExplainAnalysisResult;
import com.company.sqloptimizer.parser.ParsedQuery;

/**
 * Context for evaluating SQL rules.
 * Contains the parsed query, the schema information, and optionally the EXPLAIN analysis result.
 */
public class QueryContext {

    private final ParsedQuery parsedQuery;
    private final SchemaInfo schemaInfo;
    private final ExplainAnalysisResult explainAnalysisResult;

    public QueryContext(ParsedQuery parsedQuery, SchemaInfo schemaInfo, ExplainAnalysisResult explainAnalysisResult) {
        this.parsedQuery = parsedQuery;
        this.schemaInfo = schemaInfo;
        this.explainAnalysisResult = explainAnalysisResult;
    }

    /**
     * Constructor for when EXPLAIN analysis is not available.
     */
    public QueryContext(ParsedQuery parsedQuery, SchemaInfo schemaInfo) {
        this(parsedQuery, schemaInfo, null);
    }
    

    public ParsedQuery getParsedQuery() {
        return parsedQuery;
    }

    public SchemaInfo getSchemaInfo() {
        return schemaInfo;
    }

    public ExplainAnalysisResult getExplainAnalysisResult() {
        return explainAnalysisResult;
    }

    public boolean hasExplainAnalysisResult() {
        return explainAnalysisResult != null;
    }
}