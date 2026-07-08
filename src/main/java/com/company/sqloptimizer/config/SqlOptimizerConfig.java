package com.company.sqloptimizer.config;

import com.company.sqloptimizer.metadata.JdbcMetadataCollector;
import com.company.sqloptimizer.metadata.MetadataCollector;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration class for the SQL Optimizer application.
 */
@Configuration
public class SqlOptimizerConfig {

    /**
     * Configures the MetadataCollector bean to use the JDBC implementation
     * which fetches schema information from the database using JDBC.
     */
    @Bean
    @Primary
    public MetadataCollector metadataCollector(JdbcTemplate jdbcTemplate) {
        return new JdbcMetadataCollector(jdbcTemplate);
    }

    /**
     * Configures a RestTemplate bean for making HTTP requests to external APIs.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}