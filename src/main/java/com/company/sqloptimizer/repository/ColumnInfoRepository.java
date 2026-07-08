package com.company.sqloptimizer.repository;

import com.company.sqloptimizer.entity.ColumnInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ColumnInfoRepository extends JpaRepository<ColumnInfo, Long> {

    List<ColumnInfo> findByTableId(Long tableId);

}
