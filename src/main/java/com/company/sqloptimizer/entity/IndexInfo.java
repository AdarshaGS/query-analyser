package com.company.sqloptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "index_info", uniqueConstraints = @UniqueConstraint(columnNames = {"table_id", "index_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndexInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private TableInfo table;

    @Column(name = "index_name", nullable = false)
    private String indexName;

    @Column(name = "is_unique", nullable = false)
    private boolean unique;

    @Column(name = "type")
    private String type;

    @Builder.Default
    @OneToMany(mappedBy = "index", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<IndexColumnInfo> indexColumns = new HashSet<>();

}