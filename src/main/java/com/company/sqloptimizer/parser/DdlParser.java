package com.company.sqloptimizer.parser;

import java.util.List;

/**
 * Parser for DDL statements (CREATE TABLE, CREATE INDEX, etc.).
 */
public interface DdlParser {

    /**
     * Parses a CREATE TABLE statement.
     * @param ddl the CREATE TABLE statement
     * @return the parsed result
     */
    ParseResult parseCreateTable(String ddl);

    /**
     * Parses a CREATE INDEX statement.
     * @param ddl the CREATE INDEX statement
     * @return the parsed result
     */
    ParseResult parseIndex(String ddl);

}
