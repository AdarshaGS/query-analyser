package com.company.sqloptimizer.analyzer;

import com.company.sqloptimizer.entity.TableInfo;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents the database schema information for SQL analysis.
 */
public class SchemaInfo {

    private final Map<String, TableInfo> tablesByName;

    public SchemaInfo(Set<TableInfo> tables) {
        this.tablesByName = new HashMap<>();
        for (TableInfo table : tables) {
            String key = table.getSchemaName() != null ?
                    table.getSchemaName() + "." + table.getTableName() :
                    table.getTableName();
            this.tablesByName.put(key.toUpperCase(), table);
        }
    }

    public TableInfo getTable(String tableName) {
        return tablesByName.get(tableName.toUpperCase());
    }

    public TableInfo getTable(String schemaName, String tableName) {
        String key = (schemaName != null ? schemaName + "." : "") + tableName;
        return tablesByName.get(key.toUpperCase());
    }

    public boolean containsTable(String tableName) {
        return tablesByName.containsKey(tableName.toUpperCase());
    }

    public boolean containsTable(String schemaName, String tableName) {
        String key = (schemaName != null ? schemaName + "." : "") + tableName;
        return tablesByName.containsKey(key.toUpperCase());
    }

    public Set<TableInfo> getAllTables() {
        return Set.copyOf(tablesByName.values());
    }

    public int getTableCount() {
        return tablesByName.size();
    }
}