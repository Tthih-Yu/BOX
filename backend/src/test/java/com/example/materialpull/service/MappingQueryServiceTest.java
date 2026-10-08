package com.example.materialpull.service;

import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.MaterialMappingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import(MappingQueryService.class)
class MappingQueryServiceTest {
    @Autowired MaterialMappingRepository repository;
    @Autowired MappingQueryService service;
    @MockBean DataScopeService dataScopeService;

    @BeforeEach
    void prepare() {
        RequestContext.setLoginUser(1L, "warehouse", "仓库员", UserRole.WAREHOUSE, "弋江", List.of("1", "2"));
        when(dataScopeService.isGlobalAdmin()).thenReturn(false);
        when(dataScopeService.currentFactory()).thenReturn("弋江");
        when(dataScopeService.currentDeliveryAreas()).thenReturn(List.of("1", "2"));
        repository.saveAll(List.of(
                mapping("弋江", "1", "WH-01", "G", "正常胶带"),
                mapping("弋江", "2", "WH-02", "H", "备用胶带"),
                mapping("弋江", "3", "WH-03", "G", "区域外"),
                mapping("三山", "1", "WH-04", "G", "工厂外")));
        repository.flush();
    }

    @AfterEach
    void clear() { RequestContext.clear(); }

    @Test
    void columnFiltersApplyToWholePageWithinAccountScope() {
        var page = service.page("", "{\"deliveryArea\":[\"1\",\"2\"],\"boxSize\":[\"G\"]}",
                PageRequest.of(0, 1, Sort.by("warehouseCode")));
        assertEquals(1, page.getTotalElements());
        assertEquals("WH-01", page.getContent().get(0).getWarehouseCode());
        assertEquals(0, service.page("", "{\"factory\":[\"三山\"]}", PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void filterOptionsDoNotRevealOtherAreasOrFactories() {
        assertEquals(List.of("WH-01", "WH-02"), service.options("warehouseCode", "", "", ""));
        assertEquals(List.of("WH-01", "WH-02"), service.options("warehouseMaterialCode", "", "", ""));
        assertEquals(List.of("WH-01"), service.options("warehouseCode", "", "{\"deliveryArea\":[\"1\"]}", ""));
        assertEquals(List.of("弋江"), service.options("factory", "", "", ""));
        assertEquals(List.of("1"), service.options("mappingOrder", "", "", "1"));
        assertEquals(List.of("__MAPPING_FILTER_EMPTY__"), service.options("singleUnitUsage", "", "", ""));
        assertEquals(2, service.page("", "{\"singleUnitUsage\":[\"__MAPPING_FILTER_EMPTY__\"]}",
                PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void optionsAndFilteringIncludeMaterialsBeyondTheCurrentPageAndOldOptionLimit() {
        List<MaterialMappingEntity> additional = IntStream.range(0, 160).mapToObj(index -> {
            MaterialMappingEntity mapping = mapping("弋江", "1", String.format("EXTRA-%03d", index), "G", "批量物料");
            mapping.setLineMaterialCode(String.format("EXTRA-MAT-%03d", index));
            return mapping;
        }).toList();
        repository.saveAllAndFlush(additional);

        var firstPage = service.page("", "", PageRequest.of(0, 50, Sort.by("lineMaterialCode")));
        assertEquals(162, firstPage.getTotalElements());
        assertEquals(50, firstPage.getContent().size());
        assertFalse(firstPage.getContent().stream().anyMatch(row -> "EXTRA-MAT-159".equals(row.getLineMaterialCode())));

        List<String> options = service.options("lineMaterialCode", "", "", "");
        assertEquals(161, options.size());
        assertTrue(options.contains("EXTRA-MAT-159"));
        var lastMaterial = service.page("", "{\"lineMaterialCode\":[\"EXTRA-MAT-159\"]}", PageRequest.of(0, 50));
        assertEquals(1, lastMaterial.getTotalElements());
        assertEquals("EXTRA-159", lastMaterial.getContent().get(0).getWarehouseCode());

        String values = additional.stream().map(row -> "\"" + row.getLineMaterialCode() + "\"").collect(Collectors.joining(","));
        String filters = "{\"lineMaterialCode\":[" + values + "]}";
        var filteredFirstPage = service.page("", filters, PageRequest.of(0, 50));
        assertEquals(160, filteredFirstPage.getTotalElements());
        assertEquals(50, filteredFirstPage.getContent().size());
        var filteredLastPage = service.page("", filters, PageRequest.of(3, 50));
        assertEquals(160, filteredLastPage.getTotalElements());
        assertEquals(10, filteredLastPage.getContent().size());
    }

    private MaterialMappingEntity mapping(String factory, String area, String warehouseCode, String boxSize, String description) {
        MaterialMappingEntity mapping = new MaterialMappingEntity();
        mapping.setFactory(factory);
        mapping.setDeliveryArea(area);
        mapping.setLineMaterialCode("MAT-01");
        mapping.setWarehouseCode(warehouseCode);
        mapping.setWarehouseMaterialCode(warehouseCode);
        mapping.setBoxSize(boxSize);
        mapping.setDescription(description);
        mapping.setQuantity(BigDecimal.TEN);
        return mapping;
    }
}
