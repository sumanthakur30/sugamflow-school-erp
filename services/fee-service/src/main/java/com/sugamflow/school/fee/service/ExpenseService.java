package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.persistence.entity.ExpenseRecordEntity;
import com.sugamflow.school.fee.persistence.repo.ExpenseRecordRepository;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpenseService {

  public static final List<String> DEFAULT_CATEGORIES =
      List.of(
          "Staff Salary",
          "PF / ESI",
          "Electricity",
          "Water",
          "Internet",
          "Building Rent",
          "Maintenance",
          "Furniture",
          "Computer Purchase",
          "Books Purchase",
          "Lab Equipment",
          "Transport Fuel",
          "Vehicle Maintenance",
          "Marketing",
          "Office Expenses",
          "Stationery",
          "Miscellaneous");

  private final ExpenseRecordRepository repository;

  public ExpenseService(ExpenseRecordRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    requireFinanceAccess(scope);
    PageQuery q = PageQuery.of(page, size);
    Page<ExpenseRecordEntity> result;
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      result =
          repository
              .findByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusNotOrderByExpenseDateDescCreatedAtDesc(
                  scope.organizationId(),
                  scope.branchId(),
                  scope.academicSessionId(),
                  "VOID",
                  PageRequest.of(q.page(), q.size()));
    } else {
      result =
          repository.findByOrganizationIdAndStatusNotOrderByExpenseDateDescCreatedAtDesc(
              scope.organizationId(), "VOID", PageRequest.of(q.page(), q.size()));
    }
    return PageResult.of(
        result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFinanceAccess(scope);
    return toDto(require(id, scope.organizationId()));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFinanceWrite(scope);
    ExpenseRecordEntity e = new ExpenseRecordEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(stringOr(body.get("branchId"), scope.branchId()));
    e.setAcademicSessionId(stringOr(body.get("academicSessionId"), scope.academicSessionId()));
    apply(e, body);
    e.setCreatedBy(scope.userId());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    if (e.getVoucherNo() == null || e.getVoucherNo().isBlank()) {
      e.setVoucherNo("EXP-" + e.getId().toString().substring(0, 8).toUpperCase());
    }
    return toDto(repository.save(e));
  }

  @Transactional
  public Map<String, Object> update(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFinanceWrite(scope);
    ExpenseRecordEntity e = require(id, scope.organizationId());
    apply(e, body);
    e.setUpdatedAt(Instant.now());
    return toDto(repository.save(e));
  }

  @Transactional
  public Map<String, Object> voidExpense(UUID id) {
    TenantScope scope = TenantContext.require();
    requireFinanceWrite(scope);
    ExpenseRecordEntity e = require(id, scope.organizationId());
    e.setStatus("VOID");
    e.setUpdatedAt(Instant.now());
    return toDto(repository.save(e));
  }

  @Transactional
  public void seedDemoIfEmpty(TenantScope scope) {
    if (repository.countByOrganizationId(scope.organizationId()) > 0) {
      return;
    }
    LocalDate today = LocalDate.now();
    String branch = scope.branchId() == null || scope.branchId().isBlank() ? "main" : scope.branchId();
    String session =
        scope.academicSessionId() == null || scope.academicSessionId().isBlank()
            ? "2025-26"
            : scope.academicSessionId();
    Object[][] samples = {
      {"Electricity", "Monthly electricity bill", 9500, "BANK", -5},
      {"Water", "Water utility", 2200, "UPI", -8},
      {"Internet", "Campus internet lease", 3500, "BANK", -10},
      {"Building Rent", "Campus building rent", 25000, "CHEQUE", -12},
      {"Maintenance", "Classroom maintenance", 8500, "CASH", -15},
      {"Furniture", "New classroom desks", 4000, "BANK", -20},
      {"Computer Purchase", "Lab computers", 16000, "NEFT", -25},
      {"Books Purchase", "Library books", 12000, "BANK", -30},
      {"Lab Equipment", "Science lab kits", 9000, "BANK", -35},
      {"Transport Fuel", "Bus diesel", 9500, "CASH", -3},
      {"Vehicle Maintenance", "Bus servicing", 4500, "UPI", -18},
      {"Marketing", "Admission campaign", 5500, "UPI", -22},
      {"Office Expenses", "Admin office supplies", 4200, "CASH", -7},
      {"Stationery", "Exam stationery", 3000, "CASH", -14},
      {"Miscellaneous", "Misc campus expense", 6500, "CASH", -9},
      {"PF / ESI", "Statutory contributions", 18000, "NEFT", -2},
      {"Electricity", "Previous month electricity", 8800, "BANK", -40},
      {"Transport Fuel", "Previous month fuel", 8200, "CASH", -45},
      {"Building Rent", "Previous month rent", 25000, "CHEQUE", -50},
      {"Marketing", "Open house ads", 3800, "UPI", -55},
    };
    List<ExpenseRecordEntity> batch = new ArrayList<>();
    int i = 1;
    for (Object[] row : samples) {
      ExpenseRecordEntity e = new ExpenseRecordEntity();
      e.setId(UUID.randomUUID());
      e.setOrganizationId(scope.organizationId());
      e.setBranchId(branch);
      e.setAcademicSessionId(session);
      e.setCategory(String.valueOf(row[0]));
      e.setDescription(String.valueOf(row[1]));
      e.setAmount(BigDecimal.valueOf(((Number) row[2]).doubleValue()));
      e.setPaymentMode(String.valueOf(row[3]));
      e.setExpenseDate(today.plusDays(((Number) row[4]).longValue()));
      e.setStatus("POSTED");
      e.setVoucherNo("EXP-DEMO-" + String.format("%03d", i++));
      e.setCreatedBy(scope.userId() == null ? "system" : scope.userId());
      e.setCreatedAt(Instant.now());
      e.setUpdatedAt(Instant.now());
      batch.add(e);
    }
    repository.saveAll(batch);
  }

  public List<String> categories() {
    return DEFAULT_CATEGORIES;
  }

  private void apply(ExpenseRecordEntity e, Map<String, Object> body) {
    if (body.containsKey("voucherNo")) {
      e.setVoucherNo(stringOr(body.get("voucherNo"), e.getVoucherNo()));
    }
    String dateRaw = stringOr(body.get("expenseDate"), null);
    if (dateRaw != null && !dateRaw.isBlank()) {
      e.setExpenseDate(LocalDate.parse(dateRaw.substring(0, Math.min(10, dateRaw.length()))));
    } else if (e.getExpenseDate() == null) {
      e.setExpenseDate(LocalDate.now());
    }
    e.setCategory(stringOr(body.get("category"), e.getCategory()));
    if (e.getCategory() == null || e.getCategory().isBlank()) {
      throw new FeeException("INVALID_EXPENSE", "Expense category is required");
    }
    e.setDescription(stringOr(body.get("description"), e.getDescription() == null ? "" : e.getDescription()));
    Object amountRaw = body.get("amount");
    if (amountRaw != null) {
      BigDecimal amount = new BigDecimal(String.valueOf(amountRaw));
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new FeeException("INVALID_EXPENSE", "Expense amount must be greater than zero");
      }
      e.setAmount(amount);
    }
    if (e.getAmount() == null) {
      throw new FeeException("INVALID_EXPENSE", "Expense amount is required");
    }
    e.setPaymentMode(stringOr(body.get("paymentMode"), e.getPaymentMode() == null ? "CASH" : e.getPaymentMode()));
    e.setStatus(stringOr(body.get("status"), e.getStatus() == null ? "POSTED" : e.getStatus()));
  }

  private ExpenseRecordEntity require(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new FeeException("NOT_FOUND", "Expense not found"));
  }

  private Map<String, Object> toDto(ExpenseRecordEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("organizationId", e.getOrganizationId());
    m.put("branchId", e.getBranchId());
    m.put("academicSessionId", e.getAcademicSessionId());
    m.put("voucherNo", e.getVoucherNo());
    m.put("expenseDate", e.getExpenseDate() == null ? null : e.getExpenseDate().toString());
    m.put("date", e.getExpenseDate() == null ? null : e.getExpenseDate().toString());
    m.put("category", e.getCategory());
    m.put("description", e.getDescription());
    m.put("amount", e.getAmount());
    m.put("paymentMode", e.getPaymentMode());
    m.put("status", e.getStatus());
    m.put("createdBy", e.getCreatedBy());
    m.put("createdAt", e.getCreatedAt());
    m.put("updatedAt", e.getUpdatedAt());
    m.put("type", "EXPENSE");
    return m;
  }

  static void requireFinanceAccess(TenantScope scope) {
    String role = PersonaRoles.normalize(scope.roleCode());
    if (PersonaRoles.isPortalReadOnly(role) || PersonaRoles.isTeacher(role)) {
      throw new FeeException("FORBIDDEN", "Financial reports are not available for role: " + role);
    }
  }

  static void requireFinanceWrite(TenantScope scope) {
    requireFinanceAccess(scope);
    PersonaRoles.requireStaffWrite(scope);
    String role = PersonaRoles.normalize(scope.roleCode());
    if ("AUDITOR".equals(role)) {
      throw new FeeException("FORBIDDEN", "Auditor role is read-only");
    }
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) return fallback;
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }
}
