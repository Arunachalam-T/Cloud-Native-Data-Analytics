-- V2__create_column_metadata_table.sql
-- Column-level metadata for each uploaded dataset

CREATE TABLE column_metadata (
    id           BIGSERIAL PRIMARY KEY,
    dataset_id   BIGINT NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
    column_name  VARCHAR(255) NOT NULL,
    data_type    VARCHAR(50)  NOT NULL,
    null_count   BIGINT       NOT NULL DEFAULT 0,
    unique_count BIGINT       NOT NULL DEFAULT 0,
    min_value    VARCHAR(255),
    max_value    VARCHAR(255),
    mean_value   DOUBLE PRECISION,
    std_dev      DOUBLE PRECISION,
    column_index INTEGER      NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_column_metadata_dataset_id ON column_metadata(dataset_id);
