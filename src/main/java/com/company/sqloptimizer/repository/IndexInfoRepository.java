package com.company.sqloptimizer.repository;

import com.company.sqloptimizer.entity.IndexInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IndexInfoRepository extends JpaRepository<IndexInfo, Long> {

    List<IndexInfo> findByTableId(Long tableId);

}
