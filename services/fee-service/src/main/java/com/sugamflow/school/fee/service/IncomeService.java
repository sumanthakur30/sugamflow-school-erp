package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.api.PageQuery;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.persistence.entity.IncomeRecordEntity;
import com.sugamflow.school.fee.persistence.repo.IncomeRecordRepository;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeService {

  public static final List<String> DEFAULT_SOURCES =
      List.of(
          "Donation",
          "Uniform Sale",
          "Books Sale",
          "Cafeteria Income",
          "Interest Income",
          "Event Income",
          "Rental Income",
          "Grant",
          "Other Income");

  private final IncomeRecordRepository repository;

  public IncomeService(IncomeRecordRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public PageResult<Map<String, Object>> list(Integer page, Integer size) {
    TenantScope scope = TenantContext.require();
    ExpenseService.requireFinanceAccess(scope);
    PageQuery q = PageQuery.of(page, size);
    Page<IncomeRecordEntity> result;
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      result =
          repository
              .findByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusNotOrderByIncomeDateDescCreatedAtDesc(
                  scope.organizationId(),
                  scope.branchId(),
                  scope.academicSessionId(),
                  "VOID",
                  PageRequest.of(q.page(), q.size()));
    } else {
      result =
          repository.findByOrganizationIdAndStatusNotOrderByIncomeDateDescCreatedAtDesc(
              scope.organizationId(), "VOID", PageRequest.of(q.page(), q.size()));
    }
    return PageResult.of(
        result.map(this::toDto).getContent(), q.page(), q.size(), result.getTotalElements());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> get(UUID id) {
    TenantScope scope = TenantContext.require();
    ExpenseService.requireFinanceAccess(scope);
    return toDto(require(id, scope.organizationId()));
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    ExpenseService.requireFinanceWrite(scope);
    IncomeRecordEntity e = new IncomeRecordEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(stringOr(body.get("branchId"), scope.branchId()));
    e.setAcademicSessionId(stringOr(body.get("academicSessionId"), scope.academicSessionId()));
    apply(e, body);
    e.setCreatedBy(scope.userId());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    if (e.getVoucherNo() == null || e.getVoucherNo().isBlank()) {
      e.setVoucherNo("INC-" + e.getId().toString().substring(0, 8).toUpperCase());
    }
    return toDto(repository.save(e));
  }

  @Transactional
  public Map<String, Object> update(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    ExpenseService.requireFinanceWrite(scope);
    IncomeRecordEntity e = require(id, scope.organizationId());
    apply(e, body);
    e.setUpdatedAt(Instant.now());
    return toDto(repository.save(e));
  }

  @Transactional
  public Map<String, Object> voidIncome(UUID id) {
    TenantScope scope = TenantContext.require();
    ExpenseService.requireFinanceWrite(scope);
    IncomeRecordEntity e = require(id, scope.organizationId());
    e.setStatus("VOID");
    e.setUpdatedAt(Instant.now());
    return toDto(repository.save(e));
  }

  public List<String> sources() {
    return DEFAULT_SOURCES;
  }

  private void apply(IncomeRecordEntity e, Map<String, Object> body) {
    if (body.containsKey("voucherNo")) {
      e.setVoucherNo(stringOr(body.get("voucherNo"), e.getVoucherNo()));
    }
    String dateRaw = stringOr(body.get("incomeDate"), null);
    if (dateRaw != null && !dateRaw.isBlank()) {
      e.setIncomeDate(LocalDate.parse(dateRaw.substring(0, Math.min(10, dateRaw.length()))));
    } else if (e.getIncomeDate() == null) {
      e.setIncomeDate(LocalDate.now());
    }
    e.setSource(stringOr(body.get("source"), e.getSource()));
    if (e.getSource() == null || e.getSource().isBlank()) {
      throw new FeeException("INVALID_INCOME", "Income source is required");
    }
    e.setDescription(
        stringOr(body.get("description"), e.getDescription() == null ? "" : e.getDescription()));
    Object amountRaw = body.get("amount");
    if (amountRaw != null) {
      BigDecimal amount = new BigDecimal(String.valueOf(amountRaw));
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        throw new FeeException("INVALID_INCOME", "Income amount must be greater than zero");
      }
      e.setAmount(amount);
    }
    if (e.getAmount() == null) {
      throw new FeeException("INVALID_INCOME", "Income amount is required");
    }
    e.setPaymentMode(
        stringOr(body.get("paymentMode"), e.getPaymentMode() == null ? "CASH" : e.getPaymentMode()));
    e.setStatus(stringOr(body.get("status"), e.getStatus() == null ? "POSTED" : e.getStatus()));
  }

  private IncomeRecordEntity require(UUID id, String org) {
    return repository
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new FeeException("NOT_FOUND", "Income entry not found"));
  }

  private Map<String, Object> toDto(IncomeRecordEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("organizationId", e.getOrganizationId());
    m.put("branchId", e.getBranchId());
    m.put("academicSessionId", e.getAcademicSessionId());
    m.put("voucherNo", e.getVoucherNo());
    m.put("incomeDate", e.getIncomeDate() == null ? null : e.getIncomeDate().toString());
    m.put("date", e.getIncomeDate() == null ? null : e.getIncomeDate().toString());
    m.put("source", e.getSource());
    m.put("category", e.getSource());
    m.put("description", e.getDescription());
    m.put("amount", e.getAmount());
    m.put("paymentMode", e.getPaymentMode());
    m.put("status", e.getStatus());
    m.put("createdBy", e.getCreatedBy());
    m.put("createdAt", e.getCreatedAt());
    m.put("updatedAt", e.getUpdatedAt());
    m.put("type", "INCOME");
    return m;
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) return fallback;
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }
}
