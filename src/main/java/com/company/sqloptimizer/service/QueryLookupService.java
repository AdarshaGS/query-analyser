package com.company.sqloptimizer.service;

import com.company.sqloptimizer.exception.SqlOptimizerException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Service for looking up stored SQL queries by ID or name.
 */
@Service
@RequiredArgsConstructor
public class QueryLookupService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * Retrieves a stored query by its ID.
     *
     * @param queryId the ID of the stored query
     * @return the query text if found and active, or empty string if not found/inactive
     * @throws SqlOptimizerException if the query is found and active but the query text is null
     */
    @Transactional(readOnly = true)
    public String getQueryById(Long queryId) throws SqlOptimizerException {
        if (queryId == null) {
            return "";
        }

        String sql = "SELECT report_sql, is_active FROM stretchy_report WHERE id = :queryId";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("queryId", queryId);

        return jdbcTemplate.query(sql, params, new RowMapper<String>() {
            @Override
            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                boolean isActive = rs.getBoolean("is_active");
                if (!isActive) {
                    return "";
                }
                String queryText = rs.getString("report_sql");
                if (queryText == null) {
                    throw new SqlOptimizerException("report query should be mandatory");
                }
                return queryText;
            }
        }).stream().findFirst().orElse("");
    }

    /**
     * Retrieves a stored query by its name.
     *
     * @param queryName the name of the stored query
     * @return the query text if found and active, or empty string if not found/inactive
     * @throws SqlOptimizerException if the query is found and active but the query text is null
     */
    @Transactional(readOnly = true)
    public String getQueryByName(String queryName) throws SqlOptimizerException {
        if (queryName == null || queryName.isBlank()) {
            return "";
        }

        String sql = "SELECT report_sql, is_active FROM stretchy_report WHERE query_name = :queryName";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("queryName", queryName);

        return jdbcTemplate.query(sql, params, new RowMapper<String>() {
            @Override
            public String mapRow(ResultSet rs, int rowNum) throws SQLException {
                boolean isActive = rs.getBoolean("is_active");
                if (!isActive) {
                    return "";
                }
                String queryText = rs.getString("report_sql");
                if (queryText == null) {
                    throw new SqlOptimizerException("report query should be mandatory");
                }
                return queryText;
            }
        }).stream().findFirst().orElse("");
    }

    /**
     * Stores a new query or updates an existing one.
     *
     * @param queryName the unique name for the query
     * @param queryText the SQL query text
     * @param description optional description of the query
     * @return true if successful
     */
    @Transactional
    public boolean saveQuery(String queryName, String queryText, String description) {
        // Check if a query with this name already exists
        String checkSql = "SELECT COUNT(*) FROM stretchy_report WHERE query_name = :queryName";
        MapSqlParameterSource checkParams = new MapSqlParameterSource();
        checkParams.addValue("queryName", queryName);

        Integer count = jdbcTemplate.queryForObject(checkSql, checkParams, Integer.class);
        boolean exists = (count != null && count > 0);

        if (exists) {
            // Update existing query
            String updateSql = "UPDATE stretchy_report SET report_sql = :queryText, description = :description, " +
                    "modified_date = NOW() WHERE query_name = :queryName";
            MapSqlParameterSource updateParams = new MapSqlParameterSource();
            updateParams.addValue("queryText", queryText);
            updateParams.addValue("description", description);
            updateParams.addValue("queryName", queryName);

            jdbcTemplate.update(updateSql, updateParams);
        } else {
            // Insert new query
            String insertSql = "INSERT INTO stretchy_report (query_name, report_sql, description, is_active, created_date, modified_date) " +
                    "VALUES (:queryName, :queryText, :description, true, NOW(), NOW())";
            MapSqlParameterSource insertParams = new MapSqlParameterSource();
            insertParams.addValue("queryName", queryName);
            insertParams.addValue("queryText", queryText);
            insertParams.addValue("description", description);

            jdbcTemplate.update(insertSql, insertParams);
        }

        return true;
    }

    /**
     * Deactivates a stored query (soft delete).
     *
     * @param queryId the ID of the query to deactivate
     * @return true if the query was found and deactivated, false otherwise
     */
    @Transactional
    public boolean deactivateQuery(Long queryId) {
        if (queryId == null) {
            return false;
        }

        String sql = "UPDATE stretchy_report SET is_active = false WHERE id = :queryId";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("queryId", queryId);

        int rowsAffected = jdbcTemplate.update(sql, params);
        return rowsAffected > 0;
    }
}