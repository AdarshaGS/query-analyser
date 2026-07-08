package com.company.sqloptimizer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for storing named SQL queries that can be retrieved by ID or name.
 * Maps to the stretchy_report table.
 */
@Entity
@Table(name = "stretchy_report",
        indexes = {
                @Index(name = "uq_stored_query_name", columnList = "query_name", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Unique name for the query (used for retrieval by name).
     */
    @Column(name = "query_name", length = 255, nullable = false, unique = true)
    private String queryName;

    /**
     * The actual SQL query text.
     */
    @Column(name = "report_sql", columnDefinition = "TEXT", nullable = false)
    private String queryText;

    /**
     * Description of what the query does or its purpose.
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * When the query was created.
     */
    @CreationTimestamp
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    /**
     * When the query was last updated.
     */
    @UpdateTimestamp
    @Column(name = "modified_date")
    private LocalDateTime modifiedDate;

    /**
     * Whether this query is active and available for use.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Additional fields from stretchy_report table

    @Column(name = "report_type", length = 20, nullable = false)
    private String reportType;

    @Column(name = "report_subtype", length = 20)
    private String reportSubtype;

    @Column(name = "report_category", length = 45)
    private String reportCategory;

    @Column(name = "core_report")
    private Boolean coreReport;

    @Column(name = "use_report")
    private Boolean useReport;

    @Column(name = "track_usage", nullable = false)
    @Builder.Default
    private Boolean trackUsage = false;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "embedded_report_type")
    private Boolean embeddedReportType;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "report_output_type", length = 16)
    private String reportOutputType;

    @Column(name = "execute_on_oltp")
    private Boolean executeOnOlpt;

    @Column(name = "meta_data", columnDefinition = "TEXT")
    private String metaData;

    @Column(name = "lastmodified_date")
    private LocalDateTime lastmodifiedDate;

    @Column(name = "complexity_score")
    private Integer complexityScore;
}