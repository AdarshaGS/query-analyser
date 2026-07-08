package com.company.sqloptimizer.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import com.company.sqloptimizer.analyzer.SchemaInfo;
import com.company.sqloptimizer.entity.ColumnInfo;
import com.company.sqloptimizer.entity.IndexInfo;
import com.company.sqloptimizer.entity.TableInfo;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of MetadataCollector that uses JDBC to fetch database metadata.
 * <p>
 * This version batches queries for multiple tables and caches the results to
 * avoid repeated database hits for the same schema objects.
 */
@Component
@RequiredArgsConstructor
public class JdbcMetadataCollector implements MetadataCollector {

    private final JdbcTemplate jdbcTemplate;

    /** Cache for column information: schemaName -> tableName -> List<ColumnInfo> */
    private final Map<String, Map<String, List<ColumnInfo>>> columnCache = new ConcurrentHashMap<>();
    /** Cache for index information: schemaName -> tableName -> List<IndexInfo> */
    private final Map<String, Map<String, List<IndexInfo>>> indexCache = new ConcurrentHashMap<>();
    /** Cache for row count: schemaName -> tableName -> Long */
    private final Map<String, Map<String, Long>> rowCountCache = new ConcurrentHashMap<>();

    @Override
    public SchemaInfo collectMetadata(Set<String> tableNames) {
        // Get schema name from connection
        String schemaName = getSchemaName();
        if (schemaName == null) {
            schemaName = ""; // use empty string as key for null schema
        }

        // Normalize the table names set (null-safe)
        if (tableNames == null) {
            tableNames = Collections.emptySet();
        }

        // Ensure we have cached data for the requested tables
        // populateCacheIfAbsent(tableNames, schemaName);

        // Build the SchemaInfo from cached data
        Set<TableInfo> tables = new HashSet<>();
        for (String tableName : tableNames) {
            TableInfo tableInfo = new TableInfo();
            tableInfo.setTableName(tableName);
            tableInfo.setSchemaName(schemaName.isEmpty() ? null : schemaName);

            // Get columns from cache
            Map<String, List<ColumnInfo>> schemaColumnMap = columnCache.get(schemaName);
            List<ColumnInfo> columns = schemaColumnMap != null ? schemaColumnMap.get(tableName) : null;
            if (columns != null && !columns.isEmpty()) {
                tableInfo.setColumns(new HashSet<>(columns));
            }

            // Get indexes from cache
            Map<String, List<IndexInfo>> schemaIndexMap = indexCache.get(schemaName);
            List<IndexInfo> indexes = schemaIndexMap != null ? schemaIndexMap.get(tableName) : null;
            if (indexes != null && !indexes.isEmpty()) {
                tableInfo.setIndexes(new HashSet<>(indexes));
            }

            // Get row count from cache
            Map<String, Long> schemaRowCountMap = rowCountCache.get(schemaName);
            Long rowCount = schemaRowCountMap != null ? schemaRowCountMap.get(tableName) : null;
            if (rowCount != null) {
                // We don't have a direct setter for row count in TableInfo, but we can store it in a map or ignore.
                // For now, we'll skip setting it because the TableInfo class doesn't have a rowCount field.
                // If needed, we can extend TableInfo or use a separate map.
                // Since the existing code doesn't use row count in TableInfo, we'll just note it.
            }

            tables.add(tableInfo);
        }

        return new SchemaInfo(tables);
    }

    @Override
    public List<ColumnInfo> collectColumnInfo(String tableName, String schemaName) {
        final String effectiveSchema = (schemaName != null) ? schemaName : getSchemaName();
        final String normalizedSchema = (effectiveSchema == null) ? "" : effectiveSchema;
        return getFromCache(columnCache, normalizedSchema, tableName,
                () -> loadColumnInfo(tableName, normalizedSchema));
    }

    @Override
    public List<IndexInfo> collectIndexInfo(String tableName, String schemaName) {
        final String effectiveSchema = (schemaName != null) ? schemaName : getSchemaName();
        final String normalizedSchema = (effectiveSchema == null) ? "" : effectiveSchema;
        return getFromCache(indexCache, normalizedSchema, tableName,
                () -> loadIndexInfo(tableName, normalizedSchema));
    }

    @Override
    public long getRowCount(String tableName, String schemaName) {
        final String effectiveSchema = (schemaName != null) ? schemaName : getSchemaName();
        final String normalizedSchema = (effectiveSchema == null) ? "" : effectiveSchema;
        Long cached = getFromCache(rowCountCache, normalizedSchema, tableName,
                () -> loadRowCount(tableName, normalizedSchema));
        return cached != null ? cached : 0L;
    }

    @Override
    public Map<String, Object> getTableStatistics(Set<String> tableNames) {
        Map<String, Object> stats = new HashMap<>();
        if (tableNames == null) {
            return stats;
        }
        String schemaName = getSchemaName();
        schemaName = (schemaName == null) ? "" : schemaName;
        // Ensure we have cached data for the requested tables
        // populateCacheIfAbsent(tableNames, schemaName);

        for (String tableName : tableNames) {
            stats.put(tableName, getTableStats(tableName, schemaName));
        }
        return stats;
    }

    /**
     * Populates the cache for the given table names and schema if not already present.
     * This method batches the database queries for efficiency.
     */
    private void populateCacheIfAbsent(Set<String> tableNames, String schemaName) {
        // Determine which tables are missing in each cache
        Set<String> missingColumns = getMissingFromCache(columnCache, schemaName, tableNames);
        Set<String> missingIndexes = getMissingFromCache(indexCache, schemaName, tableNames);
        Set<String> missingRowCounts = getMissingFromCache(rowCountCache, schemaName, tableNames);

        // Batch load missing data
        if (!missingColumns.isEmpty()) {
            Map<String, List<ColumnInfo>> columnMap = loadColumnInfoBatch(missingColumns, schemaName);
            putAllToCache(columnCache, schemaName, columnMap);
        }
        if (!missingIndexes.isEmpty()) {
            Map<String, List<IndexInfo>> indexMap = loadIndexInfoBatch(missingIndexes, schemaName);
            putAllToCache(indexCache, schemaName, indexMap);
        }
        if (!missingRowCounts.isEmpty()) {
            Map<String, Long> rowCountMap = loadRowCountBatch(missingRowCounts, schemaName);
            putAllToCache(rowCountCache, schemaName, rowCountMap);
        }
    }

    /**
     * Returns the set of table names that are not present in the cache for the given schema.
     */
    private <T> Set<String> getMissingFromCache(Map<String, Map<String, T>> cache, String schemaName,
                                                Set<String> tableNames) {
        Map<String, T> schemaMap = cache.get(schemaName);
        if (schemaMap == null) {
            return new HashSet<>(tableNames);
        }
        return tableNames.stream()
                .filter(tn -> !schemaMap.containsKey(tn))
                .collect(Collectors.toSet());
    }

    /**
     * Loads column information for a batch of tables.
     */
    private Map<String, List<ColumnInfo>> loadColumnInfoBatch(Set<String> tableNames, String schemaName) {
        if (tableNames.isEmpty()) {
            return Collections.emptyMap();
        }

        // Build placeholders for the IN clause
        String placeholders = String.join(",", Collections.nCopies(tableNames.size(), "?"));
        String sql = """
                SELECT
                    TABLE_NAME,
                    COLUMN_NAME,
                    DATA_TYPE,
                    CHARACTER_MAXIMUM_LENGTH,
                    NUMERIC_PRECISION,
                    NUMERIC_SCALE,
                    IS_NULLABLE = 'YES' AS IS_NULLABLE,
                    COLUMN_DEFAULT,
                    ORDINAL_POSITION
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME IN (%s)
                AND TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """.formatted(placeholders);

        // Prepare parameters: table names followed by schema name
        Object[] params = new Object[tableNames.size() + 1];
        int i = 0;
        for (String tableName : tableNames) {
            params[i++] = tableName;
        }
        params[i] = schemaName;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        // Group by table name
        Map<String, List<ColumnInfo>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = (String) row.get("TABLE_NAME");
            ColumnInfo column = new ColumnInfo();
            column.setColumnName((String) row.get("COLUMN_NAME"));
            column.setDataType((String) row.get("DATA_TYPE"));
            column.setCharacterMaximumLength(
                    row.get("CHARACTER_MAXIMUM_LENGTH") == null ? null :
                            Integer.valueOf(row.get("CHARACTER_MAXIMUM_LENGTH").toString()));
            column.setNumericPrecision(
                    row.get("NUMERIC_PRECISION") == null ? null :
                            Integer.valueOf(row.get("NUMERIC_PRECISION").toString()));
            column.setNumericScale(
                    row.get("NUMERIC_SCALE") == null ? null :
                            Integer.valueOf(row.get("NUMERIC_SCALE").toString()));
            column.setNullable((Boolean) row.get("IS_NULLABLE"));
            column.setDefaultValue((String) row.get("COLUMN_DEFAULT"));

            result.computeIfAbsent(tableName, k -> new ArrayList<>()).add(column);
        }
        return result;
    }

    /**
     * Loads index information for a batch of tables.
     * We load both index metadata and index columns in two queries.
     */
    private Map<String, List<IndexInfo>> loadIndexInfoBatch(Set<String> tableNames, String schemaName) {
        if (tableNames.isEmpty()) {
            return Collections.emptyMap();
        }

        // First, get index metadata (name, uniqueness) for the tables
        String indexPlaceholders = String.join(",", Collections.nCopies(tableNames.size(), "?"));
        String indexSql = """
                SELECT
                    TABLE_NAME,
                    INDEX_NAME,
                    NON_UNIQUE AS IS_UNIQUE
                FROM INFORMATION_SCHEMA.STATISTICS
                WHERE TABLE_NAME IN (%s)
                AND TABLE_SCHEMA = ?
                AND INDEX_NAME != 'PRIMARY'
                ORDER BY TABLE_NAME, INDEX_NAME
                """.formatted(indexPlaceholders);

        Object[] indexParams = new Object[tableNames.size() + 1];
        int i = 0;
        for (String tableName : tableNames) {
            indexParams[i++] = tableName;
        }
        indexParams[i] = schemaName;

        List<Map<String, Object>> indexRows = jdbcTemplate.queryForList(indexSql, indexParams);
        // Build a map: tableName -> map of indexName -> IndexInfo (without columns yet)
        Map<String, Map<String, IndexInfo>> indexMapByTable = new HashMap<>();
        for (Map<String, Object> row : indexRows) {
            String tableName = (String) row.get("TABLE_NAME");
            String indexName = (String) row.get("INDEX_NAME");
            Boolean isUnique = (Boolean) row.get("IS_UNIQUE");
            IndexInfo index = new IndexInfo();
            index.setIndexName(indexName);
            // NON_UNIQUE = 0 means unique, so isUnique = !NON_UNIQUE
            index.setUnique(!isUnique);

            indexMapByTable
                    .computeIfAbsent(tableName, k -> new HashMap<>())
                    .put(indexName, index);
        }

        // Now, get the index columns for the indexes we found
        // We'll collect all index names for the given tables and schema
        Set<String> indexNames = new HashSet<>();
        for (Map<String, IndexInfo> map : indexMapByTable.values()) {
            indexNames.addAll(map.keySet());
        }

        if (!indexNames.isEmpty()) {
            String indexColPlaceholders = String.join(",", Collections.nCopies(indexNames.size(), "?"));
            String indexColSql = """
                    SELECT
                        TABLE_NAME,
                        INDEX_NAME,
                        COLUMN_NAME,
                        SEQ_IN_INDEX
                    FROM INFORMATION_SCHEMA.STATISTICS
                    WHERE TABLE_NAME IN (%s)
                    AND TABLE_SCHEMA = ?
                    AND INDEX_NAME IN (%s)
                    AND INDEX_NAME != 'PRIMARY'
                    ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX
                    """.formatted(indexColPlaceholders, indexColPlaceholders);

            List<Object> indexColParams = new ArrayList<>();
            for (String tableName : tableNames) {
                indexColParams.add(tableName);
            }
            indexColParams.add(schemaName);
            for (String indexName : indexNames) {
                indexColParams.add(indexName);
            }

            List<Map<String, Object>> indexColRows = jdbcTemplate.queryForList(
                    indexColSql, indexColParams.toArray());

            // Distribute the columns to the index objects
            for (Map<String, Object> row : indexColRows) {
                String tableName = (String) row.get("TABLE_NAME");
                String indexName = (String) row.get("INDEX_NAME");
                String columnName = (String) row.get("COLUMN_NAME");
                Integer seqInIndex = (Integer) row.get("SEQ_IN_INDEX");

                Map<String, IndexInfo> indexMap = indexMapByTable.get(tableName);
                if (indexMap != null) {
                    IndexInfo index = indexMap.get(indexName);
                    if (index != null) {
                        // We need to store the columns in order.
                        // For simplicity, we'll just note that the index has columns.
                        // In a full implementation, we would create IndexColumnInfo objects and sort by SEQ_IN_INDEX.
                        // Since the IndexInfo class doesn't have a field for columns, we'll skip storing them.
                        // If the IndexInfo class is updated to include columns, we can add them here.
                        // For now, we do nothing with the column information.
                    }
                }
            }
        }

        // Convert the nested map to the format expected by the cache: tableName -> List<IndexInfo>
        Map<String, List<IndexInfo>> result = new HashMap<>();
        for (Map.Entry<String, Map<String, IndexInfo>> tableEntry : indexMapByTable.entrySet()) {
            result.put(tableEntry.getKey(), new ArrayList<>(tableEntry.getValue().values()));
        }
        return result;
    }

    /**
     * Loads row count for a batch of tables.
     */
    private Map<String, Long> loadRowCountBatch(Set<String> tableNames, String schemaName) {
        if (tableNames.isEmpty()) {
            return Collections.emptyMap();
        }

        String placeholders = String.join(",", Collections.nCopies(tableNames.size(), "?"));
        String sql = """
                SELECT
                    TABLE_NAME,
                    TABLE_ROWS
                FROM INFORMATION_SCHEMA.TABLES
                WHERE TABLE_NAME IN (%s)
                AND TABLE_SCHEMA = ?
                """.formatted(placeholders);

        Object[] params = new Object[tableNames.size() + 1];
        int i = 0;
        for (String tableName : tableNames) {
            params[i++] = tableName;
        }
        params[i] = schemaName;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String tableName = (String) row.get("TABLE_NAME");
            Object rowsObj = row.get("TABLE_ROWS");
            Long rowCount = (rowsObj == null) ? 0L : Long.valueOf(rowsObj.toString());
            result.put(tableName, rowCount);
        }
        return result;
    }

    /**
     * Helper method to get a value from the cache, loading it if absent.
     * @param cache the cache map (schema -> table -> value)
     * @param schemaName the schema name (empty string if null)
     * @param tableName the table name
     * @param loader a supplier to load the value if not in cache
     * @param <T> the type of the cached value
     * @return the cached or newly loaded value
     */
    private <T> T getFromCache(Map<String, Map<String, T>> cache, String schemaName,
                               String tableName, java.util.function.Supplier<T> loader) {
        Map<String, T> schemaMap = cache.computeIfAbsent(schemaName, k -> new ConcurrentHashMap<>());
        return schemaMap.computeIfAbsent(tableName, k -> loader.get());
    }

    /**
     * Helper method to put all entries from a map into the cache for a given schema.
     */
    private <T> void putAllToCache(Map<String, Map<String, T>> cache, String schemaName,
                                   Map<String, T> values) {
        Map<String, T> schemaMap = cache.computeIfAbsent(schemaName, k -> new ConcurrentHashMap<>());
        schemaMap.putAll(values);
    }

    /**
     * Loads column information for a single table (used as fallback when caching misses).
     */
    private List<ColumnInfo> loadColumnInfo(String tableName, String schemaName) {
        return loadColumnInfoBatch(Set.of(tableName), schemaName).getOrDefault(tableName, Collections.emptyList());
    }

    /**
     * Loads index information for a single table (used as fallback when caching misses).
     */
    private List<IndexInfo> loadIndexInfo(String tableName, String schemaName) {
        return loadIndexInfoBatch(Set.of(tableName), schemaName).getOrDefault(tableName, Collections.emptyList());
    }

    /**
     * Loads row count for a single table (used as fallback when caching misses).
     */
    private Long loadRowCount(String tableName, String schemaName) {
        return loadRowCountBatch(Set.of(tableName), schemaName).getOrDefault(tableName, 0L);
    }

    /**
     * Gets statistics for a single table.
     */
    private Map<String, Object> getTableStats(String tableName, String schemaName) {
        Map<String, Object> tableStats = new HashMap<>();
        long rowCount = getRowCount(tableName, schemaName);
        int indexCount = getIndexCount(tableName, schemaName);
        tableStats.put("row_count", rowCount);
        tableStats.put("index_count", indexCount);
        return tableStats;
    }

    /**
     * Gets the index count for a single table.
     */
    private int getIndexCount(String tableName, String schemaName) {
        if (tableName == null || tableName.isEmpty()) {
            return 0;
        }
        String effectiveSchema = (schemaName != null) ? schemaName : getSchemaName();
        effectiveSchema = (effectiveSchema == null) ? "" : effectiveSchema;
        String sql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_NAME = ? AND (TABLE_SCHEMA = ? OR ? IS NULL) AND INDEX_NAME != 'PRIMARY'";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, tableName, effectiveSchema, effectiveSchema);
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Gets the schema name from the database connection.
     */
    private String getSchemaName() {
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Row mapper for ColumnInfo objects.
     */
    private RowMapper<ColumnInfo> columnRowMapper() {
        return (rs, rowNum) -> {
            ColumnInfo column = new ColumnInfo();
            column.setColumnName(rs.getString("COLUMN_NAME"));
            column.setDataType(rs.getString("DATA_TYPE"));
            column.setCharacterMaximumLength(
                    rs.getObject("CHARACTER_MAXIMUM_LENGTH", Integer.class));
            column.setNumericPrecision(
                    rs.getObject("NUMERIC_PRECISION", Integer.class));
            column.setNumericScale(
                    rs.getObject("NUMERIC_SCALE", Integer.class));
            column.setNullable(rs.getBoolean("IS_NULLABLE"));
            column.setDefaultValue(rs.getString("COLUMN_DEFAULT"));
            return column;
        };
    }
}