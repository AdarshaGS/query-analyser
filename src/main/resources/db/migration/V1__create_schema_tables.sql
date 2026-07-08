CREATE TABLE IF NOT EXISTS table_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_name VARCHAR(255) NOT NULL,
    schema_name VARCHAR(255),
    UNIQUE KEY uk_table_schema (table_name, schema_name)
);

CREATE TABLE IF NOT EXISTS column_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    data_type VARCHAR(255) NOT NULL,
    is_nullable BOOLEAN NOT NULL,
    column_default VARCHAR(255),
    character_maximum_length INT,
    numeric_precision INT,
    numeric_scale INT,
    FOREIGN KEY (table_id) REFERENCES table_info(id) ON DELETE CASCADE,
    UNIQUE KEY uk_table_column (table_id, column_name)
);

CREATE TABLE IF NOT EXISTS index_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT NOT NULL,
    index_name VARCHAR(255) NOT NULL,
    is_unique BOOLEAN NOT NULL,
    type VARCHAR(50),
    FOREIGN KEY (table_id) REFERENCES table_info(id) ON DELETE CASCADE,
    UNIQUE KEY uk_table_index (table_id, index_name)
);

CREATE TABLE IF NOT EXISTS index_column_info (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    index_id BIGINT NOT NULL,
    column_id BIGINT NOT NULL,
    position_in_index INT NOT NULL,
    FOREIGN KEY (index_id) REFERENCES index_info(id) ON DELETE CASCADE,
    FOREIGN KEY (column_id) REFERENCES column_info(id) ON DELETE CASCADE,
    UNIQUE KEY uk_index_column (index_id, column_id, position_in_index)
);
