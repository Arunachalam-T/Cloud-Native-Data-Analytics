package com.dataanalytics.model.dto;

import com.dataanalytics.model.entity.Dataset;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DatasetDto {
    private Long id;
    private String name;
    private String originalFilename;
    private String s3Url;
    private Dataset.FileFormat fileFormat;
    private Long fileSizeBytes;
    private Long rowCount;
    private Integer columnCount;
    private Dataset.DatasetStatus status;
    private String description;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<ColumnMetadataDto> columns;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ColumnMetadataDto {
        private Long id;
        private String columnName;
        private String dataType;
        private Long nullCount;
        private Long uniqueCount;
        private String minValue;
        private String maxValue;
        private Double meanValue;
        private Double stdDev;
        private Integer columnIndex;
    }
}
