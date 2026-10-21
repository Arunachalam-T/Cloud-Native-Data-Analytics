package com.dataanalytics.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataQualityReportDto {
    private Long datasetId;
    private String datasetName;
    private Long totalRows;
    private Integer totalColumns;
    private Double overallQualityScore; // 0-100
    private Long duplicateRowCount;
    private List<ColumnQualityDto> columnQuality;
    private List<String> warnings;
    private List<String> recommendations;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ColumnQualityDto {
        private String columnName;
        private String dataType;
        private Long nullCount;
        private Double nullPercentage;
        private Long uniqueCount;
        private Long duplicateCount;
        private Boolean hasOutliers;
        private Long outlierCount;
        private Double qualityScore; // 0-100
        private List<String> issues;
    }
}
