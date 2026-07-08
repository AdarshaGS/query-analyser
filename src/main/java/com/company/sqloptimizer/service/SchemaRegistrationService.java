package com.company.sqloptimizer.service;

import com.company.sqloptimizer.dto.SchemaRegistrationRequest;
import com.company.sqloptimizer.dto.SchemaRegistrationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * Service for registering schema information.
 */
@Service
@RequiredArgsConstructor
public class SchemaRegistrationService {

    private final SchemaService schemaService;

    public SchemaRegistrationResponse registerSchema(String createTableStatement, List<String> indexStatements) {
        // Delegate to SchemaService which handles the parsing and saving
        return schemaService.registerSchema(createTableStatement, indexStatements);
    }

}