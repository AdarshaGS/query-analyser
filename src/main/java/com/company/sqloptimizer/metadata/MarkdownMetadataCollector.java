package com.company.sqloptimizer.metadata;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.company.sqloptimizer.analyzer.SchemaInfo;
import com.company.sqloptimizer.entity.ColumnInfo;
import com.company.sqloptimizer.entity.IndexInfo;
import com.company.sqloptimizer.entity.TableInfo;

/**
 * Metadata collector that reads schema information from a markdown file.
 * This is an alternative to JdbcMetadataCollector that reads from schema.md
 * instead of querying the database directly.
 */
@Component
public class MarkdownMetadataCollector implements MetadataCollector {

    private final String schemaFilePath;

    public MarkdownMetadataCollector() {
        this.schemaFilePath = "schema.md"; // Relative to working directory
    }

    public MarkdownMetadataCollector(String schemaFilePath) {
        this.schemaFilePath = schemaFilePath;
    }

    @Override
    public SchemaInfo collectMetadata(Set<String> tableNames) {
        Set<TableInfo> tables = new HashSet<>();
        Map<String, TableInfo> tableMap = new HashMap<>();

        try {
            // Parse the markdown file to extract table information
            Map<String, List<ColumnInfo>> tableColumns = parseSchemaMarkdown();

            // Convert to TableInfo objects
            for (Map.Entry<String, List<ColumnInfo>> entry : tableColumns.entrySet()) {
                String tableName = entry.getKey();
                List<ColumnInfo> columns = entry.getValue();

                // Only include tables that were requested (if tableNames is not empty)
                if (tableNames.isEmpty() || tableNames.contains(tableName)) {
                    TableInfo table = new TableInfo();
                    table.setTableName(tableName);
                    table.setSchemaName(null); // We don't track schema in the markdown file
                    table.setColumns(new HashSet<>(columns));
                    tables.add(table);
                }
            }
        } catch (IOException e) {
            // If we can't read the file, return empty schema
            System.err.println("Warning: Could not read schema file " + schemaFilePath + ": " + e.getMessage());
        }

        return new SchemaInfo(tables);
    }

    @Override
    public List<ColumnInfo> collectColumnInfo(String tableName, String schemaName) {
        try {
            Map<String, List<ColumnInfo>> tableColumns = parseSchemaMarkdown();
            return tableColumns.getOrDefault(tableName, Collections.emptyList());
        } catch (IOException e) {
            System.err.println("Warning: Could not read schema file " + schemaFilePath + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<IndexInfo> collectIndexInfo(String tableName, String schemaName) {
        // The markdown file doesn't contain index information, so return empty list
        return Collections.emptyList();
    }

    @Override
    public long getRowCount(String tableName, String schemaName) {
        // The markdown file doesn't contain row count information, so return 0
        // In a real implementation, we might want to store this separately or
        // have a different mechanism for statistics
        return 0;
    }

    /**
     * Gets statistics for the specified tables.
     *
     * @param tableNames set of table names to get statistics for
     * @return map of table name to statistics (row count, index count, etc.)
     */
    @Override
    public Map<String, Object> getTableStatistics(Set<String> tableNames) {
        Map<String, Object> stats = new HashMap<>();
        if (tableNames == null) {
            return stats;
        }
        for (String tableName : tableNames) {
            // Since markdown doesn't have stats, return zeros
            Map<String, Object> tableStats = new HashMap<>();
            tableStats.put("row_count", 0L);
            tableStats.put("index_count", 0);
            stats.put(tableName, tableStats);
        }
        return stats;
    }

    /**
     * Parses the schema markdown file and extracts table/column information.
     *
     * @return Map of table name to list of columns
     * @throws IOException if the file cannot be read
     */
    private Map<String, List<ColumnInfo>> parseSchemaMarkdown() throws IOException {
        Map<String, List<ColumnInfo>> result = new HashMap<>();

        // Read the markdown file
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(schemaFilePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
        }

        String markdown = content.toString();

        // Find the "Current Schema Information" section
        int startIndex = markdown.indexOf("## Current Schema Information");
        if (startIndex == -1) {
            System.err.println("Warning: Could not find '## Current Schema Information' section in " + schemaFilePath);
            return result;
        }

        // Find the start of the table (look for the first markdown table header after this section)
        int tableStart = markdown.indexOf("|", startIndex);
        if (tableStart == -1) {
            System.err.println("Warning: Could not find table start in 'Current Schema Information' section");
            return result;
        }

        // Find the end of the table (look for the next markdown header or end of file)
        int tableEnd = markdown.indexOf("\n\n##", tableStart);
        if (tableEnd == -1) {
            tableEnd = markdown.length();
        }

        String tableSection = markdown.substring(tableStart, tableEnd);

        // Parse the markdown table
        List<String> lines = Arrays.asList(tableSection.split("\n"));
        if (lines.size() < 3) { // Need at least header, separator, and one data row
            System.err.println("Warning: Table in 'Current Schema Information' section has insufficient rows");
            return result;
        }

        // Parse header to get column indices
        String headerLine = lines.get(0);
        List<String> headers = parseMarkdownTableRow(headerLine);
        Map<String, Integer> headerIndices = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            headerIndices.put(headers.get(i).toLowerCase().trim(), i);
        }

        // Skip the separator line (usually contains |---|)
        int dataStartIndex = 2;

        // Process each data row
        for (int i = dataStartIndex; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || !line.startsWith("|")) {
                continue; // Skip empty lines or non-table lines
            }

            List<String> cells = parseMarkdownTableRow(line);
            if (cells.size() < headers.size()) {
                // Pad with empty cells if needed
                while (cells.size() < headers.size()) {
                    cells.add("");
                }
            }

            // Extract column information
            String tableName = getCellValue(cells, headerIndices, "table_name");
            String columnName = getCellValue(cells, headerIndices, "column_name");
            String dataType = getCellValue(cells, headerIndices, "data_type");
            String isNullable = getCellValue(cells, headerIndices, "is_nullable");
            String columnKey = getCellValue(cells, headerIndices, "column_key");
            String defaultValue = getCellValue(cells, headerIndices, "default_value");
            String extra = getCellValue(cells, headerIndices, "extra");
            String comment = getCellValue(cells, headerIndices, "comment");

            if (tableName.isEmpty() || columnName.isEmpty()) {
                continue; // Skip rows without essential data
            }

            // Create ColumnInfo object
            ColumnInfo column = new ColumnInfo();
            column.setColumnName(columnName);
            column.setDataType(dataType);
            // Set nullable based on IS_NULLABLE column (typically "YES" or "NO")
            column.setNullable("YES".equalsIgnoreCase(isNullable));
            // Set default value if available
            if (defaultValue != null && !defaultValue.isEmpty()) {
                column.setDefaultValue(defaultValue);
            }
            // Note: The markdown format doesn't directly provide character_maximum_length,
            // numeric_precision, or numeric_scale in separate columns.
            // These would need to be parsed from the DATA_TYPE/COLUMN_TYPE field if needed.

            // Add to our map
            result.computeIfAbsent(tableName, k -> new ArrayList<>()).add(column);
        }

        return result;
    }

    /**
     * Parses a markdown table row into cell values.
     *
     * @param line The table row line (starting and ending with |)
     * @return List of cell values
     */
    private List<String> parseMarkdownTableRow(String line) {
        List<String> cells = new ArrayList<>();
        // Remove the leading and trailing |
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        // Split by | and trim each cell
        String[] split = trimmed.split("\\|");
        for (String cell : split) {
            cells.add(cell.trim());
        }
        return cells;
    }

    /**
     * Gets a cell value by column name.
     *
     * @param cells The list of cell values
     * @param headerIndices Map of header names to column indices
     * @param columnName The name of the column to get
     * @return The cell value, or empty string if not found
     */
    private String getCellValue(List<String> cells, Map<String, Integer> headerIndices, String columnName) {
        Integer index = headerIndices.get(columnName.toLowerCase().trim());
        if (index == null || index >= cells.size()) {
            return "";
        }
        return cells.get(index);
    }
}