package com.company.sqloptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "column_info", uniqueConstraints = @UniqueConstraint(columnNames = {"table_id", "column_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private TableInfo table;

    @Column(name = "column_name", nullable = false)
    private String columnName;

    @Column(name = "data_type", nullable = false)
    private String dataType;

    @Column(name = "is_nullable", nullable = false)
    private boolean nullable;

    @Column(name = "column_default")
    private String defaultValue;

    @Column(name = "character_maximum_length")
    private Integer characterMaximumLength;

    @Column(name = "numeric_precision")
    private Integer numericPrecision;

    @Column(name = "numeric_scale")
    private Integer numericScale;

}