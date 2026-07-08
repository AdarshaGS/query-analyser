package com.company.sqloptimizer.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * OpenAPI 3.0 configuration for the SQL Optimizer API.
 */
@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        Map<String, SecurityScheme> securitySchemes = new HashMap<>();
        securitySchemes.put("bearerAuth", securityScheme);

        Components components = new Components();
        components.setSecuritySchemes(securitySchemes);

        return new OpenAPI()
                .info(new Info()
                        .title("SQL Optimization Platform API")
                        .version("1.0.0")
                        .description("API for SQL query analysis and optimization"))
                .addSecurityItem(new SecurityRequirement()
                        .addList("bearerAuth"))
                .components(components);
    }
}