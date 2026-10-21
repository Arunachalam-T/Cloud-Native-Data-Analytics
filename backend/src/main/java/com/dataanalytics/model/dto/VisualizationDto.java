package com.dataanalytics.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VisualizationDto {
    private Long datasetId;
    private List<ChartDto> charts;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ChartDto {
        private String chartType;   // HISTOGRAM, BAR, SCATTER, BOX, PIE, LINE
        private String title;
        private String xLabel;
        private String yLabel;
        private String xColumn;
        private String yColumn;
        private List<DataPoint> dataPoints;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    public static class DataPoint {
        private Object x;
        private Object y;
        private String label;
        private Long count;
    }
}
