package com.example.materialpull.service;

import com.example.materialpull.common.BusinessException;
import com.example.materialpull.common.ErrorCode;
import com.example.materialpull.common.RequestContext;
import com.example.materialpull.entity.MaterialMappingEntity;
import com.example.materialpull.enums.UserRole;
import com.example.materialpull.repository.MaterialMappingRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MappingQueryService {
    private static final String EMPTY_VALUE = "__MAPPING_FILTER_EMPTY__";
    private static final int MAX_FILTER_VALUES = 10000;
    private static final int MAX_FILTER_JSON_LENGTH = 120000;
    private static final Map<String, Class<?>> FIELDS = Map.ofEntries(
            Map.entry("mappingOrder", Integer.class), Map.entry("lineMaterialCode", String.class),
            Map.entry("factory", String.class), Map.entry("warehouseCode", String.class),
            Map.entry("warehouseMaterialCode", String.class),
            Map.entry("boxSize", String.class), Map.entry("quantity", BigDecimal.class),
            Map.entry("singleUnitUsage", BigDecimal.class), Map.entry("deliveryType", String.class),
            Map.entry("warehouseLocation", String.class), Map.entry("deliveryAddress", String.class),
            Map.entry("description", String.class), Map.entry("remark", String.class),
            Map.entry("deliveryArea", String.class));
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MaterialMappingRepository repository;
    private final DataScopeService dataScopeService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Page<MaterialMappingEntity> page(String keyword, String filtersJson, Pageable pageable) {
        Map<String, List<String>> filters = parseFilters(filtersJson);
        return repository.findAll(specification(keyword, filters, null), pageable);
    }

    @Transactional(readOnly = true)
    public List<String> options(String field, String keyword, String filtersJson, String search) {
        requireField(field);
        Map<String, List<String>> filters = parseFilters(filtersJson);
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<MaterialMappingEntity> root = query.from(MaterialMappingEntity.class);
        List<Predicate> predicates = predicates(root, cb, keyword, filters, field);
        String term = search == null ? "" : search.trim();
        if (term.length() > 100) throw new BusinessException(ErrorCode.PARAM_ERROR, "筛选搜索词过长");
        if (!term.isEmpty()) {
            predicates.add(cb.like(cb.lower(root.get(field).as(String.class)), contains(term), '\\'));
        }
        query.select(root.get(field)).distinct(true)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(cb.asc(root.get(field)));
        return entityManager.createQuery(query).getResultList().stream()
                .map(value -> value == null || String.valueOf(value).isBlank() ? EMPTY_VALUE : String.valueOf(value))
                .distinct().toList();
    }

    Map<String, List<String>> parseFilters(String json) {
        if (json == null || json.isBlank()) return Map.of();
        if (json.length() > MAX_FILTER_JSON_LENGTH) throw new BusinessException(ErrorCode.PARAM_ERROR, "筛选条件过长");
        try {
            Map<String, List<String>> parsed = JSON.readValue(json, new TypeReference<>() {});
            if (parsed == null || parsed.size() > FIELDS.size()) throw new IllegalArgumentException();
            Map<String, List<String>> result = new LinkedHashMap<>();
            for (var entry : parsed.entrySet()) {
                requireField(entry.getKey());
                List<String> values = entry.getValue();
                if (values == null || values.size() > MAX_FILTER_VALUES) throw new IllegalArgumentException();
                List<String> clean = values.stream().filter(value -> value != null && !value.isBlank())
                        .map(String::trim).distinct().toList();
                if (clean.stream().anyMatch(value -> value.length() > 255)) throw new IllegalArgumentException();
                if (!clean.isEmpty()) result.put(entry.getKey(), clean);
            }
            return result;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "筛选条件格式错误");
        }
    }

    private void requireField(String field) {
        if (!FIELDS.containsKey(field)) throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持筛选字段：" + field);
    }

    private Specification<MaterialMappingEntity> specification(String keyword, Map<String, List<String>> filters, String omittedField) {
        return (root, query, cb) -> cb.and(predicates(root, cb, keyword, filters, omittedField).toArray(Predicate[]::new));
    }

    private List<Predicate> predicates(Root<MaterialMappingEntity> root, CriteriaBuilder cb,
                                       String keyword, Map<String, List<String>> filters, String omittedField) {
        List<Predicate> predicates = new ArrayList<>();
        if (!dataScopeService.isGlobalAdmin()) {
            String factory = dataScopeService.currentFactory().toLowerCase(Locale.ROOT);
            predicates.add(cb.equal(cb.lower(root.get("factory")), factory));
            if (RequestContext.getRole() != UserRole.SUB_ADMIN) {
                List<String> areas = dataScopeService.currentDeliveryAreas();
                if (!areas.isEmpty()) predicates.add(root.get("deliveryArea").in(areas));
            }
        }
        String text = keyword == null ? "" : keyword.trim();
        if (!text.isEmpty()) {
            String pattern = contains(text);
            predicates.add(cb.or(
                    cb.like(cb.lower(root.get("lineMaterialCode")), pattern, '\\'),
                    cb.like(cb.lower(root.get("warehouseCode")), pattern, '\\'),
                    cb.like(cb.lower(root.get("warehouseMaterialCode")), pattern, '\\'),
                    cb.like(cb.lower(root.get("deliveryAddress")), pattern, '\\')));
        }
        for (var entry : filters.entrySet()) {
            String field = entry.getKey();
            if (field.equals(omittedField)) continue;
            List<Predicate> choices = new ArrayList<>();
            List<Object> selectedValues = new ArrayList<>();
            for (String value : entry.getValue()) {
                if (EMPTY_VALUE.equals(value)) {
                    choices.add(FIELDS.get(field) == String.class
                            ? cb.or(cb.isNull(root.get(field)), cb.equal(root.get(field), ""))
                            : cb.isNull(root.get(field)));
                } else {
                    selectedValues.add(typedValue(field, value));
                }
            }
            if (!selectedValues.isEmpty()) choices.add(root.get(field).in(selectedValues));
            if (!choices.isEmpty()) predicates.add(cb.or(choices.toArray(Predicate[]::new)));
        }
        return predicates;
    }

    private Object typedValue(String field, String value) {
        try {
            Class<?> type = FIELDS.get(field);
            if (type == Integer.class) return Integer.valueOf(value);
            if (type == BigDecimal.class) return new BigDecimal(value);
            return value;
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "筛选值格式错误：" + field);
        }
    }

    private String contains(String value) {
        return "%" + value.toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                .replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
