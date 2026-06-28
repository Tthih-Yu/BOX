package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.dto.BasicDataDtos;
import com.example.materialpull.entity.*;
import com.example.materialpull.enums.*;
import com.example.materialpull.repository.*;
import com.example.materialpull.resilience.OperationGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BasicDataService {
    private final MaterialRepository materialRepository;
    private final MaterialMappingRepository mappingRepository;
    private final StationMaterialRepository stationMaterialRepository;
    private final BoxRepository boxRepository;
    private final LabelRepository labelRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final SystemConfigRepository configRepository;
    private final OperationGuard guard;
    private final PasswordService passwordService;
    private final AuthTokenService tokenService;

    @Transactional(readOnly = true)
    public List<BasicDataDtos.UserResponse> users() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(UserEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toUserResponse)
                .toList();
    }

    @Transactional
    public BasicDataDtos.UserResponse saveUser(BasicDataDtos.UserRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "用户内容不能为空");
        String username = guard.notBlank(req.username, "账号");
        UserEntity user = req.id == null ? new UserEntity() : userRepository.findById(req.id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：" + req.id));
        userRepository.findByUsername(username).ifPresent(old -> {
            if (!Objects.equals(old.getId(), user.getId())) throw new BusinessException(ErrorCode.DATA_DIRTY, "账号已存在：" + username);
        });
        if (req.id == null && (req.password == null || req.password.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新增用户必须填写初始密码");
        }
        UserRole newRole = req.role == null ? UserRole.VIEWER : req.role;
        boolean newEnabled = req.enabled == null || req.enabled;
        boolean securityChanged = user.getId() != null && (!Objects.equals(user.getRole(), newRole) || !Objects.equals(user.getEnabled(), newEnabled) || (req.password != null && !req.password.isBlank()));
        if (user.getId() != null && user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getEnabled())
                && (newRole != UserRole.ADMIN || !newEnabled)
                && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "系统至少需要保留一个启用的管理员账号");
        }
        user.setUsername(username);
        user.setRealName(req.realName == null || req.realName.isBlank() ? username : req.realName.trim());
        user.setRole(newRole);
        user.setPhone(req.phone);
        user.setEnabled(newEnabled);
        if (req.password != null && !req.password.isBlank()) {
            passwordService.validateNewPassword(req.password);
            user.setPasswordHash(passwordService.hash(req.password.trim()));
            user.setPasswordUpdatedAt(LocalDateTime.now());
        }
        UserEntity saved = userRepository.save(user);
        if (securityChanged) tokenService.revokeUserSessions(saved.getId());
        return toUserResponse(saved);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "用户ID不能为空");
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：" + id));
        if (Objects.equals(user.getUsername(), RequestContext.getUsername())) throw new BusinessException(ErrorCode.STATE_CONFLICT, "不能删除当前登录账号");
        if (user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getEnabled()) && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "系统至少需要保留一个启用的管理员账号");
        }
        userRepository.delete(user);
        tokenService.revokeUserSessions(id);
    }

    private BasicDataDtos.UserResponse toUserResponse(UserEntity user) {
        BasicDataDtos.UserResponse r = new BasicDataDtos.UserResponse();
        r.id = user.getId();
        r.username = user.getUsername();
        r.realName = user.getRealName();
        r.role = user.getRole();
        r.roleLabel = user.getRole() == null ? null : user.getRole().label;
        r.phone = user.getPhone();
        r.enabled = user.getEnabled();
        r.lastLoginAt = user.getLastLoginAt();
        r.createdAt = user.getCreatedAt();
        r.updatedAt = user.getUpdatedAt();
        return r;
    }

    @Transactional
    public MaterialEntity saveMaterial(MaterialEntity entity) {
        if (entity == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "物料不能为空");
        entity.setMaterialCode(guard.notBlank(entity.getMaterialCode(), "线边料号"));
        entity.setWarehouseMaterialCode(guard.notBlank(entity.getWarehouseMaterialCode(), "仓库料号"));
        entity.setMaterialName(guard.notBlank(entity.getMaterialName(), "物料名称"));
        entity.setSafetyStock(nonNegative(entity.getSafetyStock(), "安全库存"));
        entity.setMinPackageQty(nonNegative(entity.getMinPackageQty(), "最小包装数"));
        materialRepository.findByMaterialCode(entity.getMaterialCode()).ifPresent(old -> {
            if (!Objects.equals(old.getId(), entity.getId())) throw new BusinessException(ErrorCode.DATA_DIRTY, "线边料号已存在：" + entity.getMaterialCode());
        });
        return materialRepository.save(entity);
    }

    @Transactional
    public void disableMaterial(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "物料ID不能为空");
        MaterialEntity e = materialRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "物料不存在：" + id));
        e.setEnabled(false);
        e.setRemark(firstNonBlank(e.getRemark(), "已停用，历史任务和标签保留追溯"));
        materialRepository.save(e);
    }

    @Transactional
    public void disableMapping(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "料号映射ID不能为空");
        MaterialMappingEntity e = mappingRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "料号映射不存在：" + id));
        e.setEnabled(false);
        e.setRemark(firstNonBlank(e.getRemark(), "已停用，历史数据保留追溯"));
        mappingRepository.save(e);
    }

    @Transactional
    public void disableStationMaterial(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "工位用料ID不能为空");
        StationMaterialEntity e = stationMaterialRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工位用料不存在：" + id));
        e.setEnabled(false);
        e.setRemark(firstNonBlank(e.getRemark(), "已停用，历史任务和标签保留追溯"));
        stationMaterialRepository.save(e);
    }

    @Transactional
    public MaterialMappingEntity saveMapping(MaterialMappingEntity entity) {
        if (entity == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "料号映射不能为空");
        entity.setLineMaterialCode(guard.notBlank(entity.getLineMaterialCode(), "物料号"));
        entity.setStationCode(blankToNull(entity.getStationCode()));
        String warehouseCode = firstNonBlank(entity.getWarehouseCode(), entity.getWarehouseMaterialCode());
        entity.setWarehouseCode(guard.notBlank(warehouseCode, "仓库代号"));
        entity.setWarehouseMaterialCode(entity.getWarehouseCode());
        entity.setQuantity(nonNegative(entity.getQuantity(), "数量"));
        if (entity.getQuantity().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "数量必须大于0");
        if (entity.getMappingOrder() == null || entity.getMappingOrder() <= 0) entity.setMappingOrder(1);
        String deliveryType = firstNonBlank(entity.getDeliveryType(), "NORMAL").toUpperCase(Locale.ROOT);
        if (!List.of("NORMAL", "URGENT").contains(deliveryType)) throw new BusinessException(ErrorCode.PARAM_ERROR, "用途只能是 NORMAL 或 URGENT");
        entity.setDeliveryType(deliveryType);
        entity.setEnabled(entity.getEnabled() == null || entity.getEnabled());
        mappingRepository.findByWarehouseCodeAndEnabledTrue(entity.getWarehouseCode()).ifPresent(old -> {
            if (!Objects.equals(old.getId(), entity.getId()) && Boolean.TRUE.equals(entity.getEnabled())) {
                throw new BusinessException(ErrorCode.DATA_DIRTY, "该仓库代号已有启用映射：" + entity.getWarehouseCode());
            }
        });
        return mappingRepository.save(entity);
    }

    @Transactional
    public StationMaterialEntity saveStationMaterial(StationMaterialEntity s) {
        if (s == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "工位用料不能为空");
        s.setLineCode(guard.notBlank(s.getLineCode(), "产线编码"));
        s.setStationCode(guard.notBlank(s.getStationCode(), "工位编码"));
        s.setStationName(guard.notBlank(s.getStationName(), "工位名称"));
        s.setMaterialCode(guard.notBlank(s.getMaterialCode(), "线边料号"));
        s.setMaterialName(guard.notBlank(s.getMaterialName(), "物料名称"));
        s.setStandardBoxQty(guard.positive(nvl(s.getStandardBoxQty()), "标准盒量"));
        if (s.getSingleBoxQty() == null || s.getSingleBoxQty().compareTo(BigDecimal.ZERO) <= 0) s.setSingleBoxQty(s.getStandardBoxQty());
        if (s.getSafetyStock() == null) s.setSafetyStock(BigDecimal.ZERO);
        if (s.getMpcThresholdQty() == null || s.getMpcThresholdQty().compareTo(BigDecimal.ZERO) <= 0) s.setMpcThresholdQty(s.getSafetyStock());
        if (s.getProductionCycleMinutes() == null) s.setProductionCycleMinutes(0);
        if (s.getDeliveryCycleMinutes() == null) s.setDeliveryCycleMinutes(0);
        if (s.getForecastEnabled() == null) s.setForecastEnabled(true);
        if (s.getWarehouseMaterialCode() == null || s.getWarehouseMaterialCode().isBlank()) {
            mappingRepository.findByLineMaterialCodeAndEnabledTrueOrderByMappingOrderAscIdAsc(s.getMaterialCode()).stream().findFirst()
                    .ifPresent(m -> s.setWarehouseMaterialCode(m.getWarehouseCode()));
        }
        s.setWarehouseMaterialCode(guard.notBlank(s.getWarehouseMaterialCode(), "仓库料号"));
        StationMaterialEntity saved = stationMaterialRepository.save(s);
        ensureBoxesAndLabels(saved);
        return saved;
    }

    @Transactional
    public InventoryEntity saveInventory(BasicDataDtos.InventorySaveRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "库存内容不能为空");
        InventoryEntity inv = req.id == null ? new InventoryEntity() : inventoryRepository.findById(req.id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "库存记录不存在：" + req.id));
        inv.setWarehouseCode(guard.notBlank(req.warehouseCode, "仓库"));
        inv.setLocationCode(guard.notBlank(req.locationCode, "库位"));
        inv.setWarehouseMaterialCode(guard.notBlank(req.warehouseMaterialCode, "仓库料号"));
        inv.setMaterialCode(req.materialCode == null ? inv.getMaterialCode() : req.materialCode.trim());
        inv.setMaterialName(guard.notBlank(req.materialName, "物料名称"));
        inv.setStockQty(nonNegative(req.stockQty, "账面库存"));
        inv.setSafetyStock(nonNegative(req.safetyStock, "安全库存"));
        inv.setBatchNo(blankToNull(req.batchNo));
        inv.setFrozen(Boolean.TRUE.equals(req.frozen));
        inv.setFreezeReason(blankToNull(req.freezeReason));
        inv.setRemark(blankToNull(req.remark));
        inv.recalc();
        return inventoryRepository.save(inv);
    }

    @Transactional
    public void deleteInventory(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "库存ID不能为空");
        InventoryEntity inv = inventoryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "库存记录不存在：" + id));
        if (nvl(inv.getLockedQty()).compareTo(BigDecimal.ZERO) > 0) throw new BusinessException(ErrorCode.STATE_CONFLICT, "该库存存在锁定数量，不能直接删除");
        inventoryRepository.delete(inv);
    }

    @Transactional
    public SystemConfigEntity saveConfig(BasicDataDtos.ConfigRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "配置内容不能为空");
        SystemConfigEntity cfg = req.id == null ? new SystemConfigEntity() : configRepository.findById(req.id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "配置不存在：" + req.id));
        if (cfg.getId() != null && !Boolean.TRUE.equals(cfg.getEditable())) throw new BusinessException(ErrorCode.FORBIDDEN, "该配置不允许通过页面修改");
        cfg.setConfigKey(guard.notBlank(req.configKey, "参数键"));
        cfg.setConfigName(guard.notBlank(req.configName, "参数名称"));
        cfg.setConfigValue(req.configValue == null ? "" : req.configValue.trim());
        cfg.setRemark(blankToNull(req.remark));
        cfg.setEditable(req.editable == null || req.editable);
        configRepository.findByConfigKey(cfg.getConfigKey()).ifPresent(old -> {
            if (!Objects.equals(old.getId(), cfg.getId())) throw new BusinessException(ErrorCode.DATA_DIRTY, "参数键已存在：" + cfg.getConfigKey());
        });
        return configRepository.save(cfg);
    }

    @Transactional
    public void ensureBoxesAndLabels(StationMaterialEntity s) {
        String pairCode = s.getStationCode() + "-" + s.getMaterialCode();
        List<BoxEntity> exists = boxRepository.findByPairCodeOrderByBoxSideAsc(pairCode);
        if (exists.isEmpty()) {
            createBox(pairCode, "A", s, BoxStatus.IN_USE);
            createBox(pairCode, "B", s, BoxStatus.FULL_STANDBY);
            return;
        }
        if (exists.size() != 2) {
            for (BoxEntity b : exists) {
                b.setHealthStatus("PAIR_BROKEN");
                b.setLastError("保存工位用料时发现AB配对数量异常");
                boxRepository.save(b);
            }
            throw new BusinessException(ErrorCode.DATA_DIRTY, "AB配对数量异常，请先处理 pairCode=" + pairCode);
        }
        for (BoxEntity b : exists) {
            b.setLineCode(s.getLineCode());
            b.setStationCode(s.getStationCode());
            b.setStationName(s.getStationName());
            b.setProjectCode(s.getProjectCode());
            b.setRouteName(s.getRouteName());
            b.setDeliveryAddress(s.getDeliveryAddress());
            b.setAreaCode(s.getAreaCode());
            b.setWarehouseLocation(s.getWarehouseLocation());
            b.setMaterialCode(s.getMaterialCode());
            b.setMaterialName(s.getMaterialName());
            b.setWarehouseMaterialCode(s.getWarehouseMaterialCode());
            b.setStandardQty(nvl(s.getStandardBoxQty()));
            boxRepository.save(b);
            syncLabelFromBoxAndStation(b, s);
        }
    }

    private void syncLabelFromBoxAndStation(BoxEntity b, StationMaterialEntity s) {
        if (b.getLabelCode() == null || b.getLabelCode().isBlank()) return;
        labelRepository.findByLabelCode(b.getLabelCode()).ifPresent(l -> {
            l.setBoxCode(b.getBoxCode());
            l.setPairCode(b.getPairCode());
            l.setBoxSide(b.getBoxSide());
            l.setLineCode(s.getLineCode());
            l.setStationCode(s.getStationCode());
            l.setStationName(s.getStationName());
            l.setProjectCode(s.getProjectCode());
            l.setRouteName(s.getRouteName());
            l.setDeliveryAddress(s.getDeliveryAddress());
            l.setAreaCode(s.getAreaCode());
            l.setWarehouseLocation(s.getWarehouseLocation());
            l.setSpecText(s.getSpecText());
            l.setUnit(s.getUnit());
            l.setMaterialCode(s.getMaterialCode());
            l.setMaterialName(s.getMaterialName());
            l.setWarehouseMaterialCode(s.getWarehouseMaterialCode());
            l.setMaterialImageUrl(findMaterialImageUrl(s.getMaterialCode()));
            l.setStandardQty(nvl(s.getStandardBoxQty()));
            l.setStatus(LabelStatus.BOUND);
            labelRepository.save(l);
        });
    }

    private void createBox(String pairCode, String side, StationMaterialEntity s, BoxStatus status) {
        String boxCode = pairCode + "-" + side;
        String labelCode = "LBL-" + s.getStationCode() + "-" + s.getMaterialCode() + "-" + side;
        if (boxRepository.findByBoxCode(boxCode).isPresent()) throw new BusinessException(ErrorCode.DATA_DIRTY, "盒号已存在：" + boxCode);
        if (labelRepository.findByLabelCode(labelCode).isPresent()) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签已存在：" + labelCode);

        BoxEntity b = new BoxEntity();
        b.setPairCode(pairCode); b.setBoxSide(side); b.setBoxCode(boxCode); b.setLabelCode(labelCode);
        b.setLineCode(s.getLineCode()); b.setStationCode(s.getStationCode()); b.setStationName(s.getStationName());
        b.setProjectCode(s.getProjectCode()); b.setRouteName(s.getRouteName()); b.setDeliveryAddress(s.getDeliveryAddress());
        b.setAreaCode(s.getAreaCode()); b.setWarehouseLocation(s.getWarehouseLocation());
        b.setMaterialCode(s.getMaterialCode()); b.setMaterialName(s.getMaterialName()); b.setWarehouseMaterialCode(s.getWarehouseMaterialCode());
        b.setStandardQty(nvl(s.getStandardBoxQty()));
        b.setCurrentQty(status == BoxStatus.IN_USE ? nvl(s.getStandardBoxQty()).divide(new BigDecimal("2"), 2, java.math.RoundingMode.HALF_UP) : nvl(s.getStandardBoxQty()));
        b.setStatus(status);
        b.setHealthStatus("OK");
        boxRepository.save(b);

        LabelEntity l = new LabelEntity();
        l.setLabelCode(labelCode); l.setLabelType("INTERNAL_BOX_LABEL"); l.setCodeCarrierType("QR_CODE"); l.setPrimaryScanValue(labelCode);
        l.setBoxCode(boxCode); l.setPairCode(pairCode); l.setBoxSide(side);
        l.setLineCode(s.getLineCode()); l.setStationCode(s.getStationCode()); l.setStationName(s.getStationName());
        l.setProjectCode(s.getProjectCode()); l.setRouteName(s.getRouteName()); l.setDeliveryAddress(s.getDeliveryAddress());
        l.setAreaCode(s.getAreaCode()); l.setWarehouseLocation(s.getWarehouseLocation()); l.setSpecText(s.getSpecText()); l.setUnit(s.getUnit());
        l.setMaterialCode(s.getMaterialCode()); l.setMaterialName(s.getMaterialName()); l.setWarehouseMaterialCode(s.getWarehouseMaterialCode()); l.setMaterialImageUrl(findMaterialImageUrl(s.getMaterialCode()));
        l.setStandardQty(nvl(s.getStandardBoxQty())); l.setTemplateCode("KANBAN_SITE"); l.setStatus(LabelStatus.BOUND);
        labelRepository.save(l);
    }

    private String findMaterialImageUrl(String materialCode) {
        if (materialCode == null || materialCode.isBlank()) return null;
        return materialRepository.findByMaterialCode(materialCode.trim()).map(MaterialEntity::getMaterialImageUrl).orElse(null);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
    private String blankToNull(String v) { return v == null || v.isBlank() ? null : v.trim(); }
    private BigDecimal nonNegative(BigDecimal v, String name) {
        BigDecimal value = v == null ? BigDecimal.ZERO : v;
        if (value.compareTo(BigDecimal.ZERO) < 0) throw new BusinessException(ErrorCode.PARAM_ERROR, name + "不能为负数");
        return value;
    }
}
