package com.company.sqloptimizer.controller;

import com.company.sqloptimizer.dto.SchemaRegistrationRequest;
import com.company.sqloptimizer.dto.SchemaRegistrationResponse;
import com.company.sqloptimizer.service.SchemaRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/schema")
@RequiredArgsConstructor
public class SchemaRegistrationController {

    private final SchemaRegistrationService schemaRegistrationService;

    @PostMapping("/register")
    public ResponseEntity<SchemaRegistrationResponse> registerSchema(@RequestBody SchemaRegistrationRequest request) {
        SchemaRegistrationResponse response = schemaRegistrationService.registerSchema(request.getCreateTableStatement(), request.getIndexStatements());
        return ResponseEntity.ok(response);
    }

}