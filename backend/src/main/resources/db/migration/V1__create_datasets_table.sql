-- V1__create_datasets_table.sql
-- Initial schema: datasets table

CREATE TABLE datasets (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(255)        NOT NULL,
    original_filename VARCHAR(255)   NOT NULL,
    s3_key       VARCHAR(512)        UNIQUE NOT NULL,
    s3_url       VARCHAR(1024),
    file_format  VARCHAR(20)         NOT NULL DEFAULT 'CSV',
    file_size_bytes BIGINT,
    row_count    BIGINT,
    column_count INTEGER,
    status       VARCHAR(30)         NOT NULL DEFAULT 'PROCESSING',
    description  TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_datasets_status ON datasets(status);
CREATE INDEX idx_datasets_created_at ON datasets(created_at DESC);
