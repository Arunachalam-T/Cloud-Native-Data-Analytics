package com.dataanalytics.service;

import com.dataanalytics.exception.DatasetNotFoundException;
import com.dataanalytics.model.entity.Dataset;
import com.dataanalytics.repository.ColumnMetadataRepository;
import com.dataanalytics.repository.DatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatasetService Unit Tests")
class DatasetServiceTest {

    @Mock private DatasetRepository datasetRepository;
    @Mock private ColumnMetadataRepository columnMetadataRepository;
    @Mock private S3Service s3Service;

    @InjectMocks
    private DatasetService datasetService;

    private Dataset sampleDataset;

    @BeforeEach
    void setUp() {
        sampleDataset = Dataset.builder()
            .id(1L)
            .name("Test Dataset")
            .originalFilename("test.csv")
            .s3Key("datasets/uuid/test.csv")
            .s3Url("https://s3.amazonaws.com/bucket/test.csv")
            .fileFormat(Dataset.FileFormat.CSV)
            .fileSizeBytes(1024L)
            .rowCount(100L)
            .columnCount(5)
            .status(Dataset.DatasetStatus.READY)
            .build();
    }

    @Test
    @DisplayName("Should return all non-deleted datasets")
    void getAllDatasets_returnsPageOfDatasets() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Dataset> mockPage = new PageImpl<>(List.of(sampleDataset));
        when(datasetRepository.findByStatusNotOrderByCreatedAtDesc(Dataset.DatasetStatus.DELETED, pageable))
            .thenReturn(mockPage);

        var result = datasetService.getAllDatasets(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Test Dataset");
        verify(datasetRepository, times(1))
            .findByStatusNotOrderByCreatedAtDesc(Dataset.DatasetStatus.DELETED, pageable);
    }

    @Test
    @DisplayName("Should throw DatasetNotFoundException for non-existent dataset")
    void getDatasetById_throwsWhenNotFound() {
        when(datasetRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> datasetService.getDatasetById(999L))
            .isInstanceOf(DatasetNotFoundException.class)
            .hasMessageContaining("999");
    }

    @Test
    @DisplayName("Should return dataset DTO for valid ID")
    void getDatasetById_returnsDto() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(sampleDataset));
        when(columnMetadataRepository.findByDatasetIdOrderByColumnIndex(1L)).thenReturn(List.of());

        var result = datasetService.getDatasetById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Test Dataset");
        assertThat(result.getStatus()).isEqualTo(Dataset.DatasetStatus.READY);
    }

    @Test
    @DisplayName("Should soft-delete dataset and call S3 deletion")
    void deleteDataset_softDeletesAndCallsS3() {
        when(datasetRepository.findById(1L)).thenReturn(Optional.of(sampleDataset));
        doNothing().when(s3Service).deleteFile(anyString());
        when(datasetRepository.save(any())).thenReturn(sampleDataset);

        datasetService.deleteDataset(1L);

        verify(s3Service).deleteFile("datasets/uuid/test.csv");
        verify(datasetRepository).save(argThat(d -> d.getStatus() == Dataset.DatasetStatus.DELETED));
    }

    @Test
    @DisplayName("Should reject unsupported file format")
    void uploadDataset_rejectsInvalidFileFormat() {
        MockMultipartFile badFile = new MockMultipartFile(
            "file", "data.xml", "application/xml", "content".getBytes()
        );

        assertThatThrownBy(() -> datasetService.uploadDataset(badFile, "test", null))
            .isInstanceOf(com.dataanalytics.exception.DatasetProcessingException.class)
            .hasMessageContaining("Unsupported file format");
    }
}
