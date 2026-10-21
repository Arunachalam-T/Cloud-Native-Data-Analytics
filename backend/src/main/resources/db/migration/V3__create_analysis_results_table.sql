-- V3__create_analysis_results_table.sql
-- Cached analysis results (EDA, quality, stats) stored as JSONB for fast retrieval

CREATE TABLE analysis_results (
    id           BIGSERIAL PRIMARY KEY,
    dataset_id   BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    result_type  VARCHAR(50) NOT NULL,   -- 'EDA', 'QUALITY', 'STATS', 'VISUALIZATION'
    result_data  JSONB NOT NULL,
    computed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (dataset_id, result_type)
);

CREATE INDEX idx_analysis_results_dataset_id ON analysis_results(dataset_id);
CREATE INDEX idx_analysis_results_type ON analysis_results(result_type);
