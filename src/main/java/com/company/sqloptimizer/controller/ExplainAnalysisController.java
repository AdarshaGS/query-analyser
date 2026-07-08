package com.company.sqloptimizer.controller;

import com.company.sqloptimizer.dto.ExplainAnalysisRequest;
import com.company.sqloptimizer.dto.ExplainAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.company.sqloptimizer.service.ExplainAnalysisService;

@RestController
@RequestMapping("/api/v1/explain")
@RequiredArgsConstructor
public class ExplainAnalysisController {

    private final ExplainAnalysisService explainAnalysisService;

    @PostMapping("/analyze")
    public ResponseEntity<ExplainAnalysisResponse> analyzeExplain(@RequestBody ExplainAnalysisRequest request) {
        ExplainAnalysisResponse response = explainAnalysisService.analyzeExplain(request.getExplainJson());
        return ResponseEntity.ok(response);
    }

}