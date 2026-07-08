package com.company.sqloptimizer.controller;

import com.company.sqloptimizer.dto.SqlAnalysisRequest;
import com.company.sqloptimizer.dto.SqlAnalysisResponse;
import com.company.sqloptimizer.service.SqlAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sql")
@RequiredArgsConstructor
public class SqlAnalysisController {

    private final SqlAnalysisService sqlAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<SqlAnalysisResponse> analyzeSql(@RequestBody SqlAnalysisRequest request) {
        SqlAnalysisResponse response = sqlAnalysisService.analyzeSql(request.getQuery());
        return ResponseEntity.ok(response);
    }

}