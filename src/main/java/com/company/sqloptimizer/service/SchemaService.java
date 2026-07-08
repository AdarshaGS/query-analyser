package com.company.sqloptimizer.service;

import com.company.sqloptimizer.dto.SchemaRegistrationRequest;
import com.company.sqloptimizer.dto.SchemaRegistrationResponse;
import com.company.sqloptimizer.entity.*;
import com.company.sqloptimizer.analyzer.SchemaInfo;
import com.company.sqloptimizer.metadata.MetadataCollector;
import com.company.sqloptimizer.parser.DdlParser;
import com.company.sqloptimizer.parser.ParseResult;
import com.company.sqloptimizer.repository.ColumnInfoRepository;
import com.company.sqloptimizer.repository.IndexColumnInfoRepository;
import com.company.sqloptimizer.repository.IndexInfoRepository;
import com.company.sqloptimizer.repository.TableInfoRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing database schema information.
 */
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final TableInfoRepository tableInfoRepository;
    private final ColumnInfoRepository columnInfoRepository;
    private final IndexInfoRepository indexInfoRepository;
    private final IndexColumnInfoRepository indexColumnInfoRepository;
    private final DdlParser ddlParser;
    private final MetadataCollector metadataCollector;

    /**
     * Registers schema information from DDL statements.
     *
     * @param createTableStatement the CREATE TABLE statement
     * @param indexStatements list of CREATE INDEX statements
     * @return response containing the registered table and index information
     */
    @Transactional
    public SchemaRegistrationResponse registerSchema(String createTableStatement, List<String> indexStatements) {
        // Parse the CREATE TABLE statement
        ParseResult tableResult = ddlParser.parseCreateTable(createTableStatement);
        if (!tableResult.isCreateTable() || tableResult.getTable() == null) {
            throw new IllegalArgumentException("Invalid CREATE TABLE statement");
        }

        TableInfo table = tableResult.getTable();
        List<ColumnInfo> columns = tableResult.getColumns();

        // Save the table
        TableInfo savedTable = tableInfoRepository.save(table);

        // Save columns and associate them with the table
        List<ColumnInfo> savedColumns = columns.stream()
                .peek(column -> column.setTable(savedTable))
                .map(columnInfoRepository::save)
                .collect(Collectors.toList());

        // Save the table with its columns (to ensure the relationship is persisted)
        savedTable.setColumns(new HashSet<>(savedColumns));
        tableInfoRepository.save(savedTable);

        // Process index statements if provided
        List<IndexInfo> savedIndexes = new ArrayList<>();
        if (indexStatements != null && !indexStatements.isEmpty()) {
            for (String indexStatement : indexStatements) {
                ParseResult indexResult = ddlParser.parseIndex(indexStatement);
                if (indexResult.isCreateIndex() && indexResult.getIndex() != null) {
                    IndexInfo index = indexResult.getIndex();
                    List<IndexColumnInfo> indexColumns = indexResult.getIndexColumns();

                    // Set the table on the index (we need to find the table by name from the index DDL)
                    // For simplicity, we'll assume the index is on the same table as the CREATE TABLE
                    // In a more robust implementation, we'd parse the table name from the index DDL
                    index.setTable(savedTable);

                    // Save the index
                    IndexInfo savedIndex = indexInfoRepository.save(index);
                    savedIndexes.add(savedIndex);

                    // Save index columns and associate them with the index
                    List<IndexColumnInfo> savedIndexColumns = indexColumns.stream()
                            .peek(indexColumn -> indexColumn.setIndex(savedIndex))
                            // We'll need to set the column references properly - for now we'll leave them null
                            // and handle this in a more sophisticated way if needed
                            .map(indexColumnInfoRepository::save)
                            .collect(Collectors.toList());

                    // Update the index with its columns
                    savedIndex.setIndexColumns(new HashSet<>(savedIndexColumns));
                    indexInfoRepository.save(savedIndex);
                }
            }
        }

        // Build and return the response
        return SchemaRegistrationResponse.builder()
                .tableId(savedTable.getId())
                .tableName(savedTable.getTableName())
                .schemaName(savedTable.getSchemaName())
                .build();
    }

    /**
     * Gets schema information for the specified tables by collecting metadata from the database.
     *
     * @param tableNames set of table names to get schema for
     * @return set of TableInfo objects with complete metadata
     */
    public Set<TableInfo> getSchemaForTables(Set<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptySet();
        }

        // Collect metadata from the database
        SchemaInfo schemaInfo = metadataCollector.collectMetadata(tableNames);
        return schemaInfo.getAllTables();
    }

    /**
     * Gets all tables in the schema with their metadata.
     *
     * @return set of all TableInfo objects with metadata
     */
    public Set<TableInfo> getAllTables() {
        // Get all table names first
        Set<String> tableNames = getAllTableNames();
        // Then get their metadata
        return getSchemaForTables(tableNames);
    }

    /**
     * Gets all table names from the database.
     *
     * @return set of table names
     */
    public Set<String> getAllTableNames() {
        // This would typically query the database for all table names
        // For now, we'll get them from the repository
        return tableInfoRepository.findAll().stream()
                .map(TableInfo::getTableName)
                .collect(Collectors.toSet());
    }

    /**
     * Gets statistics for the specified tables.
     *
     * @param tableNames set of table names to get statistics for
     * @return map of table name to statistics (row count, index count, etc.)
     */
    public Map<String, Object> getTableStatistics(Set<String> tableNames) {
        if (tableNames == null || tableNames.isEmpty()) {
            return Collections.emptyMap();
        }
        return metadataCollector.getTableStatistics(tableNames);
    }
}