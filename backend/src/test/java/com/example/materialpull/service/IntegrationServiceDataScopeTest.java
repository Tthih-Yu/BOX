package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.factory.FactoryDtos;
import com.example.materialpull.entity.InventoryEntity;
import com.example.materialpull.entity.SapImsLinkEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntegrationServiceDataScopeTest {

    @AfterEach void clear() { RequestContext.clear(); }

    @Test
    void scopedHumanCannotReadGlobalIntegrationLinks() {
        SapImsLinkRepository links = mock(SapImsLinkRepository.class);
        IntegrationService service = service(links, mock(InventoryRepository.class));
        login(UserRole.WAREHOUSE, "弋江");

        assertThrows(BusinessException.class, () -> service.links(null));

        verifyNoInteractions(links);
    }

    @Test
    void humanAccountCannotCallImsWriteEndpoint() {
        InventoryRepository inventory = mock(InventoryRepository.class);
        IntegrationService service = service(mock(SapImsLinkRepository.class), inventory);
        login(UserRole.WAREHOUSE, "弋江");

        assertThrows(BusinessException.class, () -> service.receiveImsInventory(payload()));

        verifyNoInteractions(inventory);
    }

    @Test
    void systemImsSyncNeverOverwritesScopedInventoryLookup() {
        SapImsLinkRepository links = mock(SapImsLinkRepository.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        when(links.findBySystemCodeAndExternalKey(any(), any())).thenReturn(Optional.empty());
        when(links.save(any())).thenAnswer(i -> i.getArgument(0));
        when(inventory.findFirstByWarehouseMaterialCodeAndFactoryIsNullAndDeliveryAreaIsNullOrderByUpdatedAtDesc("WM-1"))
                .thenReturn(Optional.empty());
        when(inventory.save(any())).thenAnswer(i -> i.getArgument(0));
        IntegrationService service = service(links, inventory);
        login(UserRole.SYSTEM, null);

        service.receiveImsInventory(payload());

        verify(inventory).findFirstByWarehouseMaterialCodeAndFactoryIsNullAndDeliveryAreaIsNullOrderByUpdatedAtDesc("WM-1");
        verify(inventory).findFirstByWarehouseMaterialCodeAndFactoryIsNullAndDeliveryAreaIsNullOrderByUpdatedAtDesc("WM-1");
    }

    private IntegrationService service(SapImsLinkRepository links, InventoryRepository inventory) {
        return new IntegrationService(links, mock(MaterialBomRepository.class), inventory,
                mock(ProductionPlanRepository.class), mock(AuditService.class), new DataScopeService());
    }

    private FactoryDtos.IntegrationPayload payload() {
        FactoryDtos.IntegrationPayload p = new FactoryDtos.IntegrationPayload();
        p.systemCode = "IMS"; p.externalKey = "E-1"; p.warehouseMaterialCode = "WM-1";
        p.stockQty = BigDecimal.TEN; return p;
    }

    private void login(UserRole role, String factory) {
        RequestContext.setLoginUser(9L, role.name().toLowerCase(), role.name(), role, factory,
                factory == null ? List.of() : List.of("T26 Floor"));
    }
}
