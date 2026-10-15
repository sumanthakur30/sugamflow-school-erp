package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator;
import com.sugamflow.school.fee.finance.FeePeriodFilter;
import com.sugamflow.school.fee.finance.StudentDiscountRules;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.persistence.entity.StudentFeeAdjustmentAuditEntity;
import com.sugamflow.school.fee.persistence.entity.StudentFeeAdjustmentEntity;
import com.sugamflow.school.fee.persistence.repo.StudentFeeAdjustmentAuditRepository;
import com.sugamflow.school.fee.persistence.repo.StudentFeeAdjustmentRepository;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudentFeeAdjustmentService {

  private final StudentFeeAdjustmentRepository adjustments;
  private final StudentFeeAdjustmentAuditRepository audits;
  private final ConfigEngineClient engines;

  public StudentFeeAdjustmentService(
      StudentFeeAdjustmentRepository adjustments,
      StudentFeeAdjustmentAuditRepository audits,
      ConfigEngineClient engines) {
    this.adjustments = adjustments;
    this.audits = audits;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String admissionNo) {
    TenantScope scope = requireStaff();
    List<StudentFeeAdjustmentEntity> rows =
        admissionNo == null || admissionNo.isBlank()
            ? adjustments.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId())
            : adjustments.findByOrganizationIdAndAdmissionNoOrderByCreatedAtDesc(
                scope.organizationId(), admissionNo.trim());
    return rows.stream().limit(200).map(this::toDto).toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> report() {
    TenantScope scope = requireStaff();
    List<StudentFeeAdjustmentEntity> rows =
        adjustments.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId());
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("adjustments", rows.stream().limit(200).map(this::toDto).toList());
    out.put("approvedCount", rows.stream().filter(r -> "APPROVED".equals(r.getStatus())).count());
    out.put("pendingCount", rows.stream().filter(r -> "PENDING".equals(r.getStatus())).count());
    out.put(
        "approvedValue",
        rows.stream()
            .filter(r -> "APPROVED".equals(r.getStatus()))
            .map(StudentFeeAdjustmentEntity::getAdjustmentValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> history(String id) {
    TenantScope scope = requireStaff();
    return audits
        .findByOrganizationIdAndAdjustmentIdOrderByCreatedAtAsc(scope.organizationId(), id)
        .stream()
        .map(this::auditDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body, boolean approvalRequired) {
    TenantScope scope = requireStaff();
    String admissionNo = text(body.get("admissionNo"));
    String reason = text(body.get("reason"));
    if (admissionNo.isBlank()) {
      throw new FeeException("BAD_REQUEST", "Admission number is required");
    }
    String mode = text(body.get("mode")).toUpperCase(Locale.ROOT);
    if (!mode.equals("FIXED_OFF") && !mode.equals("PERCENT") && !mode.equals("CUSTOM_PAYABLE")) {
      throw new FeeException("BAD_REQUEST", "Mode must be FIXED_OFF, PERCENT, or CUSTOM_PAYABLE");
    }
    BigDecimal value = decimal(body.get("value"));
    LocalDate from = date(body.get("effectiveFrom"));
    LocalDate to = date(body.get("effectiveTo"));
    String problem = StudentDiscountRules.validate(mode, value, reason, from, to);
    if (problem != null) {
      throw new FeeException("BAD_REQUEST", problem);
    }
    List<String> heads = FeeConcessionCalculator.heads(body.get("appliesToHeads"));
    if (heads.isEmpty()) {
      throw new FeeException("BAD_REQUEST", "Select the fee head this discount applies to.");
    }
    String session = scope.academicSessionId() == null ? "" : scope.academicSessionId();
    String kind = kindOf(body.get("kind"));
    String periodKey = FeePeriodFilter.normalizePeriod(text(body.get("periodKey")));
    if ("PERIOD".equals(kind) && periodKey.isBlank()) {
      throw new FeeException("BAD_REQUEST", "A month or fee period is required for a one-period discount");
    }
    supersedeSameRule(scope, admissionNo, session, kind, periodKey, scope.userId());

    StudentFeeAdjustmentEntity row = new StudentFeeAdjustmentEntity();
    row.setId(UUID.randomUUID().toString());
    row.setOrganizationId(scope.organizationId());
    row.setBranchId(scope.branchId());
    row.setAcademicSessionId(session);
    row.setAdmissionNo(admissionNo);
    row.setMode(mode);
    row.setAdjustmentKind(kind);
    row.setPeriodKey("PERIOD".equals(kind) ? periodKey : null);
    row.setAdjustmentValue(value);
    row.setAppliesToHeads(heads);
    row.setReason(reason);
    row.setEffectiveFrom(from);
    row.setEffectiveTo(to);
    row.setCreatedBy(scope.userId());
    row.setCreatedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    if (approvalRequired) {
      row.setStatus("PENDING");
    } else {
      row.setStatus("APPROVED");
      row.setApprovedBy(scope.userId());
    }
    adjustments.save(row);
    audit(row, approvalRequired ? "CREATED" : "APPROVED", scope.userId(), reason);
    return toDto(row);
  }

  @Transactional
  public Map<String, Object> decide(String id, String status, String reason) {
    TenantScope scope = requireStaff();
    StudentFeeAdjustmentEntity row = require(scope, id);
    if (!"PENDING".equals(row.getStatus())) {
      throw new FeeException("BAD_REQUEST", "Only a pending adjustment can be approved or rejected");
    }
    if (scope.userId() != null && scope.userId().equals(row.getCreatedBy())) {
      throw new FeeException("FORBIDDEN", "The person who created the adjustment cannot approve it");
    }
    if (!"APPROVED".equals(status) && !"REJECTED".equals(status)) {
      throw new FeeException("BAD_REQUEST", "Decision must be APPROVED or REJECTED");
    }
    if ("APPROVED".equals(status)) {
      supersedeSameRule(
          scope,
          row.getAdmissionNo(),
          row.getAcademicSessionId(),
          kindOf(row.getAdjustmentKind()),
          row.getPeriodKey(),
          scope.userId());
      row.setApprovedBy(scope.userId());
    }
    row.setStatus(status);
    row.setUpdatedAt(Instant.now());
    adjustments.save(row);
    audit(row, status, scope.userId(), reason == null || reason.isBlank() ? row.getReason() : reason);
    return toDto(row);
  }

  @Transactional
  public Map<String, Object> remove(String id, String reason) {
    TenantScope scope = requireStaff();
    if (reason == null || reason.isBlank()) {
      throw new FeeException("BAD_REQUEST", "A reason is required to remove an adjustment");
    }
    StudentFeeAdjustmentEntity row = require(scope, id);
    if ("INACTIVE".equals(row.getStatus()) || "REJECTED".equals(row.getStatus())) {
      return toDto(row);
    }
    return change(row, "INACTIVE", "REMOVED", scope.userId(), reason);
  }

  @Transactional(readOnly = true)
  public StudentFeeAdjustmentEntity approvedFor(TenantScope scope, String admissionNo) {
    return approvedRecurring(scope, admissionNo, LocalDate.now());
  }

  @Transactional(readOnly = true)
  public StudentFeeAdjustmentEntity approvedRecurring(TenantScope scope, String admissionNo, LocalDate asOf) {
    return latest(scope, admissionNo, "RECURRING", null, asOf);
  }

  @Transactional(readOnly = true)
  public StudentFeeAdjustmentEntity approvedPeriod(TenantScope scope, String admissionNo, String periodKey) {
    LocalDate asOf = FeePeriodFilter.monthOf(periodKey) == null ? LocalDate.now() : FeePeriodFilter.monthOf(periodKey).atDay(1);
    return latest(scope, admissionNo, "PERIOD", periodKey, asOf);
  }

  private StudentFeeAdjustmentEntity latest(
      TenantScope scope, String admissionNo, String kind, String periodKey, LocalDate asOf) {
    if (scope == null || admissionNo == null || admissionNo.isBlank()) {
      return null;
    }
    String session = scope.academicSessionId() == null ? "" : scope.academicSessionId();
    for (StudentFeeAdjustmentEntity row :
        adjustments.findByOrganizationIdAndAdmissionNoOrderByCreatedAtDesc(
            scope.organizationId(), admissionNo.trim())) {
      if (!"APPROVED".equals(row.getStatus()) || !sameSession(row, session)) {
        continue;
      }
      if (!kindOf(row.getAdjustmentKind()).equals(kind)) {
        continue;
      }
      if ("PERIOD".equals(kind) && !FeePeriodFilter.samePeriod(row.getPeriodKey(), periodKey)) {
        continue;
      }
      if (FeePeriodFilter.covers(row.getEffectiveFrom(), row.getEffectiveTo(), asOf)) {
        return row;
      }
    }
    return null;
  }

  private void supersedeSameRule(
      TenantScope scope, String admissionNo, String session, String kind, String periodKey, String actor) {
    for (StudentFeeAdjustmentEntity previous :
        adjustments.findByOrganizationIdAndAdmissionNoOrderByCreatedAtDesc(scope.organizationId(), admissionNo)) {
      if (!"APPROVED".equals(previous.getStatus()) || !sameSession(previous, session)) {
        continue;
      }
      if (!kindOf(previous.getAdjustmentKind()).equals(kind)) {
        continue;
      }
      if ("PERIOD".equals(kind) && !FeePeriodFilter.samePeriod(previous.getPeriodKey(), periodKey)) {
        continue;
      }
      change(previous, "INACTIVE", "SUPERSEDED", actor, "Replaced by a newer " + kind.toLowerCase(Locale.ROOT) + " discount");
    }
  }

  private static boolean sameSession(StudentFeeAdjustmentEntity row, String session) {
    String stored = row.getAcademicSessionId() == null ? "" : row.getAcademicSessionId();
    return stored.equals(session == null ? "" : session);
  }

  private static String kindOf(Object raw) {
    String value = raw == null ? "" : String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
    return "PERIOD".equals(value) ? "PERIOD" : "RECURRING";
  }

  private Map<String, Object> change(
      StudentFeeAdjustmentEntity row, String status, String action, String actor, String reason) {
    row.setStatus(status);
    row.setUpdatedAt(Instant.now());
    adjustments.save(row);
    audit(row, action, actor, reason);
    return toDto(row);
  }

  private StudentFeeAdjustmentEntity require(TenantScope scope, String id) {
    StudentFeeAdjustmentEntity row =
        adjustments
            .findById(id)
            .orElseThrow(() -> new FeeException("NOT_FOUND", "Fee adjustment not found"));
    if (!scope.organizationId().equals(row.getOrganizationId())) {
      throw new FeeException("NOT_FOUND", "Fee adjustment not found");
    }
    return row;
  }

  private TenantScope requireStaff() {
    TenantScope scope = TenantContext.require();
    if (!engines.isFeatureEnabled(scope, FeeCollectionService.FEATURE_FEE)) {
      throw new FeeException("FEATURE_OFF", "FEATURE_FEE is off for this subscription plan.");
    }
    try {
      PersonaRoles.requireStaffWrite(scope);
    } catch (SecurityException ex) {
      throw new FeeException("FORBIDDEN", ex.getMessage());
    }
    return scope;
  }

  private void audit(StudentFeeAdjustmentEntity row, String action, String actor, String reason) {
    StudentFeeAdjustmentAuditEntity audit = new StudentFeeAdjustmentAuditEntity();
    audit.setId(UUID.randomUUID().toString());
    audit.setAdjustmentId(row.getId());
    audit.setOrganizationId(row.getOrganizationId());
    audit.setAction(action);
    audit.setActor(actor);
    audit.setReason(reason);
    audit.setSnapshot(toDto(row));
    audit.setCreatedAt(Instant.now());
    audits.save(audit);
  }

  private Map<String, Object> toDto(StudentFeeAdjustmentEntity row) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", row.getId());
    out.put("admissionNo", row.getAdmissionNo());
    out.put("academicSessionId", row.getAcademicSessionId());
    out.put("status", row.getStatus());
    out.put("mode", row.getMode());
    out.put("kind", kindOf(row.getAdjustmentKind()));
    out.put("periodKey", row.getPeriodKey());
    out.put("value", row.getAdjustmentValue());
    out.put("appliesToHeads", row.getAppliesToHeads() == null ? List.of() : row.getAppliesToHeads());
    out.put("reason", row.getReason());
    out.put("effectiveFrom", row.getEffectiveFrom() == null ? null : row.getEffectiveFrom().toString());
    out.put("effectiveTo", row.getEffectiveTo() == null ? null : row.getEffectiveTo().toString());
    out.put("createdBy", row.getCreatedBy());
    out.put("approvedBy", row.getApprovedBy());
    out.put("createdAt", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
    out.put("updatedAt", row.getUpdatedAt() == null ? null : row.getUpdatedAt().toString());
    return out;
  }

  private Map<String, Object> auditDto(StudentFeeAdjustmentAuditEntity row) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", row.getId());
    out.put("adjustmentId", row.getAdjustmentId());
    out.put("action", row.getAction());
    out.put("actor", row.getActor());
    out.put("reason", row.getReason());
    out.put("snapshot", row.getSnapshot());
    out.put("createdAt", row.getCreatedAt() == null ? null : row.getCreatedAt().toString());
    return out;
  }

  private static String text(Object raw) {
    return raw == null ? "" : String.valueOf(raw).trim();
  }

  private static BigDecimal decimal(Object raw) {
    if (raw instanceof BigDecimal decimal) return decimal;
    if (raw instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
    if (raw == null || String.valueOf(raw).isBlank()) return BigDecimal.ZERO;
    return new BigDecimal(String.valueOf(raw).trim());
  }

  private static LocalDate date(Object raw) {
    String value = text(raw);
    if (value.isBlank()) return null;
    return LocalDate.parse(value);
  }
}
