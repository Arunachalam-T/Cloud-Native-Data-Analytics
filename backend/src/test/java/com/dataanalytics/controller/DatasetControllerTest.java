package com.dataanalytics.controller;

import com.dataanalytics.model.dto.DatasetDto;
import com.dataanalytics.model.entity.Dataset;
import com.dataanalytics.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DatasetController.class)
@DisplayName("DatasetController Integration Tests")
class DatasetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DatasetService datasetService;
    @MockBean private AnalyticsService analyticsService;
    @MockBean private DataQualityService dataQualityService;
    @MockBean private VisualizationService visualizationService;
    @MockBean private S3Service s3Service;

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/datasets should return 200 with list")
    void listAll_returns200() throws Exception {
        DatasetDto dto = DatasetDto.builder()
            .id(1L)
            .name("Sample")
            .status(Dataset.DatasetStatus.READY)
            .rowCount(100L)
            .columnCount(5)
            .build();

        when(datasetService.getAllDatasets(any(PageRequest.class)))
            .thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/api/v1/datasets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].name").value("Sample"));
    }

    @Test
    @DisplayName("GET /api/v1/datasets without auth should return 401")
    void listAll_unauthorizedReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/datasets"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/v1/datasets/{id} should return dataset detail")
    void getById_returnsDataset() throws Exception {
        DatasetDto dto = DatasetDto.builder()
            .id(42L)
            .name("Analytics Data")
            .status(Dataset.DatasetStatus.READY)
            .build();

        when(datasetService.getDatasetById(42L)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/datasets/42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.name").value("Analytics Data"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("DELETE /api/v1/datasets/{id} should return 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/datasets/1"))
            .andExpect(status().isNoContent());
    }
}
