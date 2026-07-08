package com.company.sqloptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "index_column_info", uniqueConstraints = @UniqueConstraint(columnNames = {"index_id", "column_id", "position_in_index"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexColumnInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "index_id", nullable = false)
    private IndexInfo index;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "column_id", nullable = false)
    private ColumnInfo column;

    @Column(name = "position_in_index", nullable = false)
    private int positionInIndex;

}