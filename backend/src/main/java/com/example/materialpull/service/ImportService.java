package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.IdGenerator;
import com.example.materialpull.common.OperatorResolver;
import com.example.materialpull.dto.LabelDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.ImportStatus;
import com.example.materialpull.repository.ImportBatchRepository;
import com.example.materialpull.repository.ImportErrorRepository;
import com.example.materialpull.repository.MaterialMappingRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.ObjIntConsumer;

@Service
@RequiredArgsConstructor
public class ImportService {
    private final ImportBatchRepository batchRepository;
    private final ImportErrorRepository errorRepository;
    private final BasicDataService basicDataService;
    private final LabelService labelService;
    private final MaterialMappingRepository mappingRepository;
    private final DataFormatter dataFormatter = new DataFormatter();

    /** 批量落库的攒批行数。与 hibernate.jdbc.batch_size 同量级，兼顾往返次数与单事务体积。 */
    private static final int WRITE_BATCH_SIZE = 500;

    /**
     * 各导入类型的规范列顺序（与下载模板表头一致）。用于两件事：
     * 1) 当文件首行是表头时，按表头名称定位列（顺序可乱、可多列）；
     * 2) 当首行不像表头或缺列时，回退按此顺序的列位置读取（兼容旧文件）。
     */
    private static final Map<String, String[]> SCHEMAS = Map.of(
            "materials", new String[]{"materialCode", "warehouseMaterialCode", "materialName", "spec", "unit", "category"},
            "mappings", new String[]{"mappingOrder", "lineMaterialCode", "warehouseCode", "boxSize", "quantity", "deliveryType", "warehouseLocation", "deliveryAddress", "remark", "deliveryArea", "warehouseMaterialCode", "singleUnitUsage"},
            "stationMaterials", new String[]{"lineCode", "stationCode", "stationName", "materialCode", "materialName", "warehouseMaterialCode", "standardBoxQty", "dailyUsage", "triggerQty"},
            "factoryLabels", new String[]{"warehouseCode", "barcodeValue", "primaryScanValue", "materialCode", "materialName", "warehouseMaterialCode", "warehouseAddress", "sendStationAddress", "boxSize", "standardQty", "unit", "delivererEmployeeNo", "lineCode", "stationCode", "stationName", "printDate", "bindBox", "boxSide", "containerType", "remark"},
            "siteLabels", new String[]{"areaCode", "kanbanCardNo", "barcodeValue", "projectCode", "routeName", "deliveryAddress", "materialCode", "materialName", "warehouseMaterialCode", "standardQty", "boxSide", "warehouseLocation", "specText", "unit", "printDate", "lineCode", "stationCode", "stationName", "bindBox"},
            "universalLabels", new String[]{"labelType", "codeCarrierType", "templateCode", "primaryScanValue", "secondaryScanValue", "barcodeValue", "kanbanCardNo", "projectCode", "routeName", "deliveryAddress", "businessCode", "gridCode", "pointOfUseAddress", "routing", "cardNo", "cardTotal", "supermarketBusiness", "supermarketGrid", "supermarketAddress", "materialCode", "materialName", "warehouseMaterialCode", "standardQty", "boxSide", "containerType", "warehouseLocation", "specText", "unit", "printDate", "lineCode", "stationCode", "stationName", "bindBox", "rawPayload", "fieldSnapshotJson", "remark"}
    );

    /** 规范字段名 → 允许的中文/别名表头（全部小写、去空格后匹配）。英文规范名本身始终可用。 */
    private static final Map<String, String[]> HEADER_ALIASES = buildHeaderAliases();

    private static Map<String, String[]> buildHeaderAliases() {
        Map<String, String[]> m = new HashMap<>();
        m.put("materialCode", new String[]{"物料号", "物料编码", "料号", "零件号"});
        m.put("warehouseMaterialCode", new String[]{"仓库物料号", "仓库料号", "仓库编码"});
        m.put("materialName", new String[]{"物料名称", "品名", "名称"});
        m.put("spec", new String[]{"规格", "规格型号"});
        m.put("unit", new String[]{"单位"});
        m.put("category", new String[]{"类别", "分类"});
        m.put("mappingOrder", new String[]{"序号", "排序", "顺序"});
        m.put("lineMaterialCode", new String[]{"物料号", "料号", "产线物料号", "零件号"});
        m.put("warehouseCode", new String[]{"仓库代号", "仓库号", "仓位代号"});
        m.put("boxSize", new String[]{"盒子大小", "盒型", "箱型", "盒子"});
        m.put("quantity", new String[]{"数量", "标准数量", "标准量"});
        m.put("deliveryType", new String[]{"用途", "配送类型", "类型"});
        m.put("warehouseLocation", new String[]{"仓库位置", "仓位", "库位"});
        m.put("deliveryAddress", new String[]{"总装地址", "配送地址", "送货地址", "地址"});
        m.put("remark", new String[]{"备注", "说明"});
        m.put("deliveryArea", new String[]{"配送区域", "区域", "分区"});
        m.put("singleUnitUsage", new String[]{"单根用量", "单件用量", "单台用量", "单根用量数"});
        m.put("lineCode", new String[]{"产线", "产线代号", "线体", "线别"});
        m.put("stationCode", new String[]{"工位代号", "工位号", "工位编码"});
        m.put("stationName", new String[]{"工位名称", "工位"});
        m.put("standardBoxQty", new String[]{"标准盒量", "标准箱量", "盒量"});
        m.put("dailyUsage", new String[]{"日用量", "每日用量"});
        m.put("triggerQty", new String[]{"触发数量", "触发量", "安全量"});
        m.put("barcodeValue", new String[]{"条码值", "条形码", "条码"});
        m.put("primaryScanValue", new String[]{"主扫描值", "主扫码值", "扫码值"});
        m.put("warehouseAddress", new String[]{"仓库地址", "库房地址"});
        m.put("sendStationAddress", new String[]{"发送工位地址", "送料工位地址", "工位地址"});
        m.put("standardQty", new String[]{"数量", "标准数量", "标准量"});
        m.put("delivererEmployeeNo", new String[]{"送料人工号", "送料工号", "配送员工号", "工号"});
        m.put("printDate", new String[]{"打印日期", "日期"});
        m.put("bindBox", new String[]{"绑定盒子", "是否绑定盒子", "绑盒"});
        m.put("boxSide", new String[]{"盒面", "盒子面"});
        m.put("containerType", new String[]{"容器类型", "容器"});
        m.put("areaCode", new String[]{"区域代号", "区域码", "区域"});
        m.put("kanbanCardNo", new String[]{"看板卡号", "看板号", "卡号"});
        m.put("projectCode", new String[]{"项目代号", "项目号", "项目"});
        m.put("routeName", new String[]{"路线名称", "路线"});
        m.put("specText", new String[]{"规格描述", "规格文本", "规格"});
        m.put("labelType", new String[]{"标签类型"});
        m.put("codeCarrierType", new String[]{"载码类型", "码载体类型"});
        m.put("templateCode", new String[]{"模板代号", "模板号", "模板"});
        m.put("secondaryScanValue", new String[]{"次扫描值", "副扫码值"});
        m.put("businessCode", new String[]{"业务代号", "业务号"});
        m.put("gridCode", new String[]{"格位代号", "格位号"});
        m.put("pointOfUseAddress", new String[]{"使用点地址", "使用地址"});
        m.put("routing", new String[]{"路由", "工艺路线"});
        m.put("cardNo", new String[]{"卡片序号", "卡序号"});
        m.put("cardTotal", new String[]{"卡片总数", "卡总数"});
        m.put("supermarketBusiness", new String[]{"超市业务", "超市业务代号"});
        m.put("supermarketGrid", new String[]{"超市格位", "超市货位"});
        m.put("supermarketAddress", new String[]{"超市地址"});
        m.put("rawPayload", new String[]{"原始报文", "原始数据"});
        m.put("fieldSnapshotJson", new String[]{"字段快照", "字段快照json"});
        return m;
    }

    public ImportBatchEntity importExcel(String type, MultipartFile file, String operator) throws Exception {
        return importExcel(type, file, operator, false);
    }

    public ImportBatchEntity importExcel(String type, MultipartFile file, String operator, boolean overwrite) throws Exception {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件不能为空");
        String importType = validateImportType(type);
        if (overwrite) applyOverwrite(importType);
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("");
        validateImportFileName(fileName);

        ImportBatchEntity batch = new ImportBatchEntity();
        batch.setBatchNo(IdGenerator.id("IMP"));
        batch.setImportType(importType);
        batch.setFileName(fileName);
        batch.setOperator(operator == null || operator.isBlank() ? OperatorResolver.systemOperator() : operator.trim());
        batchRepository.save(batch);

        // 流式解析：不把整份文件读进内存，逐行处理并即时落库，行数不设上限。
        // xlsx/xlsm 走 SAX 事件流；csv 逐行读取；xls(旧二进制)本身受 65536 行硬上限约束，用有界 DOM。
        BatchAccumulator acc = new BatchAccumulator(importType, batch);
        try {
            switch (detectFormat(file, fileName)) {
                case CSV -> streamCsv(file, acc);
                case XLS -> readLegacyXls(file, acc);
                default -> streamXlsx(file, acc);
            }
            acc.finish();
            batch.setTotalRows(acc.total);
            batch.setSuccessRows(acc.success);
            batch.setFailedRows(acc.fail);
            batch.setStatus(acc.fail == 0 ? ImportStatus.SUCCESS : (acc.success > 0 ? ImportStatus.PARTIAL_SUCCESS : ImportStatus.FAILED));
            batch.setFinishedAt(LocalDateTime.now());
            return batchRepository.save(batch);
        } catch (BusinessException e) {
            markFailed(batch);
            throw e;
        } catch (Exception e) {
            markFailed(batch);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件解析失败，请确认文件是 xlsx/xlsm/xls/csv 格式：" + e.getMessage());
        }
    }

    /** 覆盖上传：导入新数据前先清空该类型的现存数据。目前仅料号映射支持。 */
    @Transactional
    public void applyOverwrite(String importType) {
        if ("mappings".equals(importType)) {
            mappingRepository.deleteAllInBatch();
            return;
        }
        throw new BusinessException(ErrorCode.PARAM_ERROR, "该导入类型不支持覆盖上传：" + importType);
    }

    private enum FileFormat { XLSX, XLS, CSV }

    /** 按扩展名判断格式；扩展名缺失/不明时读文件头魔数(PK=zip/xlsx，D0CF=OLE2/xls，其余按 csv)。 */
    private FileFormat detectFormat(MultipartFile file, String fileName) throws IOException {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT).trim();
        if (lower.endsWith(".csv")) return FileFormat.CSV;
        if (lower.endsWith(".xls")) return FileFormat.XLS;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm")) return FileFormat.XLSX;
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            in.mark(8);
            byte[] magic = new byte[8];
            int read = in.read(magic);
            in.reset();
            if (read >= 4 && magic[0] == 'P' && magic[1] == 'K') return FileFormat.XLSX;
            if (read >= 4 && (magic[0] & 0xFF) == 0xD0 && (magic[1] & 0xFF) == 0xCF) return FileFormat.XLS;
        }
        return FileFormat.CSV;
    }

    /**
     * 一次导入的处理状态与逐行回调。第一行(下标0)始终当作表头用于建列映射并跳过，
     * 与改造前保持一致；其后每一行做空行跳过、计数、业务写入与错误落库。
     */
    private final class BatchAccumulator implements ObjIntConsumer<List<String>> {
        private final String type;
        private final ImportBatchEntity batch;
        private Map<String, Integer> headerIndex;
        private boolean headerResolved = false;
        /** 待批量写入的映射行（含行号，便于写入失败时定位到具体行）。 */
        private final List<PendingMapping> mappingBuffer = new ArrayList<>();
        private final List<ImportErrorEntity> errorBuffer = new ArrayList<>();
        int total = 0, success = 0, fail = 0;

        BatchAccumulator(String type, ImportBatchEntity batch) {
            this.type = type;
            this.batch = batch;
        }

        /** cells：本行按列下标排列的格式化文本；rowIndex：0 基行号(0 即首行/表头)。 */
        @Override
        public void accept(List<String> cells, int rowIndex) {
            if (!headerResolved) {
                headerIndex = buildHeaderIndex(type, cells);
                headerResolved = true;
                return;
            }
            if (isBlankCells(cells)) return;
            total++;
            try {
                RowView row = new RowView(cells, headerIndex);
                if ("mappings".equals(type)) {
                    // 映射行只做内存校验，攒够一批再统一落库；DB 往返次数从 O(行数) 降到 O(行数/批大小)。
                    mappingBuffer.add(new PendingMapping(buildMapping(row), cells, rowIndex));
                    if (mappingBuffer.size() >= WRITE_BATCH_SIZE) flushMappings();
                } else {
                    importRow(type, row, batch.getOperator());
                    success++;
                }
            } catch (Exception ex) {
                addError(cells, rowIndex, ex);
            }
        }

        /** 解析结束后调用，把尾批数据与错误明细写完。 */
        void finish() {
            flushMappings();
            flushErrors();
        }

        private void flushMappings() {
            if (mappingBuffer.isEmpty()) return;
            List<PendingMapping> pending = List.copyOf(mappingBuffer);
            mappingBuffer.clear();
            try {
                basicDataService.upsertMappingsByWarehouseCode(pending.stream().map(PendingMapping::entity).toList());
                success += pending.size();
            } catch (Exception batchFailure) {
                // 批量写入失败无法定位到具体行，退化为逐行 upsert，把错误准确归到出错的那一行。
                for (PendingMapping item : pending) {
                    try {
                        basicDataService.upsertMappingsByWarehouseCode(List.of(item.entity()));
                        success++;
                    } catch (Exception rowFailure) {
                        addError(item.cells(), item.rowIndex(), rowFailure);
                    }
                }
            }
        }

        private void addError(List<String> cells, int rowIndex, Exception ex) {
            fail++;
            ImportErrorEntity error = new ImportErrorEntity();
            error.setBatchNo(batch.getBatchNo());
            error.setRowNo(rowIndex + 1);
            error.setRawData(cellsToString(cells));
            error.setErrorMessage(ex.getMessage());
            errorBuffer.add(error);
            if (errorBuffer.size() >= WRITE_BATCH_SIZE) flushErrors();
        }

        private void flushErrors() {
            if (errorBuffer.isEmpty()) return;
            errorRepository.saveAll(errorBuffer);
            errorBuffer.clear();
        }
    }

    /** 缓冲中的一行映射：实体本身，加上原始单元格与行号用于错误回溯。 */
    private record PendingMapping(MaterialMappingEntity entity, List<String> cells, int rowIndex) {}

    private void importRow(String type, RowView row, String operator) {
        switch (type) {
            case "materials" -> saveMaterial(row);
            case "mappings" -> basicDataService.saveMapping(buildMapping(row));
            case "stationMaterials" -> saveStationMaterial(row);
            case "factoryLabels" -> saveFactoryLabel(row, operator);
            case "siteLabels" -> saveSiteLabel(row, operator);
            case "universalLabels" -> saveUniversalLabel(row, operator);
            default -> throw new IllegalArgumentException("未知导入类型：" + type);
        }
    }

    private void markFailed(ImportBatchEntity batch) {
        if (batch.getTotalRows() == null) batch.setTotalRows(0);
        if (batch.getSuccessRows() == null) batch.setSuccessRows(0);
        if (batch.getFailedRows() == null) batch.setFailedRows(0);
        batch.setStatus(ImportStatus.FAILED);
        batch.setFinishedAt(LocalDateTime.now());
        batchRepository.save(batch);
    }

    /** CSV 逐行流式读取，不构建任何 workbook；行数不设上限，内存恒定。 */
    private void streamCsv(MultipartFile file, BatchAccumulator acc) throws IOException {
        try (Reader reader = new InputStreamReader(new BufferedInputStream(file.getInputStream()), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setTrim(true).setIgnoreEmptyLines(false).build().parse(reader)) {
            int rowIdx = 0;
            for (CSVRecord record : parser) {
                List<String> cells = new ArrayList<>(record.size());
                for (int i = 0; i < record.size(); i++) {
                    String value = record.get(i);
                    if (rowIdx == 0 && i == 0 && value != null && value.startsWith("\uFEFF")) value = value.substring(1);
                    cells.add(value == null ? "" : value.trim());
                }
                acc.accept(cells, rowIdx);
                rowIdx++;
            }
        }
    }

    /**
     * xlsx/xlsm 事件流(SAX)读取：仅解析第一个工作表，逐行回调，内存与文件大小无关。
     * 用 ReadOnlySharedStringsTable 只读共享字符串表，避免把整表载入内存。
     */
    private void streamXlsx(MultipartFile file, BatchAccumulator acc) throws Exception {
        File temp = File.createTempFile("mp-import-", ".xlsx");
        try (InputStream in = new BufferedInputStream(file.getInputStream())) {
            java.nio.file.Files.copy(in, temp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        try (OPCPackage pkg = OPCPackage.open(temp, PackageAccess.READ)) {
            SharedStrings sst = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!sheets.hasNext()) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件没有可读取的工作表");
            try (InputStream sheetStream = sheets.next()) {
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(new XSSFSheetXMLHandler(styles, sst, new StreamingSheetHandler(acc), false));
                parser.parse(new InputSource(sheetStream));
            }
        } finally {
            if (!temp.delete()) temp.deleteOnExit();
        }
    }

    /**
     * xls(旧 BIFF 二进制)用有界 DOM 读取。该格式单表硬上限 65536 行、256 列，
     * 内存占用有天然上界，不存在无限膨胀 OOM 风险；新的大批量数据请用 xlsx/csv。
     */
    private void readLegacyXls(MultipartFile file, BatchAccumulator acc) throws Exception {
        try (InputStream in = new BufferedInputStream(file.getInputStream());
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getNumberOfSheets() == 0 ? null : wb.getSheetAt(0);
            if (sheet == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "导入文件没有可读取的工作表");
            int first = sheet.getFirstRowNum();
            for (int i = first; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                List<String> cells = new ArrayList<>();
                if (row != null) {
                    int last = Math.max(row.getLastCellNum(), 0);
                    for (int c = 0; c < last; c++) cells.add(rawCell(row, c));
                }
                acc.accept(cells, i);
            }
        }
    }

    /**
     * SAX 事件回调：把稀疏出现的单元格按列还原成完整列表(空列补空串)，整行结束时回调 accumulator。
     * DataFormatter 语义保持与旧实现一致：日期/数字被格式化为文本。
     */
    private final class StreamingSheetHandler implements SheetContentsHandler {
        private final BatchAccumulator acc;
        private final DataFormatter formatter = new DataFormatter();
        private List<String> current = new ArrayList<>();
        private int currentRow = -1;

        StreamingSheetHandler(BatchAccumulator acc) { this.acc = acc; }

        @Override
        public void startRow(int rowNum) {
            current = new ArrayList<>();
            currentRow = rowNum;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int col = cellReference == null ? current.size() : new CellReference(cellReference).getCol();
            while (current.size() < col) current.add("");
            String value = formattedValue == null ? "" : formattedValue.trim();
            if (currentRow == 0 && col == 0 && value.startsWith("\uFEFF")) value = value.substring(1);
            current.add(value);
        }

        @Override
        public void endRow(int rowNum) {
            acc.accept(current, rowNum);
        }
    }

    private String validateImportType(String type) {
        String value = type == null ? "" : type.trim();
        Set<String> allowed = Set.of("materials", "mappings", "stationMaterials", "factoryLabels", "siteLabels", "universalLabels");
        if (!allowed.contains(value)) throw new BusinessException(ErrorCode.PARAM_ERROR, "未知导入类型：" + type);
        return value;
    }

    private void validateImportFileName(String fileName) {
        String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT).trim();
        if (lower.isBlank()) return;
        if (lower.endsWith(".xlsx") || lower.endsWith(".xlsm") || lower.endsWith(".xls") || lower.endsWith(".csv")) return;
        throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 xlsx、xlsm、xls、csv 导入文件");
    }

    private void saveMaterial(RowView r) {
        MaterialEntity m = new MaterialEntity();
        m.setMaterialCode(r.str("materialCode"));
        m.setWarehouseMaterialCode(r.str("warehouseMaterialCode"));
        m.setMaterialName(r.str("materialName"));
        m.setSpec(r.str("spec"));
        m.setUnit(r.str("unit"));
        m.setCategory(r.str("category"));
        basicDataService.saveMaterial(m);
    }

    /** 把一行数据解析成映射实体并完成字段校验，不落库。 */
    private MaterialMappingEntity buildMapping(RowView r) {
        MaterialMappingEntity m = new MaterialMappingEntity();
        m.setMappingOrder(r.intValOrDefault("mappingOrder", 1));
        m.setLineMaterialCode(r.str("lineMaterialCode"));
        m.setWarehouseCode(r.str("warehouseCode"));
        m.setWarehouseMaterialCode(firstNonBlank(r.str("warehouseMaterialCode"), r.str("warehouseCode")));
        m.setBoxSize(r.str("boxSize"));
        m.setQuantity(r.num("quantity", "数量"));
        m.setDeliveryType(r.str("deliveryType").isBlank() ? inferDeliveryType(m.getMappingOrder()) : r.str("deliveryType"));
        m.setWarehouseLocation(r.str("warehouseLocation"));
        m.setDeliveryAddress(r.str("deliveryAddress"));
        m.setRemark(r.str("remark"));
        m.setDeliveryArea(r.str("deliveryArea").isBlank() ? "1" : r.str("deliveryArea"));
        m.setSingleUnitUsage(r.numOrNull("singleUnitUsage", "单根用量"));
        basicDataService.normalizeMapping(m);
        return m;
    }

    private void saveStationMaterial(RowView r) {
        StationMaterialEntity s = new StationMaterialEntity();
        s.setLineCode(r.str("lineCode"));
        s.setStationCode(r.str("stationCode"));
        s.setStationName(r.str("stationName"));
        s.setMaterialCode(r.str("materialCode"));
        s.setMaterialName(r.str("materialName"));
        s.setWarehouseMaterialCode(r.str("warehouseMaterialCode"));
        s.setStandardBoxQty(r.num("standardBoxQty", "标准盒量"));
        s.setDailyUsage(r.num("dailyUsage", "日用量"));
        s.setTriggerQty(r.num("triggerQty", "触发数量"));
        basicDataService.saveStationMaterial(s);
    }

    private void saveFactoryLabel(RowView r, String operator) {
        LabelDtos.FactoryPullLabelRequest x = new LabelDtos.FactoryPullLabelRequest();
        x.warehouseCode = r.str("warehouseCode"); x.barcodeValue = r.str("barcodeValue"); x.primaryScanValue = r.str("primaryScanValue");
        x.materialCode = r.str("materialCode"); x.materialName = r.str("materialName"); x.warehouseMaterialCode = r.str("warehouseMaterialCode");
        x.warehouseAddress = r.str("warehouseAddress"); x.sendStationAddress = r.str("sendStationAddress"); x.boxSize = r.str("boxSize");
        x.standardQty = r.num("standardQty", "数量"); x.unit = r.str("unit"); x.delivererEmployeeNo = r.str("delivererEmployeeNo");
        x.lineCode = r.str("lineCode"); x.stationCode = r.str("stationCode"); x.stationName = r.str("stationName");
        x.printDate = r.date("printDate");
        x.bindBox = "true".equalsIgnoreCase(r.str("bindBox")); x.boxSide = r.str("boxSide"); x.containerType = r.str("containerType");
        x.remark = r.str("remark"); x.operator = operator;
        labelService.createFactoryPullLabel(x);
    }

    private void saveSiteLabel(RowView r, String operator) {
        LabelDtos.SiteLabelRequest x = new LabelDtos.SiteLabelRequest();
        x.areaCode = r.str("areaCode"); x.kanbanCardNo = r.str("kanbanCardNo"); x.barcodeValue = r.str("barcodeValue");
        x.projectCode = r.str("projectCode"); x.routeName = r.str("routeName"); x.deliveryAddress = r.str("deliveryAddress");
        x.materialCode = r.str("materialCode"); x.materialName = r.str("materialName"); x.warehouseMaterialCode = r.str("warehouseMaterialCode");
        x.standardQty = r.num("standardQty", "数量"); x.boxSide = r.str("boxSide"); x.warehouseLocation = r.str("warehouseLocation");
        x.specText = r.str("specText"); x.unit = r.str("unit");
        x.printDate = r.date("printDate");
        x.lineCode = r.str("lineCode"); x.stationCode = r.str("stationCode"); x.stationName = r.str("stationName");
        x.bindBox = !"false".equalsIgnoreCase(r.str("bindBox"));
        x.operator = operator;
        labelService.createSiteLabel(x);
    }

    private void saveUniversalLabel(RowView r, String operator) {
        LabelDtos.UniversalLabelRequest x = new LabelDtos.UniversalLabelRequest();
        x.labelType = r.str("labelType"); x.codeCarrierType = r.str("codeCarrierType"); x.templateCode = r.str("templateCode");
        x.primaryScanValue = r.str("primaryScanValue"); x.secondaryScanValue = r.str("secondaryScanValue"); x.barcodeValue = r.str("barcodeValue"); x.kanbanCardNo = r.str("kanbanCardNo");
        x.projectCode = r.str("projectCode"); x.routeName = r.str("routeName"); x.deliveryAddress = r.str("deliveryAddress");
        x.businessCode = r.str("businessCode"); x.gridCode = r.str("gridCode"); x.pointOfUseAddress = r.str("pointOfUseAddress");
        x.routing = r.str("routing"); x.cardNo = r.intVal("cardNo", "卡片序号"); x.cardTotal = r.intVal("cardTotal", "卡片总数");
        x.supermarketBusiness = r.str("supermarketBusiness"); x.supermarketGrid = r.str("supermarketGrid"); x.supermarketAddress = r.str("supermarketAddress");
        x.materialCode = r.str("materialCode"); x.materialName = r.str("materialName"); x.warehouseMaterialCode = r.str("warehouseMaterialCode");
        x.standardQty = r.num("standardQty", "数量"); x.boxSide = r.str("boxSide"); x.containerType = r.str("containerType"); x.warehouseLocation = r.str("warehouseLocation");
        x.specText = r.str("specText"); x.unit = r.str("unit");
        x.printDate = r.date("printDate");
        x.lineCode = r.str("lineCode"); x.stationCode = r.str("stationCode"); x.stationName = r.str("stationName");
        x.bindBox = "true".equalsIgnoreCase(r.str("bindBox"));
        x.rawPayload = r.str("rawPayload"); x.fieldSnapshotJson = r.str("fieldSnapshotJson"); x.remark = r.str("remark");
        x.operator = operator;
        labelService.createUniversalLabel(x);
    }

    /**
     * 根据首行构建“规范字段名 → 列下标”的映射。
     * 若首行像表头（大多数列能与已知字段名/别名对上），则按表头名定位列；
     * 否则视为无表头/旧格式，回退成按 schema 的顺序位置（第0列=第1个字段…）。
     */
    private Map<String, Integer> buildHeaderIndex(String type, List<String> headerCells) {
        String[] schema = SCHEMAS.getOrDefault(type, new String[0]);
        Map<String, Integer> byField = new HashMap<>();
        if (headerCells != null && !headerCells.isEmpty()) {
            int matched = 0;
            for (int i = 0; i < headerCells.size(); i++) {
                String header = normalizeHeader(headerCells.get(i));
                if (header.isEmpty()) continue;
                String field = matchField(schema, header);
                if (field != null && !byField.containsKey(field)) {
                    byField.put(field, i);
                    matched++;
                }
            }
            if (matched >= 2) return byField;
        }
        Map<String, Integer> positional = new HashMap<>();
        for (int i = 0; i < schema.length; i++) positional.put(schema[i], i);
        return positional;
    }

    private String matchField(String[] schema, String normalizedHeader) {
        for (String field : schema) {
            if (normalizeHeader(field).equals(normalizedHeader)) return field;
            String[] aliases = HEADER_ALIASES.get(field);
            if (aliases != null) {
                for (String alias : aliases) {
                    if (normalizeHeader(alias).equals(normalizedHeader)) return field;
                }
            }
        }
        return null;
    }

    private String normalizeHeader(String v) {
        if (v == null) return "";
        return v.replace("\uFEFF", "").replaceAll("[\\s_（）()\\-]+", "").toLowerCase(Locale.ROOT).trim();
    }

    private String rawCell(Row r, int i) {
        Cell c = r.getCell(i);
        if (c == null) return "";
        return dataFormatter.formatCellValue(c).trim();
    }

    /** 常见日期文本格式：POI DataFormatter 可能输出 yyyy/M/d、yyyy-MM-dd 等，逐一尝试。 */
    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyyMMdd")
    };

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        try { return LocalDate.parse(v); } catch (Exception ignored) {}
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try { return LocalDate.parse(v, fmt); } catch (Exception ignored) {}
        }
        throw new IllegalArgumentException("日期格式应为 yyyy-MM-dd，当前值=" + s);
    }

    private BigDecimal parseNum(String s, String name) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim().replace(",", "")); }
        catch (Exception e) { throw new IllegalArgumentException(name + "必须是数字，当前值=" + s); }
    }

    /** 与 parseNum 相同，但空值返回 null（保留“未维护”语义，不落成 0）。 */
    private BigDecimal parseNumOrNull(String s, String name) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s.trim().replace(",", "")); }
        catch (Exception e) { throw new IllegalArgumentException(name + "必须是数字，当前值=" + s); }
    }

    private Integer parseInt(String s, String name) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim().replace(",", "")); }
        catch (Exception e) { throw new IllegalArgumentException(name + "必须是整数，当前值=" + s); }
    }

    private String inferDeliveryType(Integer order) {
        return order != null && order > 1 ? "URGENT" : "NORMAL";
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return "";
    }

    private boolean isBlankCells(List<String> cells) {
        if (cells == null || cells.isEmpty()) return true;
        for (String c : cells) if (c != null && !c.isBlank()) return false;
        return true;
    }

    private String cellsToString(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        int last = Math.max(40, cells == null ? 0 : cells.size());
        for (int i = 0; i < last; i++) {
            String v = cells != null && i < cells.size() ? cells.get(i) : "";
            sb.append(v == null ? "" : v).append("|");
        }
        return sb.toString();
    }

    /** 一行数据的字段视图：按“字段名→列下标”映射读取，屏蔽表头/位置差异。 */
    private final class RowView {
        private final List<String> cells;
        private final Map<String, Integer> index;

        RowView(List<String> cells, Map<String, Integer> index) {
            this.cells = cells;
            this.index = index;
        }

        String str(String field) {
            Integer i = index.get(field);
            if (i == null || i < 0 || i >= cells.size()) return "";
            String v = cells.get(i);
            return v == null ? "" : v;
        }

        BigDecimal num(String field, String name) {
            return parseNum(str(field), name);
        }

        BigDecimal numOrNull(String field, String name) {
            return parseNumOrNull(str(field), name);
        }

        Integer intVal(String field, String name) {
            return parseInt(str(field), name);
        }

        Integer intValOrDefault(String field, Integer fallback) {
            Integer v = parseInt(str(field), field);
            return v == null ? fallback : v;
        }

        LocalDate date(String field) {
            return parseDate(str(field));
        }
    }
}
