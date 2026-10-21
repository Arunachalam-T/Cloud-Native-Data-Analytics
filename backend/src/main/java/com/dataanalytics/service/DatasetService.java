package com.dataanalytics.service;

import com.dataanalytics.exception.DatasetNotFoundException;
import com.dataanalytics.exception.DatasetProcessingException;
import com.dataanalytics.model.dto.DataPreviewDto;
import com.dataanalytics.model.dto.DatasetDto;
import com.dataanalytics.model.entity.ColumnMetadata;
import com.dataanalytics.model.entity.Dataset;
import com.dataanalytics.repository.ColumnMetadataRepository;
import com.dataanalytics.repository.DatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tech.tablesaw.api.Table;
import tech.tablesaw.io.csv.CsvReadOptions;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatasetService {

    private final DatasetRepository datasetRepository;
    private final ColumnMetadataRepository columnMetadataRepository;
    private final S3Service s3Service;

    /**
     * Upload a file: save to S3, create DB record, then kick off async analysis.
     */
    @Transactional
    public DatasetDto uploadDataset(MultipartFile file, String name, String description) {
        validateFile(file);

        String s3Key;
        try {
            s3Key = s3Service.uploadFile(file, name);
        } catch (IOException e) {
            throw new DatasetProcessingException("Failed to upload file to S3: " + e.getMessage(), e);
        }

        String presignedUrl = s3Service.generatePresignedUrl(s3Key);

        Dataset dataset = Dataset.builder()
            .name(name != null ? name : file.getOriginalFilename())
            .originalFilename(file.getOriginalFilename())
            .s3Key(s3Key)
            .s3Url(presignedUrl)
            .fileFormat(detectFormat(file.getOriginalFilename()))
            .fileSizeBytes(file.getSize())
            .status(Dataset.DatasetStatus.PROCESSING)
            .description(description)
            .build();

        Dataset saved = datasetRepository.save(dataset);
        log.info("Dataset record created: id={}, name={}", saved.getId(), saved.getName());

        // Trigger async processing
        processDatasetAsync(saved.getId(), s3Key);

        return mapToDto(saved, false);
    }

    /**
     * Asynchronously parse the dataset, extract column metadata, and update status.
     */
    @Async
    public void processDatasetAsync(Long datasetId, String s3Key) {
        try {
            log.info("Starting async processing for dataset id={}", datasetId);
            InputStream inputStream = s3Service.downloadFile(s3Key);

            Table table = Table.read().csv(
                CsvReadOptions.builder(inputStream).build()
            );

            Dataset dataset = datasetRepository.findById(datasetId)
                .orElseThrow(() -> new DatasetNotFoundException("Dataset not found: " + datasetId));

            dataset.setRowCount((long) table.rowCount());
            dataset.setColumnCount(table.columnCount());
            dataset.setStatus(Dataset.DatasetStatus.READY);

            // Extract column metadata
            List<ColumnMetadata> columns = new ArrayList<>();
            for (int i = 0; i < table.columnCount(); i++) {
                var col = table.column(i);
                var meta = ColumnMetadata.builder()
                    .dataset(dataset)
                    .columnName(col.name())
                    .dataType(col.type().name())
                    .nullCount((long) col.countMissing())
                    .uniqueCount((long) col.countUnique())
                    .columnIndex(i)
                    .build();
                columns.add(meta);
            }
            columnMetadataRepository.saveAll(columns);
            datasetRepository.save(dataset);

            log.info("Dataset processing complete: id={}, rows={}, cols={}",
                datasetId, table.rowCount(), table.columnCount());

        } catch (Exception e) {
            log.error("Dataset processing failed for id={}: {}", datasetId, e.getMessage(), e);
            datasetRepository.findById(datasetId).ifPresent(d -> {
                d.setStatus(Dataset.DatasetStatus.ERROR);
                datasetRepository.save(d);
            });
        }
    }

    @Transactional(readOnly = true)
    public Page<DatasetDto> getAllDatasets(Pageable pageable) {
        return datasetRepository
            .findByStatusNotOrderByCreatedAtDesc(Dataset.DatasetStatus.DELETED, pageable)
            .map(d -> mapToDto(d, false));
    }

    @Transactional(readOnly = true)
    public DatasetDto getDatasetById(Long id) {
        Dataset dataset = findDataset(id);
        return mapToDto(dataset, true);
    }

    @Transactional(readOnly = true)
    public DataPreviewDto getPreview(Long id, int rows) {
        Dataset dataset = findDataset(id);

        try (InputStream is = s3Service.downloadFile(dataset.getS3Key())) {
            Table table = Table.read().csv(CsvReadOptions.builder(is).build());
            int displayRows = Math.min(rows, table.rowCount());
            Table preview = table.rows(0, displayRows);

            List<String> headers = table.columnNames();
            Map<String, String> columnTypes = new LinkedHashMap<>();
            table.columns().forEach(c -> columnTypes.put(c.name(), c.type().name()));

            List<List<String>> rowData = new ArrayList<>();
            for (int r = 0; r < preview.rowCount(); r++) {
                List<String> row = new ArrayList<>();
                for (var col : preview.columns()) {
                    Object val = col.get(r);
                    row.add(val == null ? "" : val.toString());
                }
                rowData.add(row);
            }

            return DataPreviewDto.builder()
                .datasetId(id)
                .headers(headers)
                .rows(rowData)
                .totalRows((long) table.rowCount())
                .displayedRows(displayRows)
                .totalColumns(table.columnCount())
                .columnTypes(columnTypes)
                .build();

        } catch (IOException e) {
            throw new DatasetProcessingException("Failed to load dataset for preview: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void deleteDataset(Long id) {
        Dataset dataset = findDataset(id);
        s3Service.deleteFile(dataset.getS3Key());
        dataset.setStatus(Dataset.DatasetStatus.DELETED);
        datasetRepository.save(dataset);
        log.info("Soft-deleted dataset id={}", id);
    }

    // ---------- helpers ----------

    private Dataset findDataset(Long id) {
        return datasetRepository.findById(id)
            .filter(d -> d.getStatus() != Dataset.DatasetStatus.DELETED)
            .orElseThrow(() -> new DatasetNotFoundException("Dataset not found: " + id));
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DatasetProcessingException("File is empty or missing", null);
        }
        String filename = file.getOriginalFilename();
        if (filename == null) return;
        String lower = filename.toLowerCase();
        if (!lower.endsWith(".csv") && !lower.endsWith(".xlsx") && !lower.endsWith(".xls") && !lower.endsWith(".tsv")) {
            throw new DatasetProcessingException("Unsupported file format. Supported: CSV, Excel, TSV", null);
        }
    }

    private Dataset.FileFormat detectFormat(String filename) {
        if (filename == null) return Dataset.FileFormat.CSV;
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return Dataset.FileFormat.EXCEL;
        if (lower.endsWith(".tsv")) return Dataset.FileFormat.TSV;
        return Dataset.FileFormat.CSV;
    }

    private DatasetDto mapToDto(Dataset d, boolean includeColumns) {
        DatasetDto.DatasetDtoBuilder builder = DatasetDto.builder()
            .id(d.getId())
            .name(d.getName())
            .originalFilename(d.getOriginalFilename())
            .s3Url(d.getS3Url())
            .fileFormat(d.getFileFormat())
            .fileSizeBytes(d.getFileSizeBytes())
            .rowCount(d.getRowCount())
            .columnCount(d.getColumnCount())
            .status(d.getStatus())
            .description(d.getDescription())
            .createdAt(d.getCreatedAt())
            .updatedAt(d.getUpdatedAt());

        if (includeColumns) {
            List<DatasetDto.ColumnMetadataDto> cols = columnMetadataRepository
                .findByDatasetIdOrderByColumnIndex(d.getId())
                .stream()
                .map(c -> DatasetDto.ColumnMetadataDto.builder()
                    .id(c.getId())
                    .columnName(c.getColumnName())
                    .dataType(c.getDataType())
                    .nullCount(c.getNullCount())
                    .uniqueCount(c.getUniqueCount())
                    .minValue(c.getMinValue())
                    .maxValue(c.getMaxValue())
                    .meanValue(c.getMeanValue())
                    .stdDev(c.getStdDev())
                    .columnIndex(c.getColumnIndex())
                    .build())
                .collect(Collectors.toList());
            builder.columns(cols);
        }

        return builder.build();
    }
}
