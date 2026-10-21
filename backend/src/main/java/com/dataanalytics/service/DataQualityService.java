package com.dataanalytics.service;

import com.dataanalytics.exception.DatasetNotFoundException;
import com.dataanalytics.model.dto.DataQualityReportDto;
import com.dataanalytics.model.entity.Dataset;
import com.dataanalytics.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.tablesaw.api.*;
import tech.tablesaw.columns.Column;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataQualityService {

    private final DatasetRepository datasetRepository;
    private final AnalyticsService analyticsService;

    public DataQualityReportDto generateQualityReport(Long datasetId) {
        Dataset dataset = datasetRepository.findById(datasetId)
            .filter(d -> d.getStatus() == Dataset.DatasetStatus.READY)
            .orElseThrow(() -> new DatasetNotFoundException("Dataset not found or not ready: " + datasetId));

        Table table = analyticsService.loadTable(dataset.getS3Key());

        long duplicateRows = table.rowCount() - table.dropDuplicateRows().rowCount();
        List<DataQualityReportDto.ColumnQualityDto> columnQuality = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();

        for (var col : table.columns()) {
            columnQuality.add(analyzeColumnQuality(col, table.rowCount(), warnings, recommendations));
        }

        if (duplicateRows > 0) {
            warnings.add(String.format("Found %d duplicate rows (%.1f%% of dataset).",
                duplicateRows, 100.0 * duplicateRows / table.rowCount()));
            recommendations.add("Consider removing duplicate rows before analysis.");
        }

        double overallScore = columnQuality.stream()
            .mapToDouble(c -> c.getQualityScore() != null ? c.getQualityScore() : 0)
            .average()
            .orElse(100.0);

        return DataQualityReportDto.builder()
            .datasetId(datasetId)
            .datasetName(dataset.getName())
            .totalRows((long) table.rowCount())
            .totalColumns(table.columnCount())
            .overallQualityScore(Math.round(overallScore * 100.0) / 100.0)
            .duplicateRowCount(duplicateRows)
            .columnQuality(columnQuality)
            .warnings(warnings)
            .recommendations(recommendations)
            .build();
    }

    private DataQualityReportDto.ColumnQualityDto analyzeColumnQuality(
            Column<?> col, int totalRows,
            List<String> warnings, List<String> recommendations) {

        long nullCount = col.countMissing();
        double nullPct = totalRows == 0 ? 0 : 100.0 * nullCount / totalRows;
        long uniqueCount = col.countUnique();
        long duplicateCount = totalRows - uniqueCount;
        List<String> issues = new ArrayList<>();

        // Detect outliers for numeric columns
        boolean hasOutliers = false;
        long outlierCount = 0;
        if (col instanceof NumericColumn<?> numCol) {
            try {
                DoubleColumn sorted = numCol.asDoubleColumn().copy();
                sorted.sortAscending();
                if (sorted.size() > 3) {
                    double q1 = percentile(sorted, 25);
                    double q3 = percentile(sorted, 75);
                    double iqr = q3 - q1;
                    double lower = q1 - 1.5 * iqr;
                    double upper = q3 + 1.5 * iqr;
                    outlierCount = sorted.asList().stream()
                        .filter(v -> v != null && (v < lower || v > upper)).count();
                    hasOutliers = outlierCount > 0;
                }
            } catch (Exception ignored) {}
        }

        // Score calculation (starts at 100, deduct for issues)
        double score = 100.0;

        if (nullPct > 50) {
            score -= 40;
            issues.add(String.format("High missing value rate: %.1f%%", nullPct));
            warnings.add(String.format("Column '%s' has %.1f%% missing values.", col.name(), nullPct));
            recommendations.add(String.format("Consider imputing or dropping column '%s'.", col.name()));
        } else if (nullPct > 20) {
            score -= 20;
            issues.add(String.format("Moderate missing values: %.1f%%", nullPct));
        } else if (nullPct > 5) {
            score -= 10;
            issues.add(String.format("Some missing values: %.1f%%", nullPct));
        }

        if (hasOutliers) {
            score -= Math.min(20, outlierCount * 2.0);
            issues.add(String.format("Contains %d outliers (IQR method)", outlierCount));
        }

        if (uniqueCount == 1) {
            score -= 30;
            issues.add("Constant column — all values are the same");
            recommendations.add(String.format("Column '%s' is constant and may not be informative.", col.name()));
        }

        return DataQualityReportDto.ColumnQualityDto.builder()
            .columnName(col.name())
            .dataType(col.type().name())
            .nullCount(nullCount)
            .nullPercentage(Math.round(nullPct * 100.0) / 100.0)
            .uniqueCount(uniqueCount)
            .duplicateCount(duplicateCount)
            .hasOutliers(hasOutliers)
            .outlierCount(outlierCount)
            .qualityScore(Math.max(0, Math.round(score * 100.0) / 100.0))
            .issues(issues)
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
