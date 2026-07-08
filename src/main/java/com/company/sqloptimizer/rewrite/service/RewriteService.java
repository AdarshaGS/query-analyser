package com.company.sqloptimizer.rewrite.service;

import org.springframework.stereotype.Service;

import com.company.sqloptimizer.rewrite.dto.RewriteSqlRequest;
import com.company.sqloptimizer.rewrite.dto.RewriteSqlResponse;

/**
 * Service for rewriting SQL queries based on selected issue or recommendation.
 */
@Service
public interface RewriteService {

    /**
     * Rewrites the SQL query based on the selected issue or recommendation.
     *
     * @param request the rewrite request
     * @return the rewrite response
     */
    RewriteSqlResponse rewriteSql(RewriteSqlRequest request);
}