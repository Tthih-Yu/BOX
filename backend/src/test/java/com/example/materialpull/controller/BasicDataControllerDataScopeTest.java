package com.example.materialpull.controller;

import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.repository.*;
import com.example.materialpull.service.BasicDataService;
import com.example.materialpull.service.LogExportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class BasicDataControllerDataScopeTest {
    @Mock MaterialRepository materialRepository;
    @Mock MaterialMappingRepository mappingRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock BoxRepository boxRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock SystemConfigRepository configRepository;
    @Mock BasicDataService service;
    @Mock LogExportService logExportService;
    @Mock com.example.materialpull.service.DataScopeService dataScopeService;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        BasicDataController controller = new BasicDataController(materialRepository, mappingRepository,
                stationMaterialRepository, boxRepository, inventoryRepository, configRepository,
                service, logExportService, dataScopeService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void pagedMappingsEndpointDelegatesToScopedService() throws Exception {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setId(11L);
        mapping.setFactory("弋江");
        mapping.setDeliveryArea("T26 Floor");
        when(service.mappings(eq("MAT"), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(mapping), PageRequest.of(0, 20), 1));

        mvc.perform(get("/mappings").param("page", "0").param("size", "20").param("keyword", "MAT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].factory").value("弋江"))
                .andExpect(jsonPath("$.data.items[0].deliveryArea").value("T26 Floor"));

        verify(service).mappings(eq("MAT"), any(PageRequest.class));
        verifyNoInteractions(mappingRepository);
    }

    @Test
    void exportEndpointGetsRowsFromScopedService() throws Exception {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setFactory("弋江");
        mapping.setDeliveryArea("T26 Floor");
        byte[] workbook = {1, 2, 3};
        when(service.exportMappings()).thenReturn(List.of(mapping));
        when(logExportService.exportToXlsx(anyString(), anyList(), anyList(), anyList())).thenReturn(workbook);
        when(logExportService.buildFileName("mappings")).thenReturn("mappings-test.xlsx");

        mvc.perform(get("/mappings/export"))
                .andExpect(status().isOk())
                .andExpect(content().bytes(workbook))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("mappings-test.xlsx")));

        verify(service).exportMappings();
        verifyNoInteractions(mappingRepository);
    }
}
