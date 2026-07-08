package com.company.sqloptimizer.repository;

import com.company.sqloptimizer.entity.AnalysisHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for accessing analysis history.
 */
@Repository
public interface AnalysisHistoryRepository extends JpaRepository<AnalysisHistory, Long> {

    /**
     * Finds an analysis record by query hash.
     *
     * @param queryHash the hash of the query
     * @return the analysis history record, or empty if not found
     */
    Optional<AnalysisHistory> findByQueryHash(String queryHash);

    /**
     * Finds an analysis record by request identifier.
     *
     * @param requestIdentifier the request identifier
     * @return the analysis history record, or empty if not found
     */
    Optional<AnalysisHistory> findByRequestIdentifier(String requestIdentifier);

}