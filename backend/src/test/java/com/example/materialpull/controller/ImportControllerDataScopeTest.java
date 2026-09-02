package com.example.materialpull.controller;

import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.service.DataScopeService;
import com.example.materialpull.service.ImportService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportControllerDataScopeTest {

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
    void importErrorsRequireGlobalAdmin() {
        DataScopeService scope = mock(DataScopeService.class);
        ImportController controller = new ImportController(
                mock(ImportService.class), mock(ImportBatchRepository.class),
                mock(ImportErrorRepository.class), scope);

        controller.errors("batch-1");

        verify(scope).requireGlobalAdmin();
    }
}
