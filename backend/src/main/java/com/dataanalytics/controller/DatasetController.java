package com.dataanalytics.controller;

import com.dataanalytics.model.dto.*;
import com.dataanalytics.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/datasets")
@RequiredArgsConstructor
public class DatasetController {

    private final DatasetService datasetService;
    private final AnalyticsService analyticsService;
    private final DataQualityService dataQualityService;
    private final VisualizationService visualizationService;
    private final S3Service s3Service;

    // ─────────── Dataset CRUD ───────────

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DatasetDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description) {

        DatasetDto dto = datasetService.uploadDataset(file, name, description);
        return ResponseEntity.accepted().body(dto);
    }

    @GetMapping
    public ResponseEntity<Page<DatasetDto>> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(datasetService.getAllDatasets(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DatasetDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(datasetService.getDatasetById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        datasetService.deleteDataset(id);
        return ResponseEntity.noContent().build();
    }

    // ─────────── Data Preview ───────────

    @GetMapping("/{id}/preview")
    public ResponseEntity<DataPreviewDto> preview(
            @PathVariable Long id,
            @RequestParam(defaultValue = "50") int rows) {
        return ResponseEntity.ok(datasetService.getPreview(id, rows));
    }

    // ─────────── EDA ───────────

    @GetMapping("/{id}/eda")
    public ResponseEntity<EdaReportDto> eda(@PathVariable Long id) {
        return ResponseEntity.ok(analyticsService.generateEdaReport(id));
    }

    // ─────────── Data Quality ───────────

    @GetMapping("/{id}/quality")
    public ResponseEntity<DataQualityReportDto> quality(@PathVariable Long id) {
        return ResponseEntity.ok(dataQualityService.generateQualityReport(id));
    }

    // ─────────── Visualizations ───────────

    @GetMapping("/{id}/visualizations")
    public ResponseEntity<VisualizationDto> visualizations(@PathVariable Long id) {
        return ResponseEntity.ok(visualizationService.generateVisualizations(id));
    }

    // ─────────── Download ───────────

    @GetMapping("/{id}/download")
    public ResponseEntity<String> download(@PathVariable Long id) {
        DatasetDto dataset = datasetService.getDatasetById(id);
        String presignedUrl = s3Service.generatePresignedUrl(
            datasetService.getDatasetById(id).getOriginalFilename()
        );
        // Return the presigned URL as JSON for the JavaFX client to open
        return ResponseEntity.ok(Map.of("downloadUrl", presignedUrl).toString());
    }

    // ─────────── Health / Status ───────────

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long id) {
        DatasetDto dto = datasetService.getDatasetById(id);
        return ResponseEntity.ok(Map.of(
            "id", dto.getId(),
            "status", dto.getStatus(),
            "rowCount", dto.getRowCount() != null ? dto.getRowCount() : 0,
            "columnCount", dto.getColumnCount() != null ? dto.getColumnCount() : 0
        ));
    }
}
