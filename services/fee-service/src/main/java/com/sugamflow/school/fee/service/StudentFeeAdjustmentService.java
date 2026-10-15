package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.finance.FeeConcessionCalculator;
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
    if (reason.length() < 3) {
      throw new FeeException("BAD_REQUEST", "A reason is required for a student fee adjustment");
    }
    String mode = text(body.get("mode")).toUpperCase(Locale.ROOT);
    if (!mode.equals("FIXED_OFF") && !mode.equals("PERCENT") && !mode.equals("CUSTOM_PAYABLE")) {
      throw new FeeException("BAD_REQUEST", "Mode must be FIXED_OFF, PERCENT, or CUSTOM_PAYABLE");
    }
    BigDecimal value = decimal(body.get("value"));
    if (value.compareTo(BigDecimal.ZERO) < 0) {
      throw new FeeException("BAD_REQUEST", "Adjustment value cannot be negative");
    }
    if ("PERCENT".equals(mode) && value.compareTo(BigDecimal.valueOf(100)) > 0) {
      throw new FeeException("BAD_REQUEST", "Percentage cannot exceed 100");
    }
    String session = scope.academicSessionId() == null ? "" : scope.academicSessionId();
    adjustments
        .findFirstByOrganizationIdAndAdmissionNoAndAcademicSessionIdAndStatusOrderByCreatedAtDesc(
            scope.organizationId(), admissionNo, session, "APPROVED")
        .ifPresent(previous -> change(previous, "INACTIVE", "SUPERSEDED", scope.userId(), "Replaced by a newer adjustment"));

    StudentFeeAdjustmentEntity row = new StudentFeeAdjustmentEntity();
    row.setId(UUID.randomUUID().toString());
    row.setOrganizationId(scope.organizationId());
    row.setBranchId(scope.branchId());
    row.setAcademicSessionId(session);
    row.setAdmissionNo(admissionNo);
    row.setMode(mode);
    row.setAdjustmentValue(value);
    row.setAppliesToHeads(FeeConcessionCalculator.heads(body.get("appliesToHeads")));
    row.setReason(reason);
    row.setEffectiveFrom(date(body.get("effectiveFrom")));
    row.setEffectiveTo(date(body.get("effectiveTo")));
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
      adjustments
          .findFirstByOrganizationIdAndAdmissionNoAndAcademicSessionIdAndStatusOrderByCreatedAtDesc(
              scope.organizationId(), row.getAdmissionNo(), row.getAcademicSessionId(), "APPROVED")
          .ifPresent(previous -> change(previous, "INACTIVE", "SUPERSEDED", scope.userId(), "Replaced by an approved adjustment"));
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
    if (admissionNo == null || admissionNo.isBlank()) {
      return null;
    }
    String session = scope.academicSessionId() == null ? "" : scope.academicSessionId();
    return adjustments
        .findFirstByOrganizationIdAndAdmissionNoAndAcademicSessionIdAndStatusOrderByCreatedAtDesc(
            scope.organizationId(), admissionNo.trim(), session, "APPROVED")
        .filter(this::inForce)
        .orElse(null);
  }

  private Map<String, Object> change(
      StudentFeeAdjustmentEntity row, String status, String action, String actor, String reason) {
    row.setStatus(status);
    row.setUpdatedAt(Instant.now());
    adjustments.save(row);
    audit(row, action, actor, reason);
    return toDto(row);
  }

  private boolean inForce(StudentFeeAdjustmentEntity row) {
    LocalDate today = LocalDate.now();
    if (row.getEffectiveFrom() != null && today.isBefore(row.getEffectiveFrom())) {
      return false;
    }
    return row.getEffectiveTo() == null || !today.isAfter(row.getEffectiveTo());
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
