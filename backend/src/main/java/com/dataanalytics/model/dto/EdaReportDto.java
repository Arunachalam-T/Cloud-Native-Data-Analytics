package com.dataanalytics.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EdaReportDto {
    private Long datasetId;
    private String datasetName;
    private Long rowCount;
    private Integer columnCount;
    private Long duplicateRows;
    private Long totalMissingValues;
    private Double missingValuePercentage;
    private List<ColumnEdaDto> columns;
    private Map<String, Object> correlationMatrix;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ColumnEdaDto {
        private String columnName;
        private String dataType;
        private Long nullCount;
        private Double nullPercentage;
        private Long uniqueCount;
        private Double uniquePercentage;

        // Numeric stats
        private Double min;
        private Double max;
        private Double mean;
        private Double median;
        private Double stdDev;
        private Double variance;
        private Double q1;
        private Double q3;
        private Double iqr;
        private List<Double> outliers;

        // Categorical stats
        private Map<String, Long> valueCounts;
        private String mostFrequentValue;
        private Long mostFrequentCount;
    }
}
