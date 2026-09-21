package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.integration.StudentAccessClient;
import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import com.sugamflow.school.fee.persistence.repo.FeeCollectionRepository;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeeClearanceService {

  private final FeeCollectionRepository repository;
  private final StudentAccessClient studentAccess;

  public FeeClearanceService(
      FeeCollectionRepository repository, StudentAccessClient studentAccess) {
    this.repository = repository;
    this.studentAccess = studentAccess;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(String admissionNo) {
    TenantScope scope = TenantContext.require();
    if (admissionNo == null || admissionNo.isBlank()) {
      throw new FeeException("VALIDATION", "admissionNo is required");
    }
    String ref = admissionNo.trim();
    AccessScope access = studentAccess.resolve(scope);
    if (access.restricted() && !access.allowsAdmissionNo(ref)) {
      throw new FeeException("FORBIDDEN", "Admission number is outside your access scope");
    }
    List<FeeCollectionEntity> records = scopedRecords(scope);
    BigDecimal pendingAmount = BigDecimal.ZERO;
    int pendingDays = 0;
    int openCount = 0;
    List<Map<String, Object>> openItems = new ArrayList<>();
    for (FeeCollectionEntity entity : records) {
      Map<String, Object> answers = entity.getAnswers() != null ? entity.getAnswers() : Map.of();
      if (!ref.equalsIgnoreCase(stringOr(answers.get("admissionNo"), ""))) {
        continue;
      }
      if ("APPROVED".equalsIgnoreCase(entity.getStatus())) {
        continue;
      }
      BigDecimal amount = toDecimal(answers.get("amount"));
      pendingAmount = pendingAmount.add(amount);
      pendingDays = Math.max(pendingDays, toInt(answers.get("pendingDays"), 0));
      openCount++;
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("collectionId", entity.getId().toString());
      item.put("status", entity.getStatus());
      item.put("amount", amount);
      item.put("pendingDays", toInt(answers.get("pendingDays"), 0));
      item.put("feeHead", stringOr(answers.get("feeHead"), ""));
      openItems.add(item);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("admissionNo", ref);
    out.put("pendingAmount", pendingAmount.setScale(2, RoundingMode.HALF_UP));
    out.put("pendingDays", pendingDays);
    out.put("openCollections", openCount);
    out.put("hasDues", pendingAmount.compareTo(BigDecimal.ZERO) > 0);
    out.put("items", openItems);
    return out;
  }

  private List<FeeCollectionEntity> scopedRecords(TenantScope scope) {
    if (scope.branchId() != null && !scope.branchId().isBlank()
        && scope.academicSessionId() != null && !scope.academicSessionId().isBlank()) {
      return repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }

  private static int toInt(Object value, int fallback) {
    if (value == null) {
      return fallback;
    }
    try {
      return (int) Math.round(Double.parseDouble(String.valueOf(value)));
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static BigDecimal toDecimal(Object value) {
    if (value == null) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(String.valueOf(value));
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }
}
