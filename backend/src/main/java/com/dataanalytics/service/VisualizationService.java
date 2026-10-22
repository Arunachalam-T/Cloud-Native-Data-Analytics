package com.dataanalytics.service;

import com.dataanalytics.exception.DatasetNotFoundException;
import com.dataanalytics.model.dto.VisualizationDto;
import com.dataanalytics.model.dto.VisualizationDto.*;
import com.dataanalytics.model.entity.Dataset;
import com.dataanalytics.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

import com.dataanalytics.model.entity.AnalysisResult;
import com.dataanalytics.repository.AnalysisResultRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VisualizationService {

    private final DatasetRepository datasetRepository;
    private final AnalyticsService analyticsService;
    private final AnalysisResultRepository analysisResultRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate chart data for all appropriate columns in the dataset.
     * - Numeric columns → histogram + box chart data
     * - Categorical columns (≤50 unique) → bar chart data
     * - First two numeric columns → scatter plot data
     */
    public VisualizationDto generateVisualizations(Long datasetId) {
        var cached = analysisResultRepository.findByDatasetIdAndResultType(datasetId, AnalysisResult.ResultType.VISUALIZATION);
        if (cached.isPresent()) {
            log.info("Returning cached Visualization data for dataset id={}", datasetId);
            try {
                return objectMapper.convertValue(cached.get().getResultData(), VisualizationDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse cached Visualization data, recomputing: {}", e.getMessage());
            }
        }

        Dataset dataset = datasetRepository.findById(datasetId)
            .filter(d -> d.getStatus() == Dataset.DatasetStatus.READY)
            .orElseThrow(() -> new DatasetNotFoundException("Dataset not found or not ready: " + datasetId));

        Table table = analyticsService.loadTable(dataset.getS3Key());
        List<ChartDto> charts = new ArrayList<>();

        List<Column<?>> numericColumns = table.columns().stream()
            .filter(c -> c instanceof NumericColumn<?>)
            .collect(Collectors.toList());

        List<Column<?>> categoricalColumns = table.columns().stream()
            .filter(c -> !(c instanceof NumericColumn<?>) && c.countUnique() <= 50)
            .collect(Collectors.toList());

        // Histograms for numeric columns (up to 8)
        for (Column<?> col : numericColumns.stream().limit(8).collect(Collectors.toList())) {
            charts.add(buildHistogram(col));
        }

        // Box plot data for numeric columns
        for (Column<?> col : numericColumns.stream().limit(6).collect(Collectors.toList())) {
            charts.add(buildBoxPlotData(col));
        }

        // Bar charts and Pie charts for categorical columns
        for (Column<?> col : categoricalColumns.stream().limit(5).collect(Collectors.toList())) {
            charts.add(buildBarChart(col));
            if (col.countUnique() <= 8 && col.countUnique() >= 2) {
                charts.add(buildPieChart(col));
            }
        }

        // Scatter plot: first 2 numeric columns
        if (numericColumns.size() >= 2) {
            charts.add(buildScatterPlot(numericColumns.get(0), numericColumns.get(1)));
        }

        VisualizationDto dto = VisualizationDto.builder()
            .datasetId(datasetId)
            .charts(charts)
            .build();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.convertValue(dto, Map.class);
            AnalysisResult result = analysisResultRepository
                .findByDatasetIdAndResultType(datasetId, AnalysisResult.ResultType.VISUALIZATION)
                .orElseGet(() -> AnalysisResult.builder()
                    .dataset(dataset)
                    .resultType(AnalysisResult.ResultType.VISUALIZATION)
                    .build());
            result.setResultData(data);
            result.setComputedAt(java.time.OffsetDateTime.now());
            analysisResultRepository.save(result);
            log.info("Saved Visualization analysis result to database for dataset id={}", datasetId);
        } catch (Exception e) {
            log.warn("Failed to persist Visualization analysis result: {}", e.getMessage());
        }

        return dto;
    }

    private ChartDto buildHistogram(Column<?> col) {
        NumericColumn<?> numCol = (NumericColumn<?>) col;
        DoubleColumn values = numCol.asDoubleColumn();

        double min = values.min();
        double max = values.max();
        int bins = 20;
        double binWidth = (max - min) / bins;
        if (binWidth == 0) binWidth = 1;

        long[] counts = new long[bins];
        double[] binStarts = new double[bins];
        for (int i = 0; i < bins; i++) binStarts[i] = min + i * binWidth;

        for (int r = 0; r < values.size(); r++) {
            if (!values.isMissing(r)) {
                int bin = (int) ((values.get(r) - min) / binWidth);
                if (bin >= bins) bin = bins - 1;
                counts[bin]++;
            }
        }

        List<DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < bins; i++) {
            dataPoints.add(DataPoint.builder()
                .x(Math.round(binStarts[i] * 100.0) / 100.0)
                .y(Math.round((binStarts[i] + binWidth) * 100.0) / 100.0)
                .label(String.format("%.2f - %.2f", binStarts[i], binStarts[i] + binWidth))
                .count(counts[i])
                .build());
        }

        return ChartDto.builder()
            .chartType("HISTOGRAM")
            .title("Distribution of " + col.name())
            .xLabel(col.name())
            .yLabel("Count")
            .xColumn(col.name())
            .dataPoints(dataPoints)
            .build();
    }

    private ChartDto buildBoxPlotData(Column<?> col) {
        NumericColumn<?> numCol = (NumericColumn<?>) col;
        DoubleColumn sorted = numCol.asDoubleColumn().copy();
        sorted.sortAscending();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("min", numCol.min());
        meta.put("max", numCol.max());
        meta.put("mean", numCol.mean());
        meta.put("q1", percentile(sorted, 25));
        meta.put("median", percentile(sorted, 50));
        meta.put("q3", percentile(sorted, 75));
        double iqr = percentile(sorted, 75) - percentile(sorted, 25);
        meta.put("iqr", iqr);
        meta.put("lowerFence", percentile(sorted, 25) - 1.5 * iqr);
        meta.put("upperFence", percentile(sorted, 75) + 1.5 * iqr);

        return ChartDto.builder()
            .chartType("BOX")
            .title("Box Plot: " + col.name())
            .xLabel("Value")
            .yLabel(col.name())
            .xColumn(col.name())
            .metadata(meta)
            .dataPoints(Collections.emptyList())
            .build();
    }

    private ChartDto buildBarChart(Column<?> col) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int r = 0; r < col.size(); r++) {
            if (!col.isMissing(r)) {
                String val = col.getString(r);
                counts.merge(val, 1L, Long::sum);
            }
        }

        List<DataPoint> dataPoints = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(20)
            .map(e -> DataPoint.builder().label(e.getKey()).count(e.getValue()).build())
            .collect(Collectors.toList());

        return ChartDto.builder()
            .chartType("BAR")
            .title("Value Distribution: " + col.name())
            .xLabel(col.name())
            .yLabel("Count")
            .xColumn(col.name())
            .dataPoints(dataPoints)
            .build();
    }

    private ChartDto buildPieChart(Column<?> col) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (int r = 0; r < col.size(); r++) {
            if (!col.isMissing(r)) {
                String val = col.getString(r);
                counts.merge(val, 1L, Long::sum);
            }
        }

        List<DataPoint> dataPoints = counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
            .limit(8)
            .map(e -> DataPoint.builder().label(e.getKey()).count(e.getValue()).build())
            .collect(Collectors.toList());

        return ChartDto.builder()
            .chartType("PIE")
            .title("Proportion Breakdown: " + col.name())
            .xColumn(col.name())
            .dataPoints(dataPoints)
            .build();
    }

    private ChartDto buildScatterPlot(Column<?> col1, Column<?> col2) {
        NumericColumn<?> x = (NumericColumn<?>) col1;
        NumericColumn<?> y = (NumericColumn<?>) col2;
        int maxPoints = Math.min(1000, x.size());

        List<DataPoint> dataPoints = new ArrayList<>();
        for (int i = 0; i < maxPoints; i++) {
            if (!x.isMissing(i) && !y.isMissing(i)) {
                dataPoints.add(DataPoint.builder()
                    .x(x.getDouble(i))
                    .y(y.getDouble(i))
                    .build());
            }
        }

        return ChartDto.builder()
            .chartType("SCATTER")
            .title(col1.name() + " vs " + col2.name())
            .xLabel(col1.name())
            .yLabel(col2.name())
            .xColumn(col1.name())
            .yColumn(col2.name())
            .dataPoints(dataPoints)
            .build();
    }

    private double percentile(DoubleColumn sorted, double p) {
        int n = sorted.size();
        if (n == 0) return 0;
        double index = (p / 100.0) * (n - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) return sorted.get(lower);
        return sorted.get(lower) + (index - lower) * (sorted.get(upper) - sorted.get(lower));
    }
}
