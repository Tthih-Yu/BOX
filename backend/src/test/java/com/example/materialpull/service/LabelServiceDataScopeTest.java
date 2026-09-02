package com.example.materialpull.service;

import com.example.materialpull.common.AppProperties;
import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.LabelDtos;
import com.example.materialpull.entity.LabelEntity;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.BoxRepository;
import com.example.materialpull.repository.LabelRepository;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.repository.StationMaterialRepository;
import com.example.materialpull.resilience.OperationGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LabelServiceDataScopeTest {
    @Mock LabelRepository labelRepository;
    @Mock BoxRepository boxRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock AuditService auditService;
    @Mock LabelResolverService resolverService;
    @Mock OperationGuard guard;
    @Mock AppProperties properties;
    @Mock ExternalHttpClient externalHttpClient;
    @Mock MaterialMappingRepository mappingRepository;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks LabelService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(7L, "warehouse", "仓库", UserRole.WAREHOUSE, "弋江", List.of("T26 Floor"));
        lenient().when(resolverService.normalize(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(guard.notBlank(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(guard.positive(any(BigDecimal.class), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void labelAndBoundBoxSnapshotUniqueMappingScope() {
        MaterialMappingEntity mapping = mapping(1L, "WH-01", "弋江", "T26 Floor");
        when(mappingRepository.findAllByWarehouseCodeAndEnabledTrueOrderByIdAsc("WH-01")).thenReturn(List.of(mapping));
        when(labelRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LabelDtos.UniversalLabelRequest req = request();
        req.bindBox = true;
        req.boxSide = "A";

        LabelEntity saved = service.createUniversalLabel(req);

        assertEquals("弋江", saved.getFactory());
        assertEquals("T26 Floor", saved.getDeliveryArea());
        verify(boxRepository).save(argThat(box -> "弋江".equals(box.getFactory()) && "T26 Floor".equals(box.getDeliveryArea())));
    }

    @Test
    void multipleMappingCandidatesRejectBeforePersistingLabelOrBox() {
        when(mappingRepository.findAllByWarehouseCodeAndEnabledTrueOrderByIdAsc("WH-01")).thenReturn(List.of(
                mapping(1L, "WH-01", "弋江", "T26 Floor"), mapping(2L, "WH-01", "弋江", "T26 Rear")));

        assertThrows(BusinessException.class, () -> service.createUniversalLabel(request()));

        verify(labelRepository, never()).save(any());
        verify(boxRepository, never()).save(any());
    }

    @Test
    void previewRejectsLabelFromAnotherFactory() {
        LabelEntity label = new LabelEntity();
        label.setFactory("三山"); label.setDeliveryArea("T26 Floor");
        when(resolverService.resolve("LBL-X")).thenReturn(label);

        assertThrows(BusinessException.class, () -> service.preview("LBL-X"));
    }

    @Test
    void voidRejectsOtherAreaBeforeMutation() {
        LabelEntity label = new LabelEntity();
        label.setLabelCode("LBL-X"); label.setFactory("弋江"); label.setDeliveryArea("T26 Rear");
        when(resolverService.resolveForUpdate("LBL-X")).thenReturn(label);

        assertThrows(BusinessException.class, () -> service.voidLabel("LBL-X", "warehouse"));

        verifyNoInteractions(boxRepository);
        verify(labelRepository, never()).save(any());
    }

    private LabelDtos.UniversalLabelRequest request() {
        LabelDtos.UniversalLabelRequest req = new LabelDtos.UniversalLabelRequest();
        req.labelType = "FACTORY_PULL_BARCODE";
        req.warehouseCode = "WH-01";
        req.materialCode = "MAT-01";
        req.materialName = "物料";
        req.standardQty = BigDecimal.TEN;
        req.stationCode = "ST-01";
        return req;
    }

    private MaterialMappingEntity mapping(Long id, String warehouseCode, String factory, String area) {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setId(id); mapping.setWarehouseCode(warehouseCode); mapping.setFactory(factory); mapping.setDeliveryArea(area); mapping.setEnabled(true);
        return mapping;
    }
}
