package com.company.sqloptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entity for storing analysis history.
 */
@Entity
@Table(name = "analysis_history",
        indexes = {
                @Index(name = "idx_query_hash", columnList = "query_hash"),
                @Index(name = "idx_created_date", columnList = "created_date")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Hash of the SQL query for deduplication and quick lookup.
     */
    @Column(name = "query_hash", length = 64, nullable = false, unique = true)
    private String queryHash;

    /**
     * The original SQL query text.
     */
    @Column(name = "query_text", columnDefinition = "TEXT", nullable = false)
    private String queryText;

    /**
     * The SQL analysis result in JSON format.
     */
    @Column(name = "sql_analysis_json", columnDefinition = "JSON")
    private String sqlAnalysisJson;

    /**
     * The EXPLAIN analysis result in JSON format.
     */
    @Column(name = "explain_analysis_json", columnDefinition = "JSON")
    private String explainAnalysisJson;

    /**
     * The schema metadata in JSON format.
     */
    @Column(name = "schema_metadata_json", columnDefinition = "JSON")
    private String schemaMetadataJson;

    /**
     * The table statistics in JSON format.
     */
    @Column(name = "table_statistics_json", columnDefinition = "JSON")
    private String tableStatisticsJson;

    /**
     * The analysis result in JSON format (the overall report).
     */
    @Column(name = "analysis_result", columnDefinition = "JSON", nullable = false)
    private String analysisResult;

    /**
     * When the analysis was performed.
     */
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @Column(name = "request_identifier")
    private String requestIdentifier;

    @Column(name = "complexity_score")
    private String complexityScore;

    @PrePersist
    protected void onCreate() {
        createdDate = LocalDateTime.now();
    }
}