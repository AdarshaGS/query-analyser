package com.company.sqloptimizer.parser;

import com.company.sqloptimizer.entity.ColumnInfo;
import com.company.sqloptimizer.entity.IndexColumnInfo;
import com.company.sqloptimizer.entity.IndexInfo;
import com.company.sqloptimizer.entity.TableInfo;

import java.util.List;

/**
 * Result of parsing a DDL statement.
 */
public class ParseResult {

    private TableInfo table;
    private List<ColumnInfo> columns;
    private IndexInfo index;
    private List<IndexColumnInfo> indexColumns;

    public ParseResult(TableInfo table, List<ColumnInfo> columns) {
        this.table = table;
        this.columns = columns;
    }

    public ParseResult(IndexInfo index, List<IndexColumnInfo> indexColumns) {
        this.index = index;
        this.indexColumns = indexColumns;
    }

    public TableInfo getTable() {
        return table;
    }

    public List<ColumnInfo> getColumns() {
        return columns;
    }

    public IndexInfo getIndex() {
        return index;
    }

    public List<IndexColumnInfo> getIndexColumns() {
        return indexColumns;
    }

    public boolean isCreateTable() {
        return table != null;
    }

    public boolean isCreateIndex() {
        return index != null;
    }
}
