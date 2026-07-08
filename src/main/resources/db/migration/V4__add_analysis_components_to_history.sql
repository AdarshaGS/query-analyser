ALTER TABLE analysis_history
ADD COLUMN sql_analysis_json JSON,
ADD COLUMN explain_analysis_json JSON,
ADD COLUMN schema_metadata_json JSON,
ADD COLUMN table_statistics_json JSON;