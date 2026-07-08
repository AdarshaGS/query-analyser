package com.company.sqloptimizer.parser;

import com.company.sqloptimizer.dto.*;
import com.company.sqloptimizer.entity.*;
import org.jooq.*;
import org.jooq.conf.*;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultConfiguration;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of DdlParser using jOOQ.
 */
@Component
public class DdlParserImpl implements DdlParser {

    private final Configuration configuration;

    public DdlParserImpl() {
        this.configuration = new DefaultConfiguration()
                .set(SQLDialect.MYSQL);
    }

    @Override
    public ParseResult parseCreateTable(String ddl) {
        try {
            // Parse the DDL into a list of Query
            Queries queries = DSL.using(configuration).parser().parse(ddl);
            List<Query> parts = new ArrayList<>();
            for (Query query : queries) {
                parts.add(query);
            }

            if (parts.isEmpty()) {
                return new ParseResult(null, Collections.<ColumnInfo>emptyList());
            }

            // We expect the first part to be a CreateTable (for CREATE TABLE statements)
            QueryPart part = parts.get(0);

            if (part instanceof Table) {
                // For CREATE TABLE, jOOQ returns a Table object
                Table<?> table = (Table<?>) part;

                // Extract table information
                TableInfo tableInfo = new TableInfo();
                tableInfo.setTableName(table.getName());
                tableInfo.setSchemaName(table.getSchema() != null ? table.getSchema().getName() : null);

                // For now, we'll return an empty column list since jOOQ's parser
                // doesn't readily expose column details from parsed Table objects
                // In a production implementation, we might need to use DDLDatabase
                // or parse the DDL differently to get column details
                return new ParseResult(tableInfo, Collections.<ColumnInfo>emptyList());
            } else {
                // Not a CREATE TABLE statement
                return new ParseResult(null, Collections.<ColumnInfo>emptyList());
            }
        } catch (Exception e) {
            // If parsing fails, return empty result
            return new ParseResult(null, Collections.<ColumnInfo>emptyList());
        }
    }

    @Override
    public ParseResult parseIndex(String ddl) {
        try {
            // Parse the DDL into a list of Query
            Queries queries = DSL.using(configuration).parser().parse(ddl);
            List<Query> parts = new ArrayList<>();
            for (Query query : queries) {
                parts.add(query);
            }

            if (parts.isEmpty()) {
                return new ParseResult(null, Collections.<IndexColumnInfo>emptyList());
            }

            // We expect the first part to be a CreateIndex (for CREATE INDEX statements)
            QueryPart part = parts.get(0);

            if (part instanceof Index) {
                // For CREATE INDEX, jOOQ returns an Index object
                Index index = (Index) part;

                // Extract index information
                IndexInfo indexInfo = new IndexInfo();
                indexInfo.setIndexName(index.getName());
                indexInfo.setType("BTREE"); // Default for MySQL
                indexInfo.setUnique(ddl.toUpperCase().contains("UNIQUE"));

                // For now, we'll return an empty index column list since
                // jOOQ's parser doesn't readily expose column details from parsed Index objects
                // In a production implementation, we might need to parse the DDL differently
                return new ParseResult(indexInfo, Collections.<IndexColumnInfo>emptyList());
            } else {
                // Not a CREATE INDEX statement
                return new ParseResult(null, Collections.<IndexColumnInfo>emptyList());
            }
        } catch (Exception e) {
            // If parsing fails, return empty result
            return new ParseResult(null, Collections.<IndexColumnInfo>emptyList());
        }
    }
}