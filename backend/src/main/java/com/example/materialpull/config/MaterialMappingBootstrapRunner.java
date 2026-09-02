package com.example.materialpull.config;

import com.example.materialpull.repository.MaterialMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class MaterialMappingBootstrapRunner implements CommandLineRunner {
    private static final Pattern ROW = Pattern.compile("^\\s*(\\d+)\\s+([A-Za-z0-9]+)\\s+([A-Za-z0-9]+)\\s+([^\\s]+)\\s+([0-9]+(?:\\.[0-9]+)?)\\s*$");
    private final MaterialMappingRepository mappingRepository;

    @Override
    public void run(String... args) throws Exception {
        Path requirements = Path.of("../新要求.md").normalize();
        if (!Files.exists(requirements)) requirements = Path.of("新要求.md");
        if (!Files.exists(requirements)) return;
        List<String> lines = Files.readAllLines(requirements);
        for (String line : lines) {
            Matcher matcher = ROW.matcher(line);
            if (!matcher.matches()) continue;
            String materialCode = matcher.group(2).trim();
            String warehouseCode = matcher.group(3).trim();
            if (warehouseCode.isBlank()) continue;
            if (mappingRepository.findByWarehouseCodeAndEnabledTrue(warehouseCode).isPresent()) continue;
            // 源文件不含 factory/deliveryArea。DataScope 启用后不得再用默认区域或首条数据猜归属；
            // 因此只保留已有数据，缺失项必须通过带明确范围的受控导入补录。
            log.warn("跳过无明确工厂/配送区域的 Mapping 自动初始化: materialCode={}, warehouseCode={}",
                    materialCode, warehouseCode);
        }
    }
}
