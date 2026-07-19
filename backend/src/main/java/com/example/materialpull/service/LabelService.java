package com.example.materialpull.service;

import com.example.materialpull.common.*;
import com.example.materialpull.dto.LabelDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.BoxStatus;
import com.example.materialpull.enums.LabelStatus;
import com.example.materialpull.repository.*;
import com.example.materialpull.resilience.OperationGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LabelService {
    private final LabelRepository labelRepository;
    private final BoxRepository boxRepository;
    private final StationMaterialRepository stationMaterialRepository;
    private final AuditService auditService;
    private final LabelResolverService resolverService;
    private final OperationGuard guard;
    private final AppProperties properties;
    private final ExternalHttpClient externalHttpClient;

    public List<LabelEntity> list(){ return labelRepository.findAll(PageRequest.of(0, 1000, Sort.by(Sort.Direction.DESC, "id"))).getContent(); }

    @Transactional
    public List<LabelEntity> generate(LabelDtos.GenerateRequest req) {
        if (req == null) req = new LabelDtos.GenerateRequest();
        StationMaterialEntity s = stationMaterialRepository.findByStationCodeAndMaterialCodeAndEnabledTrue(req.stationCode, req.materialCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工位用料不存在"));
        int count = req.count == null ? 2 : Math.max(1, Math.min(200, req.count));
        List<LabelEntity> out = new ArrayList<>();
        for (int i=0;i<count;i++) {
            String code = "LBL-" + req.stationCode + "-" + req.materialCode + "-" + IdGenerator.id("N").replace("N-", "") + "-" + i;
            LabelEntity l = new LabelEntity();
            l.setLabelCode(code); l.setLabelType("INTERNAL_BOX_LABEL"); l.setCodeCarrierType("QR_CODE"); l.setPrimaryScanValue(code);
            l.setLineCode(s.getLineCode()); l.setStationCode(s.getStationCode()); l.setStationName(s.getStationName());
            l.setProjectCode(s.getProjectCode()); l.setRouteName(s.getRouteName()); l.setDeliveryAddress(s.getDeliveryAddress());
            l.setAreaCode(s.getAreaCode()); l.setWarehouseLocation(s.getWarehouseLocation()); l.setSpecText(s.getSpecText()); l.setUnit(s.getUnit());
            l.setMaterialCode(s.getMaterialCode()); l.setMaterialName(s.getMaterialName()); l.setWarehouseMaterialCode(s.getWarehouseMaterialCode());
            l.setStandardQty(s.getStandardBoxQty()); l.setLabelUsageType("USE"); l.setDeliveryMode("NORMAL"); l.setTemplateCode(req.templateCode == null ? "KANBAN_SITE" : req.templateCode); l.setStatus(LabelStatus.UNUSED);
            out.add(labelRepository.save(l));
        }
        return out;
    }

    /**
     * 第一类现场看板卡格式建档。注意：V0.5 已取消“条码必须等于看板卡号”的硬编码。
     * 当前现场可以仍然传 139315，后续换二维码时 primaryScanValue/barcodeValue/kanbanCardNo 可以分开维护。
     */
    @Transactional
    public LabelEntity createSiteLabel(LabelDtos.SiteLabelRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "标签内容不能为空");
        LabelDtos.UniversalLabelRequest u = new LabelDtos.UniversalLabelRequest();
        u.labelType = firstNotBlank(req.labelType, "SITE_KANBAN_BARCODE");
        u.codeCarrierType = firstNotBlank(req.codeCarrierType, "BARCODE_1D");
        u.templateCode = firstNotBlank(req.templateCode, "KANBAN_SITE");
        u.areaCode = req.areaCode;
        u.kanbanCardNo = req.kanbanCardNo;
        u.barcodeValue = req.barcodeValue;
        u.primaryScanValue = req.primaryScanValue;
        u.secondaryScanValue = req.secondaryScanValue;
        u.rawPayload = req.rawPayload;
        u.projectCode = req.projectCode;
        u.routeName = req.routeName;
        u.deliveryAddress = req.deliveryAddress;
        u.materialCode = req.materialCode;
        u.materialName = req.materialName;
        u.materialImageUrl = req.materialImageUrl;
        u.warehouseMaterialCode = req.warehouseMaterialCode;
        u.standardQty = req.standardQty;
        u.boxSide = req.boxSide;
        u.labelUsageType = req.labelUsageType;
        u.deliveryMode = req.deliveryMode;
        u.containerType = firstNotBlank(req.containerType, req.boxSide);
        u.warehouseLocation = req.warehouseLocation;
        u.specText = req.specText;
        u.unit = req.unit;
        u.printDate = req.printDate;
        u.lineCode = req.lineCode;
        u.stationCode = req.stationCode;
        u.stationName = req.stationName;
        u.operator = OperatorResolver.currentOperator();
        u.bindBox = req.bindBox;
        return createUniversalLabel(u);
    }

    /** 负责人确认后的真实工厂拉动标签建档：条形码=仓库代码。 */
    @Transactional
    public LabelEntity createFactoryPullLabel(LabelDtos.FactoryPullLabelRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "真实工厂标签内容不能为空");
        LabelDtos.UniversalLabelRequest u = new LabelDtos.UniversalLabelRequest();
        u.labelType = "FACTORY_PULL_BARCODE";
        u.codeCarrierType = firstNotBlank(req.codeCarrierType, "BARCODE_1D");
        u.templateCode = firstNotBlank(req.templateCode, "FACTORY_PULL");
        u.warehouseCode = req.warehouseCode;
        u.barcodeValue = firstNotBlank(req.barcodeValue, req.warehouseCode);
        u.primaryScanValue = firstNotBlank(req.primaryScanValue, req.barcodeValue, req.warehouseCode);
        u.materialCode = req.materialCode;
        u.materialName = req.materialName;
        u.materialImageUrl = req.materialImageUrl;
        u.warehouseMaterialCode = req.warehouseMaterialCode;
        u.warehouseAddress = req.warehouseAddress;
        u.warehouseLocation = req.warehouseAddress;
        u.sendStationAddress = req.sendStationAddress;
        u.deliveryAddress = req.sendStationAddress;
        u.boxSize = req.boxSize;
        u.containerType = firstNotBlank(req.containerType, req.boxSize);
        u.boxSide = req.boxSide;
        u.labelUsageType = req.labelUsageType;
        u.deliveryMode = req.deliveryMode;
        u.standardQty = req.standardQty;
        u.unit = req.unit;
        u.delivererEmployeeNo = req.delivererEmployeeNo;
        u.lineCode = req.lineCode;
        u.stationCode = req.stationCode;
        u.stationName = req.stationName;
        u.operator = OperatorResolver.currentOperator();
        u.bindBox = req.bindBox;
        u.printDate = req.printDate;
        u.remark = req.remark;
        u.fieldSnapshotJson = "{\"source\":\"confirmed_by_factory_owner\",\"template\":\"FACTORY_PULL\",\"barcodeMeans\":\"warehouseCode\"}";
        return createUniversalLabel(u);
    }

    @Transactional
    public LabelEntity createUniversalLabel(LabelDtos.UniversalLabelRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "标签内容不能为空");
        req.labelType = normalizeType(firstNotBlank(req.labelType, "GENERIC_LABEL"));
        req.codeCarrierType = normalizeType(firstNotBlank(req.codeCarrierType, "MANUAL"));
        req.templateCode = firstNotBlank(req.templateCode, templateByType(req.labelType));
        req.materialName = guard.notBlank(req.materialName, "物料名称");
        String warehouseCode = blankToNull(req.warehouseCode == null ? null : resolverService.normalize(req.warehouseCode));
        if ("FACTORY_PULL_BARCODE".equals(req.labelType) && (warehouseCode == null || warehouseCode.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "真实工厂标签必须填写仓库代码；条形码扫码值应等于仓库代码");
        }
        String materialCode = firstNotBlank(req.materialCode, req.warehouseMaterialCode, warehouseCode);
        if (materialCode == null || materialCode.isBlank()) materialCode = IdGenerator.id("MAT");
        req.materialCode = materialCode;
        req.warehouseMaterialCode = firstNotBlank(req.warehouseMaterialCode, warehouseCode, materialCode);
        BigDecimal qty = guard.positive(req.standardQty == null ? BigDecimal.ZERO : req.standardQty, "数量");

        String primary = firstNotBlank(req.primaryScanValue, req.barcodeValue, warehouseCode, req.kanbanCardNo, req.labelCode,
                composePointOfUseCode(req));
        if (primary != null && !primary.isBlank()) primary = resolverService.normalize(primary);
        String barcodeSource = firstNotBlank(req.barcodeValue, "FACTORY_PULL_BARCODE".equals(req.labelType) ? warehouseCode : null);
        String barcode = blankToNull(barcodeSource == null ? null : resolverService.normalize(barcodeSource));
        String kanban = blankToNull(req.kanbanCardNo == null ? null : resolverService.normalize(req.kanbanCardNo));
        String labelCode = firstNotBlank(req.labelCode, buildLabelCode(req.labelType, primary, materialCode, req.cardNo));
        ensureUnique(labelCode, primary, barcode, warehouseCode, kanban);

        String container = firstNotBlank(req.containerType, req.boxSide);
        String boxSide = blankToNull(req.boxSide == null ? null : req.boxSide.trim().toUpperCase(Locale.ROOT));
        boolean bindBox = Boolean.TRUE.equals(req.bindBox);
        if (bindBox) {
            if (!"A".equals(boxSide) && !"B".equals(boxSide)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "只有 A/B 双盒标签才能绑定双盒状态机；POINT OF USE 或 D 类容器请先作为通用标签建档");
            }
        }

        String stationCode = firstNotBlank(req.stationCode, req.sendStationAddress, req.deliveryAddress, req.pointOfUseAddress, "UNKNOWN");
        String pairCode = stationCode + "-" + req.materialCode;
        String boxCode = pairCode + "-" + firstNotBlank(boxSide, container, "BOX");
        if (bindBox && boxRepository.findByBoxCode(boxCode).isPresent()) throw new BusinessException(ErrorCode.DATA_DIRTY, "盒号已存在：" + boxCode);

        LabelEntity l = new LabelEntity();
        l.setLabelCode(labelCode); l.setLabelType(req.labelType); l.setCodeCarrierType(req.codeCarrierType);
        l.setPrimaryScanValue(primary); l.setSecondaryScanValue(req.secondaryScanValue); l.setBarcodeValue(barcode); l.setWarehouseCode(warehouseCode);
        l.setWarehouseAddress(req.warehouseAddress); l.setSendStationAddress(req.sendStationAddress); l.setBoxSize(req.boxSize); l.setDelivererEmployeeNo(req.delivererEmployeeNo);
        l.setKanbanCardNo(kanban); l.setRawPayload(req.rawPayload);
        l.setAreaCode(req.areaCode); l.setBoxCode(bindBox ? boxCode : null); l.setPairCode(pairCode); l.setBoxSide(boxSide);
        l.setLabelUsageType(firstNotBlank(req.labelUsageType, "B".equalsIgnoreCase(boxSide) ? "SPARE" : "USE"));
        l.setDeliveryMode(firstNotBlank(req.deliveryMode, "SPARE".equalsIgnoreCase(l.getLabelUsageType()) ? "URGENT" : "NORMAL"));
        l.setContainerType(container);
        l.setLineCode(firstNotBlank(req.lineCode, req.projectCode, req.businessCode, "LINE")); l.setStationCode(stationCode); l.setStationName(firstNotBlank(req.stationName, req.deliveryAddress, req.pointOfUseAddress, stationCode));
        l.setProjectCode(req.projectCode); l.setRouteName(req.routeName); l.setDeliveryAddress(firstNotBlank(req.deliveryAddress, req.sendStationAddress));
        l.setBusinessCode(req.businessCode); l.setGridCode(req.gridCode); l.setPointOfUseAddress(req.pointOfUseAddress);
        l.setRouting(firstNotBlank(req.routing, req.routeName)); l.setCardNo(req.cardNo); l.setCardTotal(req.cardTotal);
        l.setSupermarketBusiness(req.supermarketBusiness); l.setSupermarketGrid(req.supermarketGrid); l.setSupermarketAddress(firstNotBlank(req.supermarketAddress, req.warehouseLocation, req.warehouseAddress));
        l.setMaterialCode(req.materialCode); l.setMaterialName(req.materialName); l.setMaterialImageUrl(req.materialImageUrl); l.setWarehouseMaterialCode(firstNotBlank(req.warehouseMaterialCode, warehouseCode, req.materialCode));
        l.setStandardQty(qty); l.setUnit(req.unit); l.setSpecText(firstNotBlank(req.specText, req.boxSize)); l.setWarehouseLocation(firstNotBlank(req.warehouseLocation, req.warehouseAddress));
        l.setTemplateCode(req.templateCode); l.setTemplateVersion("V0.6"); l.setPrintDate(req.printDate == null ? LocalDate.now() : req.printDate); l.setStatus(bindBox ? LabelStatus.BOUND : LabelStatus.UNUSED);
        l.setFieldSnapshotJson(req.fieldSnapshotJson); l.setRemark(req.remark);
        LabelEntity saved = labelRepository.save(l);

        if (bindBox) {
            BoxEntity b = new BoxEntity();
            b.setBoxCode(boxCode); b.setPairCode(pairCode); b.setBoxSide(boxSide); b.setLabelCode(labelCode); b.setBarcodeValue(firstNotBlank(barcode, primary));
            b.setWarehouseCode(warehouseCode); b.setWarehouseAddress(req.warehouseAddress); b.setSendStationAddress(req.sendStationAddress); b.setBoxSize(req.boxSize); b.setDelivererEmployeeNo(req.delivererEmployeeNo); b.setKanbanCardNo(kanban);
            b.setAreaCode(req.areaCode); b.setLineCode(l.getLineCode()); b.setStationCode(stationCode); b.setStationName(l.getStationName());
            b.setProjectCode(firstNotBlank(req.projectCode, req.businessCode)); b.setRouteName(firstNotBlank(req.routeName, req.routing)); b.setDeliveryAddress(firstNotBlank(req.deliveryAddress, req.sendStationAddress, req.pointOfUseAddress)); b.setWarehouseLocation(firstNotBlank(req.warehouseLocation, req.warehouseAddress, req.supermarketAddress));
            b.setMaterialCode(req.materialCode); b.setMaterialName(req.materialName); b.setWarehouseMaterialCode(l.getWarehouseMaterialCode());
            b.setStandardQty(qty); b.setCurrentQty("A".equals(boxSide) ? qty.divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP) : qty);
            b.setStatus("A".equals(boxSide) ? BoxStatus.IN_USE : BoxStatus.FULL_STANDBY);
            b.setHealthStatus("OK");
            boxRepository.save(b);
        }
        auditService.print(saved.getLabelCode(), "CREATE_UNIVERSAL_LABEL", OperatorResolver.currentOperator(), null, true, "通用标签建档，labelType=" + req.labelType + "，scan=" + primary);
        return saved;
    }

    @Transactional(readOnly = true)
    public LabelDtos.PreviewResponse preview(String scanCode) {
        LabelEntity l = resolverService.resolve(scanCode);
        return toPreview(l);
    }

    @Transactional
    public LabelEntity print(String scanCode, LabelDtos.PrintRequest req) {
        if (req == null) req = new LabelDtos.PrintRequest();
        LabelEntity l = resolverService.resolveForUpdate(scanCode);
        if (l.getStatus() == LabelStatus.VOIDED) throw new BusinessException(ErrorCode.STATE_CONFLICT, "作废标签不能打印");
        String printerName = firstNotBlank(req.printerName, properties.getDefaultPrinterName());
        if (printerName == null || printerName.isBlank()) throw new BusinessException(ErrorCode.PARAM_ERROR, "打印机名称不能为空，生产环境禁止使用未指定打印机");
        String payload = labelPrintPayload(l, printerName, Boolean.TRUE.equals(req.reprint));
        String response = externalHttpClient.postJson(
                properties.getPrintSubmitUrl(),
                payload,
                properties.getPrintAuthHeader(),
                properties.getPrintApiKey(),
                properties.getExternalCallTimeoutMs(),
                "标签打印服务"
        );
        l.setPrintCount((l.getPrintCount() == null ? 0 : l.getPrintCount()) + 1);
        l.setLastPrintedAt(LocalDateTime.now());
        l.setLastPrintUser(OperatorResolver.currentOperator());
        l.setLastError(response);
        if (Boolean.TRUE.equals(req.reprint)) l.setStatus(LabelStatus.REPRINTED);
        labelRepository.save(l);
        auditService.print(l.getLabelCode(), Boolean.TRUE.equals(req.reprint) ? "REPRINT" : "PRINT", OperatorResolver.currentOperator(), printerName, true, "标签打印任务已提交真实打印服务；主扫码值=" + l.getPrimaryScanValue());
        return l;
    }

    @Transactional
    public LabelEntity voidLabel(String scanCode, String operator) {
        LabelEntity l = resolverService.resolveForUpdate(scanCode);
        boxRepository.findByLabelCode(l.getLabelCode()).ifPresent(b -> { throw new BusinessException(ErrorCode.STATE_CONFLICT, "标签已绑定盒子，不能直接作废；请先停用盒子或走异常处理流程"); });
        l.setStatus(LabelStatus.VOIDED);
        auditService.print(l.getLabelCode(), "VOID", OperatorResolver.currentOperator(), null, true, "标签作废；主扫码值=" + l.getPrimaryScanValue());
        return labelRepository.save(l);
    }

    private LabelDtos.PreviewResponse toPreview(LabelEntity l) {
        LabelDtos.PreviewResponse r = new LabelDtos.PreviewResponse();
        r.labelCode = l.getLabelCode(); r.labelType = l.getLabelType(); r.codeCarrierType = l.getCodeCarrierType(); r.templateCode = l.getTemplateCode();
        r.primaryScanValue = l.getPrimaryScanValue(); r.secondaryScanValue = l.getSecondaryScanValue(); r.barcodeValue = l.getBarcodeValue();
        r.warehouseCode = l.getWarehouseCode(); r.warehouseAddress = l.getWarehouseAddress(); r.sendStationAddress = l.getSendStationAddress(); r.boxSize = l.getBoxSize(); r.delivererEmployeeNo = l.getDelivererEmployeeNo();
        r.kanbanCardNo = l.getKanbanCardNo(); r.areaCode = l.getAreaCode();
        r.projectCode = l.getProjectCode(); r.routeName = l.getRouteName(); r.deliveryAddress = l.getDeliveryAddress();
        r.businessCode = l.getBusinessCode(); r.gridCode = l.getGridCode(); r.pointOfUseAddress = l.getPointOfUseAddress(); r.routing = l.getRouting(); r.cardNo = l.getCardNo(); r.cardTotal = l.getCardTotal();
        r.supermarketBusiness = l.getSupermarketBusiness(); r.supermarketGrid = l.getSupermarketGrid(); r.supermarketAddress = l.getSupermarketAddress();
        r.materialCode = l.getMaterialCode(); r.materialName = l.getMaterialName(); r.materialImageUrl = l.getMaterialImageUrl(); r.standardQty = l.getStandardQty(); r.boxSide = l.getBoxSide(); r.labelUsageType = l.getLabelUsageType(); r.deliveryMode = l.getDeliveryMode(); r.containerType = l.getContainerType();
        r.warehouseLocation = l.getWarehouseLocation(); r.specText = l.getSpecText(); r.printDate = l.getPrintDate() == null ? null : l.getPrintDate().toString();
        r.status = l.getStatus() == null ? null : l.getStatus().name(); r.rawPayload = l.getRawPayload(); r.fieldSnapshotJson = l.getFieldSnapshotJson();
        return r;
    }

    private String labelPrintPayload(LabelEntity l, String printerName, boolean reprint) {
        return "{\"printJobNo\":\"" + esc(IdGenerator.id("LBLPRN")) + "\",\"printType\":\"LABEL_TEMPLATE\",\"printerName\":\"" + esc(printerName) + "\",\"reprint\":\"" + reprint + "\",\"labelCode\":\"" + esc(l.getLabelCode()) + "\",\"labelType\":\"" + esc(l.getLabelType()) + "\",\"primaryScanValue\":\"" + esc(l.getPrimaryScanValue()) + "\",\"warehouseCode\":\"" + esc(l.getWarehouseCode()) + "\",\"materialCode\":\"" + esc(l.getMaterialCode()) + "\",\"materialName\":\"" + esc(l.getMaterialName()) + "\",\"materialImageUrl\":\"" + esc(l.getMaterialImageUrl()) + "\",\"qty\":\"" + l.getStandardQty() + "\",\"warehouseAddress\":\"" + esc(l.getWarehouseAddress()) + "\",\"sendStationAddress\":\"" + esc(firstNotBlank(l.getSendStationAddress(), l.getDeliveryAddress(), l.getStationCode())) + "\",\"templateCode\":\"" + esc(l.getTemplateCode()) + "\"}";
    }

    private String esc(String v) { return v == null ? "" : v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n"); }

    private void ensureUnique(String labelCode, String primary, String barcode, String warehouseCode, String kanban) {
        if (labelCode != null && labelRepository.findByLabelCode(labelCode).isPresent()) throw new BusinessException(ErrorCode.DATA_DIRTY, "内部标签编码已存在：" + labelCode);
        if (primary != null && labelRepository.existsByPrimaryScanValue(primary)) throw new BusinessException(ErrorCode.DATA_DIRTY, "主扫码值已存在，禁止重复建档：" + primary);
        if (barcode != null && labelRepository.existsByBarcodeValue(barcode)) throw new BusinessException(ErrorCode.DATA_DIRTY, "条码值已存在，禁止重复建档：" + barcode);
        if (warehouseCode != null && labelRepository.existsByWarehouseCode(warehouseCode)) throw new BusinessException(ErrorCode.DATA_DIRTY, "仓库代码已存在，禁止重复建档：" + warehouseCode);
        if (kanban != null && labelRepository.existsByKanbanCardNo(kanban)) throw new BusinessException(ErrorCode.DATA_DIRTY, "看板卡号已存在，禁止重复建档：" + kanban);
    }
    private String normalizeType(String v) { return v == null ? null : v.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_'); }
    private String templateByType(String labelType) { return "FACTORY_PULL_BARCODE".equals(labelType) ? "FACTORY_PULL" : ("POINT_OF_USE_CARD".equals(labelType) ? "POINT_OF_USE" : ("SITE_KANBAN_BARCODE".equals(labelType) ? "KANBAN_SITE" : "GENERIC_LABEL")); }
    private String buildLabelCode(String labelType, String primary, String materialCode, Integer cardNo) {
        String base = firstNotBlank(primary, materialCode + (cardNo == null ? "" : "-" + cardNo), IdGenerator.id("LBL"));
        return normalizeType(labelType) + "-" + base.replaceAll("[^A-Za-z0-9_-]", "-");
    }
    private String composePointOfUseCode(LabelDtos.UniversalLabelRequest r) {
        if (!"POINT_OF_USE_CARD".equals(normalizeType(r.labelType))) return null;
        return String.join("-", nonBlank(firstNotBlank(r.businessCode, r.projectCode), firstNotBlank(r.pointOfUseAddress, r.deliveryAddress), r.materialCode, r.cardNo == null ? null : String.valueOf(r.cardNo)));
    }
    private List<String> nonBlank(String... arr) { List<String> out = new ArrayList<>(); if (arr != null) for (String a : arr) if (a != null && !a.isBlank()) out.add(a.trim()); return out; }
    private String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private String firstNotBlank(String... values) { if (values == null) return null; for (String v : values) if (v != null && !v.isBlank()) return v.trim(); return null; }
}
