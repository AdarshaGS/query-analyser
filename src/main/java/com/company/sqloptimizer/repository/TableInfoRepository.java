package com.company.sqloptimizer.repository;

import com.company.sqloptimizer.entity.TableInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TableInfoRepository extends JpaRepository<TableInfo, Long> {

    TableInfo findByTableNameAndSchemaName(String tableName, String schemaName);

}
