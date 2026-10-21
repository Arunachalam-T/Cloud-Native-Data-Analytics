package com.dataanalytics.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataPreviewDto {
    private Long datasetId;
    private List<String> headers;
    private List<List<String>> rows;
    private Long totalRows;
    private Integer displayedRows;
    private Integer totalColumns;
    private Map<String, String> columnTypes;
}
