package com.sugamflow.school.compliance.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.dto.InfrastructureAssetRequest;
import com.sugamflow.school.compliance.dto.InfrastructureAssetResponse;
import com.sugamflow.school.compliance.dto.InfrastructureSummaryResponse;
import com.sugamflow.school.compliance.integration.ConfigEngineClient;
import com.sugamflow.school.compliance.persistence.entity.InfrastructureAssetEntity;
import com.sugamflow.school.compliance.persistence.repo.InfrastructureAssetRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class InfrastructureService {
  public static final List<String> RECOMMENDED_CATEGORIES =
      List.of(
          "CLASSROOM",
          "SCIENCE_LAB",
          "COMPUTER_LAB",
          "LIBRARY",
          "TOILET_BOYS",
          "TOILET_GIRLS",
          "DRINKING_WATER",
          "CCTV",
          "FIRE_SAFETY",
          "PLAYGROUND",
          "MEDICAL_ROOM");

  private final InfrastructureAssetRepository repository;
  private final ConfigEngineClient configEngineClient;

  public InfrastructureService(
      InfrastructureAssetRepository repository, ConfigEngineClient configEngineClient) {
    this.repository = repository;
    this.configEngineClient = configEngineClient;
  }

  @Transactional(readOnly = true)
  public List<InfrastructureAssetResponse> list(String category) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    List<InfrastructureAssetEntity> rows =
        category == null || category.isBlank()
            ? repository.findByOrganizationIdAndActiveTrueOrderByCategoryAscNameAsc(
                scope.organizationId())
            : repository.findByOrganizationIdAndCategoryAndActiveTrueOrderByNameAsc(
                scope.organizationId(), category.trim().toUpperCase(Locale.ROOT));
    return rows.stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public InfrastructureSummaryResponse summary() {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    List<InfrastructureAssetEntity> rows =
        repository.findByOrganizationIdAndActiveTrueOrderByCategoryAscNameAsc(
            scope.organizationId());
    Map<String, long[]> byCat = new LinkedHashMap<>();
    long totalQty = 0;
    for (InfrastructureAssetEntity row : rows) {
      long[] agg = byCat.computeIfAbsent(row.getCategory(), k -> new long[] {0, 0});
      agg[0] += 1;
      agg[1] += row.getQuantity();
      totalQty += row.getQuantity();
    }
    List<InfrastructureSummaryResponse.CategoryCount> counts = new ArrayList<>();
    for (Map.Entry<String, long[]> e : byCat.entrySet()) {
      counts.add(
          new InfrastructureSummaryResponse.CategoryCount(e.getKey(), e.getValue()[0], e.getValue()[1]));
    }
    Set<String> present = byCat.keySet();
    List<String> missing =
        RECOMMENDED_CATEGORIES.stream().filter(c -> !present.contains(c)).toList();
    return new InfrastructureSummaryResponse(rows.size(), totalQty, counts, missing);
  }

  @Transactional
  public InfrastructureAssetResponse create(InfrastructureAssetRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    InfrastructureAssetEntity entity = new InfrastructureAssetEntity();
    entity.setOrganizationId(scope.organizationId());
    apply(entity, request, scope);
    return toResponse(repository.save(entity));
  }

  @Transactional
  public InfrastructureAssetResponse update(Long id, InfrastructureAssetRequest request) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    InfrastructureAssetEntity entity = require(id, scope.organizationId());
    apply(entity, request, scope);
    return toResponse(repository.save(entity));
  }

  @Transactional
  public void delete(Long id) {
    TenantScope scope = TenantContext.require();
    requireFeature(scope);
    InfrastructureAssetEntity entity = require(id, scope.organizationId());
    entity.setActive(false);
    repository.save(entity);
  }

  private void apply(
      InfrastructureAssetEntity entity, InfrastructureAssetRequest request, TenantScope scope) {
    entity.setCategory(request.category().trim().toUpperCase(Locale.ROOT));
    entity.setName(request.name().trim());
    entity.setQuantity(request.quantity() == null ? 1 : Math.max(0, request.quantity()));
    entity.setCapacity(request.capacity());
    entity.setUnitLabel(trimToNull(request.unitLabel()));
    entity.setConditionCode(
        request.conditionCode() == null || request.conditionCode().isBlank()
            ? "GOOD"
            : request.conditionCode().trim().toUpperCase(Locale.ROOT));
    String branch =
        request.branchId() != null && !request.branchId().isBlank()
            ? request.branchId().trim()
            : scope.branchId();
    entity.setBranchId(trimToNull(branch));
    entity.setLocationNote(trimToNull(request.locationNote()));
    entity.setNotes(trimToNull(request.notes()));
    entity.setActive(true);
  }

  private InfrastructureAssetEntity require(Long id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .filter(InfrastructureAssetEntity::isActive)
        .orElseThrow(
            () ->
                new ComplianceException(
                    "NOT_FOUND", "Infrastructure asset not found", HttpStatus.NOT_FOUND));
  }

  private InfrastructureAssetResponse toResponse(InfrastructureAssetEntity e) {
    return new InfrastructureAssetResponse(
        e.getId(),
        e.getOrganizationId(),
        e.getBranchId(),
        e.getCategory(),
        e.getName(),
        e.getQuantity(),
        e.getCapacity(),
        e.getUnitLabel(),
        e.getConditionCode(),
        e.getLocationNote(),
        e.getNotes(),
        e.isActive(),
        e.getUpdatedAt());
  }

  private void requireFeature(TenantScope scope) {
    if (!configEngineClient.isFeatureEnabled(scope, ComplianceService.FEATURE_CBSE_COMPLIANCE)) {
      throw new ComplianceException(
          "FEATURE_DISABLED",
          "FEATURE_CBSE_COMPLIANCE is off for this subscription plan.",
          HttpStatus.FORBIDDEN);
    }
  }

  private static String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
