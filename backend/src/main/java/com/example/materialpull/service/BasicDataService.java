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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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
    private final DataScopeService dataScopeService;
    private final UserDeliveryAreaRepository userDeliveryAreaRepository;
    private final FactoryRepository factoryRepository;
    private final DeliveryAreaRepository deliveryAreaRepository;
    private final UserScopeAuditService userScopeAuditService;

    @Transactional(readOnly = true)
    public List<BasicDataDtos.UserResponse> users() {
        List<UserEntity> users = userRepository.findAll();
        if (RequestContext.getRole() == UserRole.SUB_ADMIN) {
            String factory = dataScopeService.currentFactory();
            users = users.stream()
                    .filter(user -> user.getRole() != UserRole.ADMIN && user.getRole() != UserRole.SUB_ADMIN)
                    .filter(user -> factory.equalsIgnoreCase(firstNonBlank(user.getFactory(), "")))
                    .toList();
        }
        List<Long> userIds = users.stream().map(UserEntity::getId).filter(Objects::nonNull).toList();
        List<UserDeliveryAreaEntity> areaLinks = userIds.isEmpty() ? List.of() : userDeliveryAreaRepository.findByUserIdIn(userIds);
        Map<Long, List<String>> areasByUser = areaLinks.stream()
                .collect(java.util.stream.Collectors.groupingBy(UserDeliveryAreaEntity::getUserId,
                        java.util.stream.Collectors.mapping(UserDeliveryAreaEntity::getDeliveryArea, java.util.stream.Collectors.toList())));
        return users.stream()
                .sorted(Comparator.comparing(UserEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(user -> toUserResponse(user, areasByUser.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    @Transactional
    public BasicDataDtos.UserResponse saveUser(BasicDataDtos.UserRequest req) {
        if (req == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "用户内容不能为空");
        String username = guard.notBlank(req.username, "账号");
        UserEntity user = req.id == null ? new UserEntity() : userRepository.findById(req.id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：" + req.id));
        boolean existingUser = user.getId() != null;
        requireCanManageUser(user.getId(), user.getRole(), user.getFactory());
        if (user.getId() != null && req.version == null) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "账号版本缺失，请刷新后重试");
        }
        if (user.getId() != null && !Objects.equals(req.version, user.getVersion())) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "账号已被其他管理员修改，请刷新后重试");
        }
        userRepository.findByUsername(username).ifPresent(old -> {
            if (!Objects.equals(old.getId(), user.getId())) throw new BusinessException(ErrorCode.DATA_DIRTY, "账号已存在：" + username);
        });
        if (req.id == null && (req.password == null || req.password.isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新增用户必须填写初始密码");
        }
        UserRole newRole = req.role == null ? UserRole.VIEWER : req.role;
        String newFactory = normalizeAndValidateUserScope(newRole, req.factory, req.deliveryAreas);
        List<String> newAreas = normalizeAreas(req.deliveryAreas);
        requireCanDelegate(newRole, newFactory, newAreas);
        boolean newEnabled = req.enabled == null || req.enabled;
        List<String> oldAreas = user.getId() == null ? List.of() : userDeliveryAreaRepository.findByUserId(user.getId()).stream()
                .map(UserDeliveryAreaEntity::getDeliveryArea).toList();
        UserScopeAuditService.UserSnapshot before = user.getId() == null ? null : userScopeAuditService.snapshot(user, oldAreas);
        boolean securityChanged = user.getId() != null && (!Objects.equals(user.getRole(), newRole)
                || !Objects.equals(user.getEnabled(), newEnabled)
                || !Objects.equals(blankToNull(user.getFactory()), newFactory)
                || !normalizedAreaKeys(oldAreas).equals(normalizedAreaKeys(newAreas))
                || (req.password != null && !req.password.isBlank()));
        if (user.getId() != null && user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getEnabled())
                && (newRole != UserRole.ADMIN || !newEnabled)
                && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "系统至少需要保留一个启用的管理员账号");
        }
        user.setUsername(username);
        user.setRealName(req.realName == null || req.realName.isBlank() ? username : req.realName.trim());
        user.setRole(newRole);
        user.setFactory(newFactory);
        user.setPhone(req.phone);
        user.setEnabled(newEnabled);
        if (req.password != null && !req.password.isBlank()) {
            passwordService.validateNewPassword(req.password);
            user.setPasswordHash(passwordService.hash(req.password.trim()));
            user.setPasswordUpdatedAt(LocalDateTime.now());
        }
        UserEntity saved = userRepository.save(user);
        userDeliveryAreaRepository.deleteByUserId(saved.getId());
        if (!newAreas.isEmpty()) {
            List<UserDeliveryAreaEntity> links = newAreas.stream().map(area -> {
                UserDeliveryAreaEntity link = new UserDeliveryAreaEntity();
                link.setUserId(saved.getId());
                link.setDeliveryArea(area);
                return link;
            }).toList();
            userDeliveryAreaRepository.saveAll(links);
        }
        if (securityChanged) tokenService.revokeUserSessions(saved.getId());
        userScopeAuditService.record(existingUser ? "UPDATE" : "CREATE", before, saved, newAreas, req.changeReason);
        return toUserResponse(saved, newAreas);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "用户ID不能为空");
        UserEntity user = userRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在：" + id));
        if (Objects.equals(user.getUsername(), RequestContext.getUsername())) throw new BusinessException(ErrorCode.STATE_CONFLICT, "不能删除当前登录账号");
        requireCanManageUser(user.getId(), user.getRole(), user.getFactory());
        if (user.getRole() == UserRole.ADMIN && Boolean.TRUE.equals(user.getEnabled()) && userRepository.countByRoleAndEnabledTrue(UserRole.ADMIN) <= 1) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "系统至少需要保留一个启用的管理员账号");
        }
        UserScopeAuditService.UserSnapshot before = userScopeAuditService.snapshot(user,
                userDeliveryAreaRepository.findByUserId(id).stream().map(UserDeliveryAreaEntity::getDeliveryArea).toList());
        userDeliveryAreaRepository.deleteByUserId(id);
        userRepository.delete(user);
        tokenService.revokeUserSessions(id);
        userScopeAuditService.recordDeletion(before, null);
    }

    private BasicDataDtos.UserResponse toUserResponse(UserEntity user, List<String> deliveryAreas) {
        BasicDataDtos.UserResponse r = new BasicDataDtos.UserResponse();
        r.id = user.getId();
        r.username = user.getUsername();
        r.realName = user.getRealName();
        r.role = user.getRole();
        r.roleLabel = user.getRole() == null ? null : user.getRole().label;
        r.phone = user.getPhone();
        r.enabled = user.getEnabled();
        r.factory = user.getFactory();
        r.deliveryAreas = deliveryAreas == null ? List.of() : deliveryAreas;
        r.version = user.getVersion();
        r.lastLoginAt = user.getLastLoginAt();
        r.createdAt = user.getCreatedAt();
        r.updatedAt = user.getUpdatedAt();
        return r;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> userScopeOptions() {
        List<DeliveryAreaEntity> dictionary;
        String factoryFilter = null;
        if (RequestContext.getRole() == UserRole.SUB_ADMIN) {
            String factory = dataScopeService.currentFactory();
            factoryFilter = factory;
            dictionary = deliveryAreaRepository.findByFactoryCodeAndEnabledTrueOrderByDisplayOrderAscAreaCodeAsc(factory);
            return Map.of("factories", factoryRepository.findByFactoryCode(factory).stream().toList(), "deliveryAreas", mergeMappingAreas(factory, dictionary));
        }
        if (!dataScopeService.isGlobalAdmin()) throw new BusinessException(ErrorCode.FORBIDDEN, "只有全局管理员可以管理账号范围");
        List<DeliveryAreaEntity> all = deliveryAreaRepository.findByEnabledTrueOrderByFactoryCodeAscDisplayOrderAscAreaCodeAsc();
        return Map.of("factories", factoryRepository.findByEnabledTrueOrderByDisplayOrderAscFactoryCodeAsc(),
                "deliveryAreas", mergeMappingAreas(null, all));
    }

    private List<DeliveryAreaEntity> mergeMappingAreas(String factory, List<DeliveryAreaEntity> source) {
        Map<String, DeliveryAreaEntity> merged = new LinkedHashMap<>();
        for (DeliveryAreaEntity e : source) merged.put(e.getFactoryCode() + "\u0000" + e.getAreaCode(), e);
        List<String> factories = factory == null ? factoryRepository.findByEnabledTrueOrderByDisplayOrderAscFactoryCodeAsc().stream().map(FactoryEntity::getFactoryCode).toList() : List.of(factory);
        for (String f : factories) for (String area : mappingRepository.findDistinctDeliveryAreasByFactory(f)) {
            String key = f + "\u0000" + area;
            if (!merged.containsKey(key)) { DeliveryAreaEntity e = new DeliveryAreaEntity(); e.setFactoryCode(f); e.setAreaCode(area); e.setAreaName(area); e.setEnabled(true); merged.put(key, e); }
        }
        return new ArrayList<>(merged.values());
    }

    private void requireCanManageUser(Long targetId, UserRole targetRole, String targetFactory) {
        if (Objects.equals(targetId, RequestContext.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能通过账号管理页面修改自己的安全属性");
        }
        if (dataScopeService.isGlobalAdmin()) {
            if (targetRole == UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN, "全局管理员不能修改同级 ADMIN 账号");
            return;
        }
        if (RequestContext.getRole() != UserRole.SUB_ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN, "当前账号无权管理用户");
        if (targetRole == UserRole.ADMIN || targetRole == UserRole.SUB_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "SUB_ADMIN 不能管理 ADMIN 或其他 SUB_ADMIN");
        }
        if (targetId != null && !dataScopeService.canAccessFactoryOnly(targetFactory)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能管理其他工厂的账号");
        }
    }

    private void requireCanDelegate(UserRole role, String factory, List<String> areas) {
        if (dataScopeService.isGlobalAdmin()) {
            if (role == UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN, "不能通过该入口创建或修改同级 ADMIN");
            return;
        }
        if (role == UserRole.ADMIN || role == UserRole.SUB_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "SUB_ADMIN 不能委派 ADMIN 或 SUB_ADMIN 角色");
        }
        if (!dataScopeService.currentFactory().equalsIgnoreCase(factory)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能分配当前工厂");
        }
        Set<String> allowed = normalizedAreaKeys(dataScopeService.currentDeliveryAreas());
        // 未选择配送区域表示当前工厂全区域，可委派当前工厂任意合法区域。
        if (!allowed.isEmpty() && !allowed.containsAll(normalizedAreaKeys(areas))) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能分配自身配送区域的子集");
        }
    }

    private String normalizeAndValidateUserScope(UserRole role, String factory, List<String> areas) {
        if (role == UserRole.ADMIN) {
            if (blankToNull(factory) != null || !normalizeAreas(areas).isEmpty()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "全局 ADMIN 不能绑定工厂或配送区域");
            }
            return null;
        }
        String normalizedFactory = guard.notBlank(factory, "工厂");
        FactoryEntity factoryEntity = factoryRepository.findByFactoryCode(normalizedFactory)
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PARAM_ERROR, "工厂不存在或已停用：" + normalizedFactory));
        List<String> normalizedAreas = normalizeAreas(areas);
        // 配送区域允许手动维护；空列表表示该工厂全部区域。
        return factoryEntity.getFactoryCode();
    }

    private List<String> normalizeAreas(List<String> areas) {
        if (areas == null) return List.of();
        return areas.stream().map(this::blankToNull).filter(Objects::nonNull).distinct().sorted().toList();
    }

    private Set<String> normalizedAreaKeys(List<String> areas) {
        return normalizeAreas(areas).stream().map(area -> area.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
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
    public void deleteMapping(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "料号映射ID不能为空");
        MaterialMappingEntity e = mappingRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "料号映射不存在：" + id));
        dataScopeService.requireAccessFactoryArea(e.getFactory(), e.getDeliveryArea());
        // 补货任务已经保存映射快照，不依赖映射外键。这里必须真正删除，
        // 否则 enabled=false 的记录仍会出现在基础数据列表中，用户会误以为删除失败。
        mappingRepository.delete(e);
        mappingRepository.flush();
    }

    @Transactional
    public void disableStationMaterial(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "工位用料ID不能为空");
        StationMaterialEntity e = stationMaterialRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "工位用料不存在：" + id));
        e.setEnabled(false);
        e.setRemark(firstNonBlank(e.getRemark(), "已停用，历史任务和标签保留追溯"));
        stationMaterialRepository.save(e);
    }

    /**
     * 批量删除料号映射。
     * scope=ALL 清空全部；scope=AREA 按配送区域删除；否则按 ids 删除。
     * 与单条删除保持一致，均为物理删除；历史补货任务保留自己的映射快照。
     */
    @Transactional
    public Map<String, Object> deleteMappings(String scope, String deliveryArea, List<Long> ids) {
        String mode = scope == null ? "" : scope.trim().toUpperCase(Locale.ROOT);
        if ("ALL".equals(mode)) {
            if (!dataScopeService.isGlobalAdmin()) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只有全局管理员可以删除全部料号映射");
            }
            long total = mappingRepository.count();
            mappingRepository.deleteAllInBatch();
            return Map.of("deleted", total, "mode", "ALL");
        }
        if ("AREA".equals(mode)) {
            String area = blankToNull(deliveryArea);
            if (area == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请指定要删除的配送区域");
            if (dataScopeService.isGlobalAdmin()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "全局管理员按区域删除时必须通过限定工厂的入口");
            }
            String factory = dataScopeService.currentFactory();
            dataScopeService.requireAccessFactoryArea(factory, area);
            int deleted = mappingRepository.deleteByFactoryAndDeliveryArea(factory, area);
            return Map.of("deleted", deleted, "mode", "AREA", "factory", factory, "deliveryArea", area);
        }
        if (ids == null || ids.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要删除的料号映射");
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要删除的料号映射");
        List<MaterialMappingEntity> mappings = mappingRepository.findAllById(distinct);
        if (mappings.size() != distinct.size()) throw new BusinessException(ErrorCode.NOT_FOUND, "部分料号映射不存在");
        dataScopeService.requireAccessBatch(mappings, MaterialMappingEntity::getFactory, MaterialMappingEntity::getDeliveryArea);
        mappingRepository.deleteAllInBatch(mappings);
        return Map.of("deleted", distinct.size(), "mode", "IDS");
    }

    @Transactional(readOnly = true)
    public List<MaterialMappingEntity> mappings() {
        if (dataScopeService.isGlobalAdmin()) return mappingRepository.findAllByOrderByLineMaterialCodeAscMappingOrderAscIdAsc();
        return mappingRepository.findByFactoryIgnoreCaseAndDeliveryAreaInOrderByLineMaterialCodeAscMappingOrderAscIdAsc(
                dataScopeService.currentFactory(), dataScopeService.currentDeliveryAreas());
    }

    @Transactional(readOnly = true)
    public Page<MaterialMappingEntity> mappings(String keyword, PageRequest pageable) {
        String query = keyword == null ? "" : keyword.trim();
        if (dataScopeService.isGlobalAdmin()) {
            return mappingRepository.findByLineMaterialCodeContainingIgnoreCaseOrWarehouseCodeContainingIgnoreCaseOrWarehouseMaterialCodeContainingIgnoreCaseOrDeliveryAddressContainingIgnoreCaseOrderByLineMaterialCodeAscMappingOrderAscIdAsc(query, query, query, query, pageable);
        }
        return mappingRepository.findScoped(dataScopeService.currentFactory(), dataScopeService.currentDeliveryAreas(), query, pageable);
    }

    @Transactional(readOnly = true)
    public List<MaterialMappingEntity> exportMappings() {
        return mappings();
    }

    /**
     * 校验并补齐料号映射字段，不访问数据库。
     * 导入的批量 upsert 与单条保存共用同一套字段规则，避免两条链路校验口径不一致。
     */
    public void normalizeMapping(MaterialMappingEntity entity) {
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
        entity.setWarehouseLocation(blankToNull(entity.getWarehouseLocation()));
        entity.setDeliveryAddress(blankToNull(entity.getDeliveryAddress()));
        entity.setDeliveryArea(guard.notBlank(entity.getDeliveryArea(), "配送区域"));
        String factory = guard.notBlank(entity.getFactory(), "工厂");
        if (!Set.of("弋江", "三山").contains(factory)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "工厂只能是弋江或三山");
        }
        entity.setFactory(factory);
        entity.setEnabled(entity.getEnabled() == null || entity.getEnabled());
    }

    /**
     * 按“仓库代号”为唯一主键批量 upsert 料号映射，供导入使用。
     * 入参必须已通过 {@link #normalizeMapping} 校验。
     * 一次事务内只做 1 次存量查询 + 1 次批量写入，避免逐行两次数据库往返导致大文件导入超时。
     * 同一批内仓库代号重复时以最后一条为准（与“上传信息更新、以最后上传为准”的口径一致）。
     */
    @Transactional
    public void upsertMappingsByWarehouseCode(List<MaterialMappingEntity> rows) {
        if (rows == null || rows.isEmpty()) return;
        rows.forEach(this::applyTrustedMappingScope);
        Map<String, MaterialMappingEntity> deduped = new LinkedHashMap<>();
        for (MaterialMappingEntity row : rows) deduped.put(row.getWarehouseCode(), row);
        Map<String, Long> existingIds = new HashMap<>();
        for (MaterialMappingEntity old : mappingRepository.findByWarehouseCodeInAndEnabledTrue(deduped.keySet())) {
            dataScopeService.requireAccessFactoryArea(old.getFactory(), old.getDeliveryArea());
            existingIds.putIfAbsent(old.getWarehouseCode(), old.getId());
        }
        for (MaterialMappingEntity row : deduped.values()) {
            row.setId(existingIds.get(row.getWarehouseCode()));
        }
        mappingRepository.saveAll(deduped.values());
    }

    @Transactional
    public MaterialMappingEntity saveMapping(MaterialMappingEntity entity) {
        normalizeMapping(entity);
        if (entity.getId() != null) {
            MaterialMappingEntity old = mappingRepository.findById(entity.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "料号映射不存在：" + entity.getId()));
            dataScopeService.requireAccessFactoryArea(old.getFactory(), old.getDeliveryArea());
        }
        applyTrustedMappingScope(entity);
        mappingRepository.findByWarehouseCodeAndEnabledTrue(entity.getWarehouseCode()).ifPresent(old -> {
            dataScopeService.requireAccessFactoryArea(old.getFactory(), old.getDeliveryArea());
            if (!Objects.equals(old.getId(), entity.getId()) && Boolean.TRUE.equals(entity.getEnabled())) {
                throw new BusinessException(ErrorCode.DATA_DIRTY, "该仓库代号已有启用映射：" + entity.getWarehouseCode());
            }
        });
        return mappingRepository.save(entity);
    }

    private void applyTrustedMappingScope(MaterialMappingEntity entity) {
        if (dataScopeService.isGlobalAdmin()) {
            dataScopeService.requireAccessFactoryArea(entity.getFactory(), entity.getDeliveryArea());
            return;
        }
        entity.setFactory(dataScopeService.currentFactory());
        dataScopeService.requireAccessFactoryArea(entity.getFactory(), entity.getDeliveryArea());
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
        if (inv.getId() != null) dataScopeService.requireAccessFactoryArea(inv.getFactory(), inv.getDeliveryArea());
        if (dataScopeService.isGlobalAdmin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "全局管理员没有可信工厂范围，不能直接维护区域库存");
        }
        String factory = dataScopeService.currentFactory();
        String deliveryArea = guard.notBlank(req.deliveryArea, "配送区域");
        dataScopeService.requireAccessFactoryArea(factory, deliveryArea);
        inv.setFactory(factory);
        inv.setDeliveryArea(deliveryArea);
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
        if (inventoryRepository.existsByFactoryIgnoreCaseAndDeliveryAreaAndWarehouseCodeAndLocationCodeAndWarehouseMaterialCodeAndIdNot(
                factory, deliveryArea, inv.getWarehouseCode(), inv.getLocationCode(), inv.getWarehouseMaterialCode(), inv.getId() == null ? -1L : inv.getId())) {
            throw new BusinessException(ErrorCode.DATA_DIRTY, "同一工厂、区域、仓库、库位和仓库料号的库存记录已存在");
        }
        inv.recalc();
        return inventoryRepository.save(inv);
    }

    @Transactional
    public void deleteInventory(Long id) {
        if (id == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "库存ID不能为空");
        InventoryEntity inv = inventoryRepository.findById(id).orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "库存记录不存在：" + id));
        dataScopeService.requireAccessFactoryArea(inv.getFactory(), inv.getDeliveryArea());
        if (nvl(inv.getLockedQty()).compareTo(BigDecimal.ZERO) > 0) throw new BusinessException(ErrorCode.STATE_CONFLICT, "该库存存在锁定数量，不能直接删除");
        inventoryRepository.delete(inv);
    }

    @Transactional(readOnly = true)
    public List<InventoryEntity> inventory() {
        if (dataScopeService.isGlobalAdmin()) return inventoryRepository.findAll(PageRequest.of(0, 1000, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))).getContent();
        return inventoryRepository.findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByUpdatedAtDesc(
                dataScopeService.currentFactory(), dataScopeService.currentDeliveryAreas());
    }

    @Transactional(readOnly = true)
    public List<BoxEntity> boxes() {
        if (dataScopeService.isGlobalAdmin()) return boxRepository.findAll(PageRequest.of(0, 1000, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"))).getContent();
        return boxRepository.findTop1000ByFactoryIgnoreCaseAndDeliveryAreaInOrderByIdDesc(
                dataScopeService.currentFactory(), dataScopeService.currentDeliveryAreas());
    }

    @Transactional
    public SystemConfigEntity saveConfig(BasicDataDtos.ConfigRequest req) {
        dataScopeService.requireGlobalAdmin();
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
        MaterialMappingEntity scope = requireUniqueMappingScope(s.getWarehouseMaterialCode());
        dataScopeService.requireAccessFactoryArea(scope.getFactory(), scope.getDeliveryArea());
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
            b.setFactory(scope.getFactory());
            b.setDeliveryArea(scope.getDeliveryArea());
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
            syncLabelFromBoxAndStation(b, s, scope);
        }
    }

    private void syncLabelFromBoxAndStation(BoxEntity b, StationMaterialEntity s, MaterialMappingEntity scope) {
        if (b.getLabelCode() == null || b.getLabelCode().isBlank()) return;
        labelRepository.findByLabelCode(b.getLabelCode()).ifPresent(l -> {
            l.setFactory(scope.getFactory());
            l.setDeliveryArea(scope.getDeliveryArea());
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
        MaterialMappingEntity scope = requireUniqueMappingScope(s.getWarehouseMaterialCode());
        dataScopeService.requireAccessFactoryArea(scope.getFactory(), scope.getDeliveryArea());
        String boxCode = pairCode + "-" + side;
        String labelCode = "LBL-" + s.getStationCode() + "-" + s.getMaterialCode() + "-" + side;
        if (boxRepository.findByBoxCode(boxCode).isPresent()) throw new BusinessException(ErrorCode.DATA_DIRTY, "盒号已存在：" + boxCode);
        if (labelRepository.findByLabelCode(labelCode).isPresent()) throw new BusinessException(ErrorCode.DATA_DIRTY, "标签已存在：" + labelCode);

        BoxEntity b = new BoxEntity();
        b.setFactory(scope.getFactory()); b.setDeliveryArea(scope.getDeliveryArea());
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
        l.setFactory(scope.getFactory()); l.setDeliveryArea(scope.getDeliveryArea());
        l.setLabelCode(labelCode); l.setLabelType("INTERNAL_BOX_LABEL"); l.setCodeCarrierType("QR_CODE"); l.setPrimaryScanValue(labelCode);
        l.setBoxCode(boxCode); l.setPairCode(pairCode); l.setBoxSide(side);
        l.setLineCode(s.getLineCode()); l.setStationCode(s.getStationCode()); l.setStationName(s.getStationName());
        l.setProjectCode(s.getProjectCode()); l.setRouteName(s.getRouteName()); l.setDeliveryAddress(s.getDeliveryAddress());
        l.setAreaCode(s.getAreaCode()); l.setWarehouseLocation(s.getWarehouseLocation()); l.setSpecText(s.getSpecText()); l.setUnit(s.getUnit());
        l.setMaterialCode(s.getMaterialCode()); l.setMaterialName(s.getMaterialName()); l.setWarehouseMaterialCode(s.getWarehouseMaterialCode()); l.setMaterialImageUrl(findMaterialImageUrl(s.getMaterialCode()));
        l.setStandardQty(nvl(s.getStandardBoxQty())); l.setTemplateCode("KANBAN_SITE"); l.setStatus(LabelStatus.BOUND);
        labelRepository.save(l);
    }

    private MaterialMappingEntity requireUniqueMappingScope(String warehouseCode) {
        String code = guard.notBlank(warehouseCode, "仓库代码");
        List<MaterialMappingEntity> candidates = mappingRepository.findAllByWarehouseCodeAndEnabledTrueOrderByIdAsc(code);
        if (candidates.isEmpty()) throw new BusinessException(ErrorCode.NOT_FOUND, "仓库代码未找到启用的范围映射：" + code);
        if (candidates.size() != 1) throw new BusinessException(ErrorCode.DATA_DIRTY, "仓库代码匹配到多个范围，拒绝创建 Box/Label：" + code);
        MaterialMappingEntity scope = candidates.get(0);
        if (blankToNull(scope.getFactory()) == null || blankToNull(scope.getDeliveryArea()) == null) {
            throw new BusinessException(ErrorCode.DATA_DIRTY, "仓库代码映射缺少工厂或配送区域：" + code);
        }
        return scope;
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
