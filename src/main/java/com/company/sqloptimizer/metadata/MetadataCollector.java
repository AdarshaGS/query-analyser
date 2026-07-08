package com.company.sqloptimizer.metadata;

import com.company.sqloptimizer.entity.*;
import com.company.sqloptimizer.analyzer.SchemaInfo;

import java.util.*;
import java.util.Map;

/**
 * Interface for collecting metadata from the database.
 */
public interface MetadataCollector {

    /**
     * Collects metadata for the specified tables.
     *
     * @param tableNames set of table names to collect metadata for
     * @return SchemaInfo object containing all collected metadata
     */
    SchemaInfo collectMetadata(Set<String> tableNames);

    /**
     * Collects column information for a specific table.
     *
     * @param tableName name of the table
     * @param schemaName name of the schema (can be null)
     * @return list of ColumnInfo objects
     */
    List<ColumnInfo> collectColumnInfo(String tableName, String schemaName);

    /**
     * Collects index information for a specific table.
     *
     * @param tableName name of the table
     * @param schemaName name of the schema (can be null)
     * @return list of IndexInfo objects
     */
    List<IndexInfo> collectIndexInfo(String tableName, String schemaName);

    /**
     * Gets the approximate row count for a table.
     *
     * @param tableName name of the table
     * @param schemaName name of the schema (can be null)
     * @return approximate row count
     */
    long getRowCount(String tableName, String schemaName);

    /**
     * Gets statistics for the specified tables.
     *
     * @param tableNames set of table names to get statistics for
     * @return map of table name to statistics (row count, index count, etc.)
     */
    Map<String, Object> getTableStatistics(Set<String> tableNames);
}