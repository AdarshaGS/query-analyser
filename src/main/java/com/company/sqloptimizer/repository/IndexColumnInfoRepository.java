package com.company.sqloptimizer.repository;

import com.company.sqloptimizer.entity.IndexColumnInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IndexColumnInfoRepository extends JpaRepository<IndexColumnInfo, Long> {

    List<IndexColumnInfo> findByIndexId(Long indexId);

}
