package com.company.sqloptimizer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "table_info", uniqueConstraints = @UniqueConstraint(columnNames = {"table_name", "schema_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_name", nullable = false)
    private String tableName;

    @Column(name = "schema_name")
    private String schemaName;

    @Builder.Default
    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ColumnInfo> columns = new HashSet<>();

    @Builder.Default
    @OneToMany(mappedBy = "table", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<IndexInfo> indexes = new HashSet<>();

}