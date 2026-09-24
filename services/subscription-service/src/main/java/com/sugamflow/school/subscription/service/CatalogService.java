package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.model.CatalogViews;
import com.sugamflow.school.subscription.persistence.entity.BusinessTypeEntity;
import com.sugamflow.school.subscription.persistence.entity.FeatureDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.LimitDefinitionEntity;
import com.sugamflow.school.subscription.persistence.entity.ModuleDefinitionEntity;
import com.sugamflow.school.subscription.persistence.repo.BusinessTypeRepository;
import com.sugamflow.school.subscription.persistence.repo.FeatureDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.LimitDefinitionRepository;
import com.sugamflow.school.subscription.persistence.repo.ModuleDefinitionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Platform catalog (business types / modules / features / limits). Does not affect plan JSON
 * entitlement resolution. Read APIs for all; upsert for Super Admin configuration.
 */
@Service
public class CatalogService {

  private final BusinessTypeRepository businessTypeRepository;
  private final ModuleDefinitionRepository moduleDefinitionRepository;
  private final FeatureDefinitionRepository featureDefinitionRepository;
  private final LimitDefinitionRepository limitDefinitionRepository;

  public CatalogService(
      BusinessTypeRepository businessTypeRepository,
      ModuleDefinitionRepository moduleDefinitionRepository,
      FeatureDefinitionRepository featureDefinitionRepository,
      LimitDefinitionRepository limitDefinitionRepository) {
    this.businessTypeRepository = businessTypeRepository;
    this.moduleDefinitionRepository = moduleDefinitionRepository;
    this.featureDefinitionRepository = featureDefinitionRepository;
    this.limitDefinitionRepository = limitDefinitionRepository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listBusinessTypes() {
    return businessTypeRepository.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
        .map(this::toBusinessType)
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listModules(String businessTypeCode) {
    List<ModuleDefinitionEntity> rows =
        StringUtils.hasText(businessTypeCode)
            ? moduleDefinitionRepository
                .findByBusinessTypeCodeAndActiveTrueOrderBySortOrderAscNameAsc(businessTypeCode)
            : moduleDefinitionRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    return rows.stream().map(this::toModule).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listFeatures(String moduleCode) {
    List<FeatureDefinitionEntity> rows =
        StringUtils.hasText(moduleCode)
            ? featureDefinitionRepository.findByModuleCodeAndActiveTrueOrderBySortOrderAscNameAsc(
                moduleCode)
            : featureDefinitionRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    return rows.stream().map(this::toFeature).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listLimits(String businessTypeCode) {
    List<LimitDefinitionEntity> rows =
        StringUtils.hasText(businessTypeCode)
            ? limitDefinitionRepository
                .findByBusinessTypeCodeAndActiveTrueOrderBySortOrderAscNameAsc(businessTypeCode)
            : limitDefinitionRepository.findByActiveTrueOrderBySortOrderAscNameAsc();
    return rows.stream().map(this::toLimit).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> upsertBusinessType(Map<String, Object> body) {
    String code = requireCode(body, "code");
    boolean creating = !businessTypeRepository.existsById(code);
    BusinessTypeEntity e =
        businessTypeRepository.findById(code).orElseGet(BusinessTypeEntity::new);
    e.setCode(code);
    e.setName(requireText(body, "name"));
    e.setDescription(optionalText(body, "description"));
    e.setSortOrder(optionalInt(body, "sortOrder", e.getSortOrder()));
    if (body.containsKey("active") && body.get("active") != null) {
      e.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
    } else if (creating) {
      e.setActive(true);
    }
    if (creating) {
      e.setCreatedAt(java.time.Instant.now());
    }
    return toBusinessType(businessTypeRepository.save(e));
  }

  @Transactional
  public Map<String, Object> upsertModule(Map<String, Object> body) {
    String code = requireCode(body, "code");
    boolean creating = !moduleDefinitionRepository.existsById(code);
    ModuleDefinitionEntity e =
        moduleDefinitionRepository.findById(code).orElseGet(ModuleDefinitionEntity::new);
    e.setCode(code);
    e.setBusinessTypeCode(optionalText(body, "businessTypeCode"));
    e.setName(requireText(body, "name"));
    e.setDescription(optionalText(body, "description"));
    e.setSortOrder(optionalInt(body, "sortOrder", e.getSortOrder()));
    if (body.containsKey("active") && body.get("active") != null) {
      e.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
    } else if (creating) {
      e.setActive(true);
    }
    if (creating) {
      e.setCreatedAt(java.time.Instant.now());
    }
    return toModule(moduleDefinitionRepository.save(e));
  }

  @Transactional
  public Map<String, Object> upsertFeature(Map<String, Object> body) {
    String code = requireCode(body, "code");
    boolean creating = !featureDefinitionRepository.existsById(code);
    FeatureDefinitionEntity e =
        featureDefinitionRepository.findById(code).orElseGet(FeatureDefinitionEntity::new);
    e.setCode(code);
    e.setModuleCode(requireCode(body, "moduleCode"));
    e.setName(requireText(body, "name"));
    e.setDescription(optionalText(body, "description"));
    e.setSortOrder(optionalInt(body, "sortOrder", e.getSortOrder()));
    if (body.containsKey("active") && body.get("active") != null) {
      e.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
    } else if (creating) {
      e.setActive(true);
    }
    if (creating) {
      e.setCreatedAt(java.time.Instant.now());
    }
    return toFeature(featureDefinitionRepository.save(e));
  }

  @Transactional
  public Map<String, Object> upsertLimit(Map<String, Object> body) {
    String code = requireCode(body, "code");
    boolean creating = !limitDefinitionRepository.existsById(code);
    LimitDefinitionEntity e =
        limitDefinitionRepository.findById(code).orElseGet(LimitDefinitionEntity::new);
    e.setCode(code);
    e.setBusinessTypeCode(optionalText(body, "businessTypeCode"));
    e.setName(requireText(body, "name"));
    e.setUnit(
        body.get("unit") != null
            ? String.valueOf(body.get("unit"))
            : (creating ? "COUNT" : e.getUnit()));
    e.setAggregation(
        body.get("aggregation") != null
            ? String.valueOf(body.get("aggregation"))
            : (creating ? "NUMERIC" : e.getAggregation()));
    e.setDescription(optionalText(body, "description"));
    e.setSortOrder(optionalInt(body, "sortOrder", e.getSortOrder()));
    if (body.containsKey("active") && body.get("active") != null) {
      e.setActive(Boolean.parseBoolean(String.valueOf(body.get("active"))));
    } else if (creating) {
      e.setActive(true);
    }
    if (creating) {
      e.setCreatedAt(java.time.Instant.now());
    }
    return toLimit(limitDefinitionRepository.save(e));
  }

  private Map<String, Object> toBusinessType(BusinessTypeEntity e) {
    return CatalogViews.businessType(e.getCode(), e.getName(), e.getDescription(), e.getSortOrder());
  }

  private Map<String, Object> toModule(ModuleDefinitionEntity e) {
    return CatalogViews.module(
        e.getCode(), e.getBusinessTypeCode(), e.getName(), e.getDescription(), e.getSortOrder());
  }

  private Map<String, Object> toFeature(FeatureDefinitionEntity e) {
    return CatalogViews.feature(
        e.getCode(), e.getModuleCode(), e.getName(), e.getDescription(), e.getSortOrder());
  }

  private Map<String, Object> toLimit(LimitDefinitionEntity e) {
    return CatalogViews.limit(
        e.getCode(),
        e.getBusinessTypeCode(),
        e.getName(),
        e.getUnit(),
        e.getAggregation(),
        e.getDescription(),
        e.getSortOrder());
  }

  private static String requireCode(Map<String, Object> body, String key) {
    String v = optionalText(body, key);
    if (!StringUtils.hasText(v)) {
      throw new IllegalArgumentException(key + " is required");
    }
    return v.trim();
  }

  private static String requireText(Map<String, Object> body, String key) {
    return requireCode(body, key);
  }

  private static String optionalText(Map<String, Object> body, String key) {
    if (body == null || body.get(key) == null) {
      return null;
    }
    String v = String.valueOf(body.get(key)).trim();
    return v.isEmpty() || "null".equalsIgnoreCase(v) ? null : v;
  }

  private static int optionalInt(Map<String, Object> body, String key, int fallback) {
    if (body == null || body.get(key) == null) {
      return fallback;
    }
    return Integer.parseInt(String.valueOf(body.get(key)));
  }
}
