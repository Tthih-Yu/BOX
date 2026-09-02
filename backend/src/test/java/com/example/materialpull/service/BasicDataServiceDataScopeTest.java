package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.BasicDataDtos;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasicDataServiceDataScopeTest {
    @Mock MaterialRepository materialRepository;
    @Mock MaterialMappingRepository mappingRepository;
    @Mock StationMaterialRepository stationMaterialRepository;
    @Mock BoxRepository boxRepository;
    @Mock LabelRepository labelRepository;
    @Mock InventoryRepository inventoryRepository;
    @Mock UserRepository userRepository;
    @Mock SystemConfigRepository configRepository;
    @Mock OperationGuard guard;
    @Mock PasswordService passwordService;
    @Mock AuthTokenService tokenService;
    @Mock UserDeliveryAreaRepository userDeliveryAreaRepository;
    @Mock FactoryRepository factoryRepository;
    @Mock DeliveryAreaRepository deliveryAreaRepository;
    @Mock UserScopeAuditService userScopeAuditService;
    @Spy DataScopeService dataScopeService = new DataScopeService();
    @InjectMocks BasicDataService service;

    @BeforeEach
    void login() {
        RequestContext.setLoginUser(7L, "planner", "计划员", UserRole.PLANNER,
                "弋江", List.of("T26 Floor", "T26 Rear"));
        lenient().when(guard.notBlank(anyString(), anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void listUsesCurrentFactoryAndAreas() {
        service.mappings();

        verify(mappingRepository).findByFactoryIgnoreCaseAndDeliveryAreaInOrderByLineMaterialCodeAscMappingOrderAscIdAsc(
                "弋江", List.of("T26 Floor", "T26 Rear"));
        verify(mappingRepository, never()).findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
    }

    @Test
    void exportUsesTheSameScopedQueryAsList() {
        service.exportMappings();

        verify(mappingRepository).findByFactoryIgnoreCaseAndDeliveryAreaInOrderByLineMaterialCodeAscMappingOrderAscIdAsc(
                "弋江", List.of("T26 Floor", "T26 Rear"));
        verify(mappingRepository, never()).findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
    }

    @Test
    void saveIgnoresForgedFactoryAndUsesTrustedFactory() {
        MaterialMappingEntity mapping = validMapping("三山", "T26 Floor");
        when(mappingRepository.findByWarehouseCodeAndEnabledTrue("WH-01")).thenReturn(Optional.empty());
        when(mappingRepository.save(mapping)).thenReturn(mapping);

        MaterialMappingEntity saved = service.saveMapping(mapping);

        assertEquals("弋江", saved.getFactory());
        verify(mappingRepository).save(mapping);
    }

    @Test
    void saveRejectsAreaOutsideCurrentScope() {
        MaterialMappingEntity mapping = validMapping("弋江", "T26 Engine");

        assertThrows(BusinessException.class, () -> service.saveMapping(mapping));
        verify(mappingRepository, never()).save(any());
    }

    @Test
    void batchDeleteRejectsWholeRequestWhenOneMappingIsOutOfScope() {
        MaterialMappingEntity allowed = scopedMapping(1L, "弋江", "T26 Floor");
        MaterialMappingEntity denied = scopedMapping(2L, "三山", "T26 Floor");
        when(mappingRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(allowed, denied));

        assertThrows(BusinessException.class, () -> service.deleteMappings("IDS", null, List.of(1L, 2L)));
        verify(mappingRepository, never()).deleteAllInBatch(anyList());
    }

    @Test
    void deleteAllRequiresGlobalAdmin() {
        assertThrows(BusinessException.class, () -> service.deleteMappings("ALL", null, null));
        verify(mappingRepository, never()).deleteAllInBatch();
    }

    @Test
    void areaDeleteAlwaysIncludesTrustedFactory() {
        when(mappingRepository.deleteByFactoryAndDeliveryArea("弋江", "T26 Floor")).thenReturn(3);

        service.deleteMappings("AREA", "T26 Floor", null);

        verify(mappingRepository).deleteByFactoryAndDeliveryArea("弋江", "T26 Floor");
    }

    @Test
    void globalAdminCanCreateLowerRoleWithFactoryAndAreas() {
        RequestContext.setLoginUser(1L, "root", "全局管理员", UserRole.ADMIN, null, List.of());
        BasicDataDtos.UserRequest req = userRequest(null, UserRole.PLANNER, "弋江", List.of("T26 Floor"));
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(factoryRepository.findByFactoryCode("弋江")).thenReturn(Optional.of(factory("弋江")));
        when(deliveryAreaRepository.findByFactoryCodeAndAreaCodeIn("弋江", List.of("T26 Floor")))
                .thenReturn(List.of(area("弋江", "T26 Floor")));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            com.example.materialpull.entity.UserEntity saved = invocation.getArgument(0);
            saved.setId(20L);
            return saved;
        });

        BasicDataDtos.UserResponse response = service.saveUser(req);

        assertEquals("弋江", response.factory);
        assertEquals(List.of("T26 Floor"), response.deliveryAreas);
        verify(userDeliveryAreaRepository).deleteByUserId(20L);
        verify(userDeliveryAreaRepository).saveAll(argThat(items -> items.iterator().hasNext()));
    }

    @Test
    void ordinaryAccountMayHaveFactoryWithoutAreas() {
        RequestContext.setLoginUser(1L, "root", "全局管理员", UserRole.ADMIN, null, List.of());
        BasicDataDtos.UserRequest req = userRequest(null, UserRole.VIEWER, "弋江", List.of());
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(factoryRepository.findByFactoryCode("弋江")).thenReturn(Optional.of(factory("弋江")));
        when(userRepository.save(any())).thenAnswer(invocation -> {
            com.example.materialpull.entity.UserEntity saved = invocation.getArgument(0);
            saved.setId(21L);
            return saved;
        });

        BasicDataDtos.UserResponse response = service.saveUser(req);

        assertEquals("弋江", response.factory);
        assertTrue(response.deliveryAreas.isEmpty());
        verify(userDeliveryAreaRepository, never()).saveAll(any());
    }

    @Test
    void inventoryListUsesCurrentFactoryAndAreas() {
        service.inventory();

        verify(inventoryRepository).findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByUpdatedAtDesc(
                "弋江", List.of("T26 Floor", "T26 Rear"));
        verify(inventoryRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    @Test
    void inventorySaveUsesTrustedFactoryAndAuthorizedArea() {
        BasicDataDtos.InventorySaveRequest req = new BasicDataDtos.InventorySaveRequest();
        req.deliveryArea = "T26 Floor";
        req.warehouseCode = "WH";
        req.locationCode = "L01";
        req.warehouseMaterialCode = "WM-01";
        req.materialName = "物料";
        req.stockQty = BigDecimal.TEN;
        req.safetyStock = BigDecimal.ONE;
        when(inventoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var saved = service.saveInventory(req);

        assertEquals("弋江", saved.getFactory());
        assertEquals("T26 Floor", saved.getDeliveryArea());
        verify(inventoryRepository).existsByFactoryIgnoreCaseAndDeliveryAreaAndWarehouseCodeAndLocationCodeAndWarehouseMaterialCodeAndIdNot(
                "弋江", "T26 Floor", "WH", "L01", "WM-01", -1L);
    }

    @Test
    void subAdminCannotDelegateAreaOutsideOwnScope() {
        RequestContext.setLoginUser(2L, "sub", "普通管理员", UserRole.SUB_ADMIN,
                "弋江", List.of("T26 Floor"));
        BasicDataDtos.UserRequest req = userRequest(null, UserRole.VIEWER, "弋江", List.of("T26 Rear"));
        when(userRepository.findByUsername("new-user")).thenReturn(Optional.empty());
        when(factoryRepository.findByFactoryCode("弋江")).thenReturn(Optional.of(factory("弋江")));
        when(deliveryAreaRepository.findByFactoryCodeAndAreaCodeIn("弋江", List.of("T26 Rear")))
                .thenReturn(List.of(area("弋江", "T26 Rear")));

        assertThrows(BusinessException.class, () -> service.saveUser(req));
        verify(userRepository, never()).save(any());
    }

    @Test
    void accountSecurityChangeRevokesSessions() {
        RequestContext.setLoginUser(1L, "root", "全局管理员", UserRole.ADMIN, null, List.of());
        com.example.materialpull.entity.UserEntity existing = new com.example.materialpull.entity.UserEntity();
        existing.setId(30L);
        existing.setUsername("worker");
        existing.setRole(UserRole.VIEWER);
        existing.setFactory("弋江");
        existing.setEnabled(true);
        existing.setVersion(2L);
        BasicDataDtos.UserRequest req = userRequest(30L, UserRole.VIEWER, "三山", List.of());
        req.username = "worker";
        req.password = null;
        req.version = 2L;
        when(userRepository.findById(30L)).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername("worker")).thenReturn(Optional.of(existing));
        when(userDeliveryAreaRepository.findByUserId(30L)).thenReturn(List.of());
        when(factoryRepository.findByFactoryCode("三山")).thenReturn(Optional.of(factory("三山")));
        when(userRepository.save(existing)).thenReturn(existing);

        service.saveUser(req);

        verify(tokenService).revokeUserSessions(30L);
    }

    @Test
    void updateWithoutVersionIsRejectedBeforeAnyWrite() {
        RequestContext.setLoginUser(1L, "root", "全局管理员", UserRole.ADMIN, null, List.of());
        var existing = new com.example.materialpull.entity.UserEntity();
        existing.setId(31L); existing.setUsername("worker"); existing.setRole(UserRole.VIEWER);
        existing.setEnabled(true); existing.setVersion(3L);
        BasicDataDtos.UserRequest req = userRequest(31L, UserRole.VIEWER, "弋江", List.of());
        req.username = "worker";
        req.version = null;
        when(userRepository.findById(31L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.saveUser(req));
        verify(userRepository, never()).save(any());
        verifyNoInteractions(userScopeAuditService);
    }

    @Test
    void globalAdminCannotModifyPeerAdmin() {
        RequestContext.setLoginUser(1L, "root", "全局管理员", UserRole.ADMIN, null, List.of());
        var existing = new com.example.materialpull.entity.UserEntity();
        existing.setId(2L); existing.setUsername("other-admin"); existing.setRole(UserRole.ADMIN);
        existing.setEnabled(true); existing.setVersion(1L);
        BasicDataDtos.UserRequest req = userRequest(2L, UserRole.ADMIN, null, List.of());
        req.username = "other-admin"; req.enabled = false; req.version = 1L;
        when(userRepository.findById(2L)).thenReturn(Optional.of(existing));

        assertThrows(BusinessException.class, () -> service.saveUser(req));
        verify(userRepository, never()).save(any());
    }

    private BasicDataDtos.UserRequest userRequest(Long id, UserRole role, String factory, List<String> areas) {
        BasicDataDtos.UserRequest req = new BasicDataDtos.UserRequest();
        req.id = id;
        req.username = "new-user";
        req.realName = "新用户";
        req.password = id == null ? "Valid-password-123" : null;
        req.role = role;
        req.enabled = true;
        req.factory = factory;
        req.deliveryAreas = areas;
        return req;
    }

    private com.example.materialpull.entity.FactoryEntity factory(String code) {
        com.example.materialpull.entity.FactoryEntity factory = new com.example.materialpull.entity.FactoryEntity();
        factory.setFactoryCode(code);
        factory.setEnabled(true);
        return factory;
    }

    private com.example.materialpull.entity.DeliveryAreaEntity area(String factory, String code) {
        com.example.materialpull.entity.DeliveryAreaEntity area = new com.example.materialpull.entity.DeliveryAreaEntity();
        area.setFactoryCode(factory);
        area.setAreaCode(code);
        area.setEnabled(true);
        return area;
    }

    private MaterialMappingEntity validMapping(String factory, String area) {
        MaterialMappingEntity mapping = scopedMapping(null, factory, area);
        mapping.setLineMaterialCode("MAT-01");
        mapping.setWarehouseCode("WH-01");
        mapping.setWarehouseMaterialCode("WH-01");
        mapping.setQuantity(BigDecimal.ONE);
        mapping.setDeliveryType("NORMAL");
        return mapping;
    }

    private MaterialMappingEntity scopedMapping(Long id, String factory, String area) {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setId(id);
        mapping.setFactory(factory);
        mapping.setDeliveryArea(area);
        return mapping;
    }
}
