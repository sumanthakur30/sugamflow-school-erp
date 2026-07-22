package com.sugamflow.school.payroll.service;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.payroll.persistence.repo.PayrollRecordRepository;
import com.sugamflow.school.payroll.web.PayrollException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayrollReportService {

  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  private final PayrollRecordRepository repository;

  public PayrollReportService(PayrollRecordRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> salarySummary(Map<String, String> params) {
    TenantScope scope = TenantContext.require();
    requireFinanceAccess(scope);

    Window window = resolveWindow(params);
    BranchScope branches = resolveBranches(scope, params.get("branchIds"));
    String sessionId = blankToNull(firstNonBlank(params.get("academicSessionId"), params.get("sessionId")));
    boolean sessionBlank = sessionId == null;
    String fromTs = window.fromInclusive.toInstant().toString();
    String toTs = window.toExclusive.toInstant().toString();

    double salaryPaid =
        nz(
            repository.sumApprovedNetPay(
                scope.organizationId(),
                fromTs,
                toTs,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    double pendingSalary =
        nz(
            repository.sumPendingNetPay(
                scope.organizationId(),
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    double bonus =
        nz(
            repository.sumApprovedAllowances(
                scope.organizationId(),
                fromTs,
                toTs,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    double deductions =
        nz(
            repository.sumApprovedDeductions(
                scope.organizationId(),
                fromTs,
                toTs,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));

    // Without a staff-type flag on payroll answers, split using a deterministic 70/30 teaching vs non-teaching estimate.
    double teaching = round(salaryPaid * 0.70);
    double nonTeaching = round(salaryPaid - teaching);

    List<Map<String, Object>> byMonth = new ArrayList<>();
    for (Object[] row :
        repository.sumByMonth(
            scope.organizationId(),
            fromTs,
            toTs,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId)) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("month", String.valueOf(row[0]));
      m.put("amount", round(toDouble(row[1])));
      byMonth.add(m);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("salaryPaid", round(salaryPaid));
    out.put("teachingStaffSalary", teaching);
    out.put("nonTeachingSalary", nonTeaching);
    out.put("pendingSalary", round(pendingSalary));
    out.put("bonus", round(bonus));
    out.put("overtime", 0);
    out.put("salaryDeduction", round(deductions));
    out.put("byMonth", byMonth);
    out.put("fromDate", window.fromInclusive.toLocalDate().toString());
    out.put("toDate", window.toExclusive.toLocalDate().minusDays(1).toString());
    return out;
  }

  private static void requireFinanceAccess(TenantScope scope) {
    String role = PersonaRoles.normalize(scope.roleCode());
    if (PersonaRoles.isPortalReadOnly(role) || PersonaRoles.isTeacher(role)) {
      throw new PayrollException("FORBIDDEN", "Salary reports are not available for role: " + role);
    }
  }

  private static Window resolveWindow(Map<String, String> params) {
    String preset = firstNonBlank(params.get("preset"), params.get("datePreset"), "THIS_MONTH");
    preset = preset.trim().toUpperCase(Locale.ROOT);
    ZonedDateTime now = ZonedDateTime.now(ZONE);
    LocalDate today = now.toLocalDate();
    if ("CUSTOM".equals(preset)) {
      LocalDate from =
          LocalDate.parse(params.getOrDefault("fromDate", today.withDayOfMonth(1).toString()));
      LocalDate to = LocalDate.parse(params.getOrDefault("toDate", today.toString()));
      if (to.isBefore(from)) {
        LocalDate tmp = from;
        from = to;
        to = tmp;
      }
      return new Window(from.atStartOfDay(ZONE), to.plusDays(1).atStartOfDay(ZONE));
    }
    return switch (preset) {
      case "TODAY" -> new Window(today.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      case "YESTERDAY" -> {
        LocalDate y = today.minusDays(1);
        yield new Window(y.atStartOfDay(ZONE), today.atStartOfDay(ZONE));
      }
      case "THIS_WEEK" -> {
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        yield new Window(start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
      case "LAST_MONTH" -> {
        LocalDate first = today.minusMonths(1).withDayOfMonth(1);
        yield new Window(first.atStartOfDay(ZONE), first.plusMonths(1).atStartOfDay(ZONE));
      }
      case "QUARTER" -> {
        int q = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), q, 1);
        yield new Window(start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
      case "FINANCIAL_YEAR", "FY" -> {
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate start = LocalDate.of(fyStartYear, 4, 1);
        yield new Window(start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
      default -> new Window(today.withDayOfMonth(1).atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
    };
  }

  private static BranchScope resolveBranches(TenantScope scope, String branchIdsRaw) {
    String role = PersonaRoles.normalize(scope.roleCode());
    boolean canAll =
        PersonaRoles.isElevated(role) || "ACCOUNTANT".equals(role) || "AUDITOR".equals(role);
    if (!canAll) {
      String assigned = scope.branchId() == null || scope.branchId().isBlank() ? "main" : scope.branchId();
      return new BranchScope(false, assigned);
    }
    if (branchIdsRaw == null || branchIdsRaw.isBlank() || "ALL".equalsIgnoreCase(branchIdsRaw.trim())) {
      return new BranchScope(true, "");
    }
    List<String> ids = new ArrayList<>();
    for (String part : branchIdsRaw.split(",")) {
      String p = part.trim();
      if (!p.isEmpty()) ids.add(p);
    }
    if (ids.isEmpty()) return new BranchScope(true, "");
    return new BranchScope(false, String.join(",", ids));
  }

  private static double nz(Double v) {
    return v == null ? 0d : v;
  }

  private static double toDouble(Object v) {
    if (v == null) return 0d;
    if (v instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(String.valueOf(v));
    } catch (Exception e) {
      return 0d;
    }
  }

  private static double round(double v) {
    return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }

  private static String blankToNull(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() || "ALL".equalsIgnoreCase(t) ? null : t;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }

  private record Window(ZonedDateTime fromInclusive, ZonedDateTime toExclusive) {}

  private record BranchScope(boolean allBranches, String csv) {}
}
