package com.example.materialpull.controller;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.ImportBatchEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.service.DataScopeService;
import com.example.materialpull.service.ImportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportControllerDataScopeTest {

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void importBatchMetadataRequiresGlobalAdmin() {
        DataScopeService scope = mock(DataScopeService.class);
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        when(batches.findAll(org.mockito.ArgumentMatchers.any(Pageable.class))).thenReturn(Page.empty());
        ImportController controller = new ImportController(
                mock(ImportService.class), batches,
                mock(ImportErrorRepository.class), scope);

        controller.batches();

        verify(scope).requireGlobalAdmin();
    }

    @Test
    void globalAdminCanReadImportErrorsWithoutBatchOwnershipCheck() {
        DataScopeService scope = mock(DataScopeService.class);
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        ImportErrorRepository errors = mock(ImportErrorRepository.class);
        when(scope.isGlobalAdmin()).thenReturn(true);
        RequestContext.setLoginUser(1L, "root", "最高管理员", UserRole.ADMIN, null, java.util.List.of());
        ImportController controller = new ImportController(
                mock(ImportService.class), batches, errors, scope);

        controller.errors("batch-1");

        verify(errors).findByBatchNoOrderByRowNoAsc("batch-1");
        verify(batches, never()).findByBatchNo("batch-1");
    }

    @Test
    void subAdminCanReadOwnMappingImportErrors() {
        DataScopeService scope = mock(DataScopeService.class);
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        ImportErrorRepository errors = mock(ImportErrorRepository.class);
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setImportType("mappings");
        batch.setOperator("普通管理员");
        when(batches.findByBatchNo("batch-1")).thenReturn(Optional.of(batch));
        RequestContext.setLoginUser(2L, "sub", "普通管理员", UserRole.SUB_ADMIN,
                "弋江", java.util.List.of());
        ImportController controller = new ImportController(mock(ImportService.class), batches, errors, scope);

        controller.errors("batch-1");

        verify(errors).findByBatchNoOrderByRowNoAsc("batch-1");
    }

    @Test
    void subAdminCannotReadAnotherOperatorsImportErrors() {
        DataScopeService scope = mock(DataScopeService.class);
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        ImportErrorRepository errors = mock(ImportErrorRepository.class);
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setImportType("mappings");
        batch.setOperator("其他管理员");
        when(batches.findByBatchNo("batch-1")).thenReturn(Optional.of(batch));
        RequestContext.setLoginUser(2L, "sub", "普通管理员", UserRole.SUB_ADMIN,
                "弋江", java.util.List.of());
        ImportController controller = new ImportController(mock(ImportService.class), batches, errors, scope);

        assertThrows(BusinessException.class, () -> controller.errors("batch-1"));

        verify(errors, never()).findByBatchNoOrderByRowNoAsc("batch-1");
    }

    @Test
    void plannerCannotImportMappings() {
        ImportService importService = mock(ImportService.class);
        RequestContext.setLoginUser(3L, "planner", "计划员", UserRole.PLANNER,
                "弋江", java.util.List.of());
        ImportController controller = new ImportController(importService, mock(ImportBatchRepository.class),
                mock(ImportErrorRepository.class), mock(DataScopeService.class));
        MockMultipartFile file = new MockMultipartFile("file", "mappings.csv", "text/csv", new byte[]{1});

        assertThrows(BusinessException.class, () -> controller.upload("mappings", file, false));
    }

    @Test
    void subAdminCanImportMappings() throws Exception {
        ImportService importService = mock(ImportService.class);
        RequestContext.setLoginUser(2L, "sub", "普通管理员", UserRole.SUB_ADMIN,
                "弋江", java.util.List.of());
        ImportController controller = new ImportController(importService, mock(ImportBatchRepository.class),
                mock(ImportErrorRepository.class), mock(DataScopeService.class));
        MockMultipartFile file = new MockMultipartFile("file", "mappings.csv", "text/csv", new byte[]{1});

        controller.upload("mappings", file, false);

        verify(importService).importExcel("mappings", file, "普通管理员", false);
    }
}
