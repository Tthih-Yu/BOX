package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.entity.ImportBatchEntity;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.ImportStatus;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.repository.MaterialMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImportServiceDataScopeTest {
    @Mock ImportBatchRepository batchRepository;
    @Mock ImportErrorRepository errorRepository;
    @Mock BasicDataService basicDataService;
    @Mock LabelService labelService;
    @Mock MaterialMappingRepository mappingRepository;
    @InjectMocks ImportService service;

    @BeforeEach
    void saveBatchReturnsArgument() {
        lenient().when(batchRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void csvFactoryValueFlowsThroughTrustedScopeWritePath() throws Exception {
        MockMultipartFile file = csv("""
                mappingOrder,lineMaterialCode,warehouseCode,quantity,deliveryType,deliveryArea,factory
                1,MAT-01,WH-01,10,NORMAL,T26 Floor,三山
                """);

        ImportBatchEntity batch = service.importExcel("mappings", file, "planner");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialMappingEntity>> rows = ArgumentCaptor.forClass(List.class);
        verify(basicDataService).upsertMappingsByWarehouseCode(rows.capture());
        assertEquals("三山", rows.getValue().get(0).getFactory(), "解析层保留文件值，由 BasicDataService 统一覆盖为登录可信工厂");
        assertEquals(ImportStatus.SUCCESS, batch.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"xlsx", "xlsm", "xls"})
    void excelFormatsUseTheSameScopedMappingWritePath(String extension) throws Exception {
        MockMultipartFile file = excel(extension);

        ImportBatchEntity batch = service.importExcel("mappings", file, "planner");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MaterialMappingEntity>> rows = ArgumentCaptor.forClass(List.class);
        verify(basicDataService).upsertMappingsByWarehouseCode(rows.capture());
        assertEquals(1, rows.getValue().size());
        assertEquals("弋江", rows.getValue().get(0).getFactory());
        assertEquals("T26 Floor", rows.getValue().get(0).getDeliveryArea());
        assertEquals(ImportStatus.SUCCESS, batch.getStatus());
    }

    @Test
    void blankDeliveryAreaIsRejectedInsteadOfDefaultingToOne() throws Exception {
        doAnswer(invocation -> {
            MaterialMappingEntity row = invocation.getArgument(0);
            if (row.getDeliveryArea() == null || row.getDeliveryArea().isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "配送区域不能为空");
            }
            return null;
        }).when(basicDataService).normalizeMapping(any());
        MockMultipartFile file = csv("""
                mappingOrder,lineMaterialCode,warehouseCode,quantity,deliveryType,deliveryArea,factory
                1,MAT-01,WH-01,10,NORMAL,,弋江
                """);

        ImportBatchEntity batch = service.importExcel("mappings", file, "planner");

        assertEquals(ImportStatus.FAILED, batch.getStatus());
        assertEquals(0, batch.getSuccessRows());
        assertEquals(1, batch.getFailedRows());
        verify(basicDataService, never()).upsertMappingsByWarehouseCode(anyList());
    }

    @Test
    void businessRejectionDoesNotFallbackToPartialRowWrites() throws Exception {
        AtomicInteger savedErrors = new AtomicInteger();
        when(errorRepository.saveAll(any())).thenAnswer(invocation -> {
            Iterable<?> errors = invocation.getArgument(0);
            errors.forEach(ignored -> savedErrors.incrementAndGet());
            return List.of();
        });
        doThrow(new BusinessException(ErrorCode.FORBIDDEN, "无权访问该数据"))
                .when(basicDataService).upsertMappingsByWarehouseCode(anyList());
        MockMultipartFile file = csv("""
                mappingOrder,lineMaterialCode,warehouseCode,quantity,deliveryType,deliveryArea,factory
                1,MAT-01,WH-01,10,NORMAL,T26 Floor,弋江
                2,MAT-02,WH-02,20,NORMAL,T26 Engine,三山
                """);

        ImportBatchEntity batch = service.importExcel("mappings", file, "planner");

        assertEquals(ImportStatus.FAILED, batch.getStatus());
        assertEquals(0, batch.getSuccessRows());
        assertEquals(2, batch.getFailedRows());
        verify(basicDataService, times(1)).upsertMappingsByWarehouseCode(anyList());
        verify(errorRepository).saveAll(any());
        assertEquals(2, savedErrors.get());
    }

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "mappings.csv", "text/csv",
                content.stripIndent().getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile excel(String extension) throws Exception {
        try (Workbook workbook = "xls".equals(extension) ? new HSSFWorkbook() : new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("mappings");
            Row header = sheet.createRow(0);
            String[] headers = {"mappingOrder", "lineMaterialCode", "warehouseCode", "quantity",
                    "deliveryType", "deliveryArea", "factory"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            Row data = sheet.createRow(1);
            Object[] values = {1, "MAT-01", "WH-01", 10, "NORMAL", "T26 Floor", "弋江"};
            for (int i = 0; i < values.length; i++) {
                if (values[i] instanceof Number number) data.createCell(i).setCellValue(number.doubleValue());
                else data.createCell(i).setCellValue(String.valueOf(values[i]));
            }
            workbook.write(out);
            return new MockMultipartFile("file", "mappings." + extension,
                    "application/octet-stream", out.toByteArray());
        }
    }
}
