package com.dataanalytics.service;

import com.dataanalytics.exception.DatasetNotFoundException;
import com.dataanalytics.exception.DatasetProcessingException;
import com.dataanalytics.model.dto.EdaReportDto;
import com.dataanalytics.model.entity.Dataset;
import com.dataanalytics.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;
import tech.tablesaw.io.csv.CsvReadOptions;

import com.dataanalytics.model.entity.AnalysisResult;
import com.dataanalytics.repository.AnalysisResultRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final DatasetRepository datasetRepository;
    private final S3Service s3Service;
    private final AnalysisResultRepository analysisResultRepository;
    private final ObjectMapper objectMapper;

    public EdaReportDto generateEdaReport(Long datasetId) {
        var cached = analysisResultRepository.findByDatasetIdAndResultType(datasetId, AnalysisResult.ResultType.EDA);
        if (cached.isPresent()) {
            log.info("Returning cached EDA report for dataset id={}", datasetId);
            try {
                return objectMapper.convertValue(cached.get().getResultData(), EdaReportDto.class);
            } catch (Exception e) {
                log.warn("Failed to parse cached EDA report, recomputing: {}", e.getMessage());
            }
        }

        Dataset dataset = findReadyDataset(datasetId);
        Table table = loadTable(dataset.getS3Key());

        long duplicateRows = table.rowCount() - table.dropDuplicateRows().rowCount();
        long totalMissing = table.columns().stream().mapToLong(c -> (long) c.countMissing()).sum();
        double missingPct = table.rowCount() == 0 ? 0
            : (totalMissing * 100.0) / ((long) table.rowCount() * table.columnCount());

        List<EdaReportDto.ColumnEdaDto> columnReports = new ArrayList<>();
        for (var col : table.columns()) {
            columnReports.add(analyzeColumn(col, table.rowCount()));
        }

        // Build simple correlation matrix for numeric columns
        Map<String, Object> correlationMatrix = buildCorrelationMatrix(table);

        EdaReportDto report = EdaReportDto.builder()
            .datasetId(datasetId)
            .datasetName(dataset.getName())
            .rowCount((long) table.rowCount())
            .columnCount(table.columnCount())
            .duplicateRows(duplicateRows)
            .totalMissingValues(totalMissing)
            .missingValuePercentage(Math.round(missingPct * 100.0) / 100.0)
            .columns(columnReports)
            .correlationMatrix(correlationMatrix)
            .build();

        try {
            Map<String, Object> dataMap = objectMapper.convertValue(report, new TypeReference<Map<String, Object>>() {});
            AnalysisResult result = analysisResultRepository
                .findByDatasetIdAndResultType(datasetId, AnalysisResult.ResultType.EDA)
                .orElseGet(() -> AnalysisResult.builder()
                    .dataset(dataset)
                    .resultType(AnalysisResult.ResultType.EDA)
                    .build());
            result.setResultData(dataMap);
            result.setComputedAt(java.time.OffsetDateTime.now());
            analysisResultRepository.save(result);
            log.info("Stored EDA report in analysis_results table for dataset id={}", datasetId);
        } catch (Exception e) {
            log.warn("Failed to save EDA report to analysis_results: {}", e.getMessage());
        }

        return report;
    }

    private EdaReportDto.ColumnEdaDto analyzeColumn(Column<?> col, int totalRows) {
        EdaReportDto.ColumnEdaDto.ColumnEdaDtoBuilder builder = EdaReportDto.ColumnEdaDto.builder()
            .columnName(col.name())
            .dataType(col.type().name())
            .nullCount((long) col.countMissing())
            .nullPercentage(totalRows == 0 ? 0 : Math.round(col.countMissing() * 10000.0 / totalRows) / 100.0)
            .uniqueCount((long) col.countUnique())
            .uniquePercentage(totalRows == 0 ? 0 : Math.round(col.countUnique() * 10000.0 / totalRows) / 100.0);

        if (col instanceof NumericColumn<?> numCol) {
            builder
                .min(numCol.min())
                .max(numCol.max())
                .mean(roundTo4(numCol.mean()))
                .stdDev(roundTo4(numCol.standardDeviation()))
                .variance(roundTo4(numCol.variance()));

            // Quartiles via sorted values
            try {
                DoubleColumn sorted = numCol.asDoubleColumn().copy();
                sorted.sortAscending();
                int n = sorted.size();
                if (n > 0) {
                    builder.median(roundTo4(percentile(sorted, 50)));
                    builder.q1(roundTo4(percentile(sorted, 25)));
                    builder.q3(roundTo4(percentile(sorted, 75)));
                    double q1 = percentile(sorted, 25);
                    double q3 = percentile(sorted, 75);
                    double iqr = q3 - q1;
                    builder.iqr(roundTo4(iqr));

                    // IQR-based outlier detection
                    double lower = q1 - 1.5 * iqr;
                    double upper = q3 + 1.5 * iqr;
                    List<Double> outliers = sorted.asList().stream()
                        .filter(v -> v != null && (v < lower || v > upper))
                        .distinct()
                        .limit(20)
                        .collect(Collectors.toList());
                    builder.outliers(outliers);
                }
            } catch (Exception ignored) {}

        } else if (col instanceof StringColumn || col instanceof TextColumn) {
            // Value counts for categoricals (top 20)
            Map<String, Long> rawCounts = new LinkedHashMap<>();
            for (int r = 0; r < col.size(); r++) {
                if (!col.isMissing(r)) {
                    String val = col.getString(r);
                    rawCounts.merge(val, 1L, Long::sum);
                }
            }

            String mostFrequent = null;
            long mostFreqCount = 0;
            for (var entry : rawCounts.entrySet()) {
                if (entry.getValue() > mostFreqCount) {
                    mostFreqCount = entry.getValue();
                    mostFrequent = entry.getKey();
                }
            }

            Map<String, Long> valueCounts = rawCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                    (e1, e2) -> e1, LinkedHashMap::new));

            builder.valueCounts(valueCounts)
                   .mostFrequentValue(mostFrequent)
                   .mostFrequentCount(mostFreqCount);
        }

        return builder.build();
    }

    private Map<String, Object> buildCorrelationMatrix(Table table) {
        Map<String, Object> matrix = new LinkedHashMap<>();
        List<String> numericCols = table.columns().stream()
            .filter(c -> c instanceof NumericColumn<?>)
            .map(Column::name)
            .collect(Collectors.toList());

        for (String col1 : numericCols) {
            Map<String, Double> row = new LinkedHashMap<>();
            for (String col2 : numericCols) {
                try {
                    DoubleColumn c1 = table.numberColumn(col1).asDoubleColumn();
                    DoubleColumn c2 = table.numberColumn(col2).asDoubleColumn();
                    double corr = pearsonCorrelation(c1.asDoubleArray(), c2.asDoubleArray());
                    row.put(col2, roundTo4(corr));
                } catch (Exception e) {
                    row.put(col2, null);
                }
            }
            matrix.put(col1, row);
        }
        return matrix;
    }

    private double pearsonCorrelation(double[] x, double[] y) {
        int n = Math.min(x.length, y.length);
        if (n == 0) return 0;

        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(x[i]) && !Double.isNaN(y[i])) {
                sumX += x[i];
                sumY += y[i];
                sumXY += x[i] * y[i];
                sumX2 += x[i] * x[i];
                sumY2 += y[i] * y[i];
            }
        }
        double denom = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));
        return denom == 0 ? 0 : (n * sumXY - sumX * sumY) / denom;
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

    private Double roundTo4(double val) {
        return Double.isNaN(val) || Double.isInfinite(val) ? null : Math.round(val * 10000.0) / 10000.0;
    }

    Table loadTable(String s3Key) {
        try (InputStream is = s3Service.downloadFile(s3Key)) {
            return Table.read().csv(CsvReadOptions.builder(is).build());
        } catch (IOException e) {
            throw new DatasetProcessingException("Failed to load dataset from S3: " + e.getMessage(), e);
        }
    }

    private Dataset findReadyDataset(Long id) {
        return datasetRepository.findById(id)
            .filter(d -> d.getStatus() == Dataset.DatasetStatus.READY)
            .orElseThrow(() -> new DatasetNotFoundException("Dataset not found or not ready: " + id));
    }
}
