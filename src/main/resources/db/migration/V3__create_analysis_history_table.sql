CREATE TABLE analysis_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    query_hash VARCHAR(64) NOT NULL UNIQUE,
    query_text TEXT NOT NULL,
    analysis_result JSON NOT NULL,
    created_date TIMESTAMP NOT NULL,
    INDEX idx_created_date (created_date)
);