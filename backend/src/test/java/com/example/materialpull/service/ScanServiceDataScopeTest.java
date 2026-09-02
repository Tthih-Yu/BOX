package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.ScanDtos;
import com.example.materialpull.entity.LabelEntity;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import com.example.materialpull.resilience.BusinessLockService;
import com.example.materialpull.resilience.IdempotencyService;
import com.example.materialpull.resilience.OperationGuard;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScanServiceDataScopeTest {
    @Mock LabelRepository labelRepository;
    @Mock LabelResolverService labelResolverService;
    @Mock BoxRepository boxRepository;
    @Mock ReplenishmentTaskRepository taskRepository;
    @Mock AuditService auditService;
    @Mock RealtimePushService pushService;
    @Mock SystemAlertService alertService;
    @Mock BusinessLockService lockService;
    @Mock IdempotencyService idempotencyService;
    @Mock OperationGuard guard;
    @Mock AppProperties properties;
    @Mock TaskService taskService;
    @Mock ExceptionEventRepository exceptionEventRepository;
    @Mock MaterialMappingRepository mappingRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock SystemConfigRepository configRepository;
    @Mock MaterialRepository materialRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();

    @InjectMocks ScanService service;

    @org.junit.jupiter.api.AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void resolveScopeRejectsWhenNoMappingExists() {
        when(mappingRepository.findByWarehouseCodeInAndEnabledTrue(List.of("WH-404"))).thenReturn(List.of());
        when(mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc("MAT-404"))
                .thenReturn(List.of());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveMappingScope("WH-404", "MAT-404"));

        assertEquals(ErrorCode.DATA_DIRTY, exception.getErrorCode());
    }

    @Test
    void historicalSnapshotDoesNotDependOnMappingAfterDeletion() {
        ScanService.MappingScope scope = service.snapshotOrMappingScope("弋江", "T26 Floor", "WH-DELETED", "MAT-1");

        assertEquals("弋江", scope.factory());
        assertEquals("T26 Floor", scope.deliveryArea());
        verifyNoInteractions(mappingRepository);
    }

    @Test
    void resolveScopeRejectsMappingWithMissingDeliveryArea() {
        MaterialMappingEntity mapping = mapping(1L, "WH-1", "MAT-1", "弋江", null, "NORMAL");
        when(mappingRepository.findByWarehouseCodeInAndEnabledTrue(List.of("WH-1")))
                .thenReturn(List.of(mapping));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveMappingScope("WH-1", "MAT-1"));

        assertEquals(ErrorCode.DATA_DIRTY, exception.getErrorCode());
    }

    @Test
    void resolveScopeRejectsMultipleFactoryAreaCandidates() {
        MaterialMappingEntity first = mapping(1L, "WH-X", "MAT-X", "弋江", "T26 Floor", "NORMAL");
        MaterialMappingEntity second = mapping(2L, "WH-X", "MAT-X", "三山", "A区", "NORMAL");
        when(mappingRepository.findByWarehouseCodeInAndEnabledTrue(List.of("WH-X")))
                .thenReturn(List.of(first, second));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.resolveMappingScope("WH-X", "MAT-X"));

        assertEquals(ErrorCode.DATA_DIRTY, exception.getErrorCode());
    }

    @Test
    void chooseMappingRejectsMultipleCandidatesForSameUsage() {
        MaterialMappingEntity first = mapping(1L, "WH-1", "MAT-X", "弋江", "T26 Floor", "NORMAL");
        MaterialMappingEntity second = mapping(2L, "WH-2", "MAT-X", "弋江", "T26 Floor", "NORMAL");
        first.setDeliveryAddress("工位-A");
        second.setDeliveryAddress("工位-A");
        when(mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc("MAT-X"))
                .thenReturn(List.of(first, second));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.chooseMapping("MAT-X", "工位-A", false));

        assertEquals(ErrorCode.DATA_DIRTY, exception.getErrorCode());
    }

    @Test
    void previewRejectsLabelOutsideLoginScope() {
        RequestContext.setLoginUser(1L, "warehouse", "仓库", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
        LabelEntity label = new LabelEntity();
        label.setLabelCode("LBL-X"); label.setFactory("三山"); label.setDeliveryArea("A区");
        label.setWarehouseCode("WH-X"); label.setMaterialCode("MAT-X"); label.setMaterialName("物料");
        label.setWarehouseMaterialCode("WM-X"); label.setStandardQty(java.math.BigDecimal.TEN); label.setStationCode("S1");
        when(labelResolverService.normalize("LBL-X")).thenReturn("LBL-X");
        when(labelResolverService.resolve("LBL-X")).thenReturn(label);
        ScanDtos.ScanRequest request = new ScanDtos.ScanRequest(); request.scanCode = "LBL-X";

        assertThrows(BusinessException.class, () -> service.preview(request));

        verifyNoInteractions(boxRepository);
    }

    @Test
    void trustedDeviceCreatesTaskFromUniqueServerSideMappingWithoutAccountScope() {
        RequestContext.setLoginUser(null, "device-01", "现场设备", UserRole.LINE, null, null);
        RequestContext.setTrustedScanner(true);
        MaterialMappingEntity mapping = mapping(1L, "WH-1", "MAT-1", "弋江", "T26 Floor", "NORMAL");
        mapping.setDeliveryAddress("工位-A");
        mapping.setWarehouseLocation("库位-1");
        mapping.setWarehouseMaterialCode("WM-1");
        mapping.setQuantity(java.math.BigDecimal.TEN);
        when(labelResolverService.normalize("MAT-1,工位-A,使用")).thenReturn("MAT-1,工位-A,使用");
        when(labelResolverService.resolveForUpdate("MAT-1"))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "not a label"));
        when(guard.notBlank("MAT-1", "物料号")).thenReturn("MAT-1");
        when(guard.positive(java.math.BigDecimal.TEN, "本次申请数量"))
                .thenReturn(java.math.BigDecimal.TEN);
        when(mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc("MAT-1"))
                .thenReturn(List.of(mapping));
        when(stationMaterialRepository.findByStationCodeAndMaterialCodeAndEnabledTrue("工位-A", "MAT-1"))
                .thenReturn(Optional.empty());
        when(stationMaterialRepository.findFirstByMaterialCodeAndEnabledTrue("MAT-1"))
                .thenReturn(Optional.empty());
        when(taskRepository.findByFactoryAndDeliveryAreaAndWarehouseCodeAndStatusIn(
                eq("弋江"), eq("T26 Floor"), eq("WH-1"), anyList())).thenReturn(List.of());
        when(configRepository.findByConfigKey("task.dedup.window-minutes")).thenReturn(Optional.empty());
        when(lockService.execute(anyString(), any())).thenAnswer(invocation ->
                ((Supplier<?>) invocation.getArgument(1)).get());
        ScanDtos.ScanRequest request = new ScanDtos.ScanRequest();
        request.scanCode = "MAT-1,工位-A,使用";
        request.deviceNo = "ANDROID-01";

        ScanDtos.ScanResult result = service.scanEmpty(request);

        assertTrue(result.taskCreated);
        assertEquals("WH-1", result.warehouseCode);
        assertEquals("工位-A", result.sendStationAddress);
        var taskCaptor = org.mockito.ArgumentCaptor.forClass(
                com.example.materialpull.entity.ReplenishmentTaskEntity.class);
        verify(taskRepository).save(taskCaptor.capture());
        assertEquals("弋江", taskCaptor.getValue().getFactory());
        assertEquals("T26 Floor", taskCaptor.getValue().getDeliveryArea());
    }

    private MaterialMappingEntity mapping(Long id, String warehouseCode, String materialCode,
                                           String factory, String area, String deliveryType) {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setId(id);
        mapping.setWarehouseCode(warehouseCode);
        mapping.setLineMaterialCode(materialCode);
        mapping.setFactory(factory);
        mapping.setDeliveryArea(area);
        mapping.setDeliveryType(deliveryType);
        mapping.setEnabled(true);
        return mapping;
    }
}
