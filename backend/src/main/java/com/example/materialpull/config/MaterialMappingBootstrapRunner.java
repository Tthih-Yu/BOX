package com.example.materialpull.config;

import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.example.materialpull.service.BasicDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(5)
@RequiredArgsConstructor
public class MaterialMappingBootstrapRunner implements CommandLineRunner {
    private static final Pattern ROW = Pattern.compile("^\\s*(\\d+)\\s+([A-Za-z0-9]+)\\s+([A-Za-z0-9]+)\\s+([^\\s]+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*$");
    private final MaterialMappingRepository mappingRepository;
    private final BasicDataService basicDataService;

    @Override
    public void run(String... args) throws Exception {
        Path requirements = Path.of("../新要求.md").normalize();
        if (!Files.exists(requirements)) requirements = Path.of("新要求.md");
        if (!Files.exists(requirements)) return;
        List<String> lines = Files.readAllLines(requirements);
        Map<String, Integer> orderByMaterial = new HashMap<>();
        for (String line : lines) {
            Matcher matcher = ROW.matcher(line);
            if (!matcher.matches()) continue;
            String materialCode = matcher.group(2).trim();
            String warehouseCode = matcher.group(3).trim();
            if (warehouseCode.isBlank()) continue;
            if (mappingRepository.findByWarehouseCodeAndEnabledTrue(warehouseCode).isPresent()) continue;
            int materialOrder = orderByMaterial.merge(materialCode, 1, Integer::sum);
            MaterialMappingEntity mapping = new MaterialMappingEntity();
            mapping.setMappingOrder(materialOrder);
            mapping.setLineMaterialCode(materialCode);
            mapping.setWarehouseCode(warehouseCode);
            mapping.setWarehouseMaterialCode(warehouseCode);
            mapping.setBoxSize(matcher.group(4).trim());
            mapping.setQuantity(new BigDecimal(matcher.group(5).trim()));
            mapping.setDeliveryType("NORMAL");
            mapping.setRemark("按新要求文档自动初始化；同物料多个仓库代号为不同工位/AB盒，请回填工位列");
            basicDataService.saveMapping(mapping);
        }
    }
}
