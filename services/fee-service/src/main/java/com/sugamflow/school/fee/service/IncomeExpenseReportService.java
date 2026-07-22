package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.persistence.entity.ExpenseRecordEntity;
import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import com.sugamflow.school.fee.persistence.entity.IncomeRecordEntity;
import com.sugamflow.school.fee.persistence.repo.ExpenseRecordRepository;
import com.sugamflow.school.fee.persistence.repo.FeeCollectionRepository;
import com.sugamflow.school.fee.persistence.repo.IncomeRecordRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IncomeExpenseReportService {

  private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

  private final FeeCollectionRepository feeRepo;
  private final ExpenseRecordRepository expenseRepo;
  private final ExpenseService expenseService;
  private final IncomeRecordRepository incomeRepo;
  private final IncomeService incomeService;

  public IncomeExpenseReportService(
      FeeCollectionRepository feeRepo,
      ExpenseRecordRepository expenseRepo,
      ExpenseService expenseService,
      IncomeRecordRepository incomeRepo,
      IncomeService incomeService) {
    this.feeRepo = feeRepo;
    this.expenseRepo = expenseRepo;
    this.expenseService = expenseService;
    this.incomeRepo = incomeRepo;
    this.incomeService = incomeService;
  }

  @Transactional
  public Map<String, Object> report(Map<String, String> params) {
    TenantScope scope = TenantContext.require();
    ExpenseService.requireFinanceAccess(scope);
    expenseService.seedDemoIfEmpty(scope);

    ReportWindow window = resolveWindow(params);
    BranchScope branches = resolveBranches(scope, params.get("branchIds"));
    String sessionId = blankToNull(firstNonBlank(params.get("academicSessionId"), params.get("sessionId")));
    boolean sessionBlank = sessionId == null;
    String feeType = blankToNull(params.get("feeType"));
    String expenseCategory = blankToNull(params.get("expenseCategory"));
    String incomeSource = blankToNull(params.get("incomeSource"));
    String paymentMode = blankToNull(params.get("paymentMode"));
    String collectedBy = blankToNull(params.get("collectedBy"));

    String fromTs = window.fromInclusive.toInstant().toString();
    String toTs = window.toExclusive.toInstant().toString();
    String fromDate = window.fromInclusive.toLocalDate().format(DAY);
    String toDate = window.toExclusive.toLocalDate().minusDays(1).format(DAY);

    double feeIncome =
        nz(
            feeRepo.sumApprovedAmount(
                scope.organizationId(),
                fromTs,
                toTs,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId,
                feeType == null,
                feeType == null ? "" : feeType,
                paymentMode == null,
                paymentMode == null ? "" : paymentMode,
                collectedBy == null,
                collectedBy == null ? "" : collectedBy));

    double otherIncome =
        nz(
            incomeRepo.sumAmount(
                scope.organizationId(),
                fromDate,
                toDate,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId,
                incomeSource == null,
                incomeSource == null ? "" : incomeSource,
                paymentMode == null,
                paymentMode == null ? "" : paymentMode));
    double totalIncome = feeIncome + otherIncome;

    double operatingExpense =
        nz(
            expenseRepo.sumAmount(
                scope.organizationId(),
                fromDate,
                toDate,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId,
                expenseCategory == null,
                expenseCategory == null ? "" : expenseCategory,
                paymentMode == null,
                paymentMode == null ? "" : paymentMode));

    double pendingFees =
        nz(
            feeRepo.sumPendingAmount(
                scope.organizationId(),
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    double overdueFees =
        nz(
            feeRepo.sumOverdueAmount(
                scope.organizationId(),
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));

    // Same-day / month / year snapshots for fee analysis (ignore custom fee type filter for these KPIs).
    ReportWindow todayW = preset("TODAY");
    ReportWindow monthW = preset("THIS_MONTH");
    ReportWindow yearW = preset("FINANCIAL_YEAR");
    double collectedToday =
        nz(
            feeRepo.sumApprovedAmount(
                scope.organizationId(),
                todayW.fromInclusive.toInstant().toString(),
                todayW.toExclusive.toInstant().toString(),
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId,
                true,
                "",
                true,
                "",
                true,
                ""));
    double collectedMonth =
        nz(
            feeRepo.sumApprovedAmount(
                scope.organizationId(),
                monthW.fromInclusive.toInstant().toString(),
                monthW.toExclusive.toInstant().toString(),
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId,
                true,
                "",
                true,
                "",
                true,
                ""));
    double collectedYear =
        nz(
            feeRepo.sumApprovedAmount(
                scope.organizationId(),
                yearW.fromInclusive.toInstant().toString(),
                yearW.toExclusive.toInstant().toString(),
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId,
                true,
                "",
                true,
                "",
                true,
                ""));

    List<Map<String, Object>> incomeBySource =
        rows(feeRepo.sumByFeeHead(
            scope.organizationId(),
            fromTs,
            toTs,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId));
    incomeBySource =
        mergeRows(
            incomeBySource,
            rows(
                incomeRepo.sumBySource(
                    scope.organizationId(),
                    fromDate,
                    toDate,
                    branches.allBranches,
                    branches.csv,
                    sessionBlank,
                    sessionId == null ? "" : sessionId)));
    List<Map<String, Object>> expenseByCategory =
        rows(expenseRepo.sumByCategory(
            scope.organizationId(),
            fromDate,
            toDate,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId));
    List<Map<String, Object>> incomeByPaymentMode =
        rows(feeRepo.sumByPaymentMode(
            scope.organizationId(),
            fromTs,
            toTs,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId));
    incomeByPaymentMode =
        mergeRows(
            incomeByPaymentMode,
            rows(
                incomeRepo.sumByPaymentMode(
                    scope.organizationId(),
                    fromDate,
                    toDate,
                    branches.allBranches,
                    branches.csv,
                    sessionBlank,
                    sessionId == null ? "" : sessionId)));
    List<Map<String, Object>> expenseByPaymentMode =
        rows(expenseRepo.sumByPaymentMode(
            scope.organizationId(),
            fromDate,
            toDate,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId));

    Map<String, Double> incomeMonth =
        toMonthMap(
            feeRepo.sumByMonth(
                scope.organizationId(),
                fromTs,
                toTs,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    mergeAmounts(
        incomeMonth,
        toMonthMap(
            incomeRepo.sumByMonth(
                scope.organizationId(),
                fromDate,
                toDate,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId)));
    Map<String, Double> expenseMonth =
        toMonthMap(
            expenseRepo.sumByMonth(
                scope.organizationId(),
                fromDate,
                toDate,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    List<Map<String, Object>> monthlyTrend = buildMonthlyTrend(window, incomeMonth, expenseMonth);

    Map<String, Double> incomeBranch =
        toKeyMap(
            feeRepo.sumByBranch(
                scope.organizationId(),
                fromTs,
                toTs,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    mergeAmounts(
        incomeBranch,
        toKeyMap(
            incomeRepo.sumByBranch(
                scope.organizationId(),
                fromDate,
                toDate,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId)));
    Map<String, Double> expenseBranch =
        toKeyMap(
            expenseRepo.sumByBranch(
                scope.organizationId(),
                fromDate,
                toDate,
                branches.allBranches,
                branches.csv,
                sessionBlank,
                sessionId == null ? "" : sessionId));
    List<Map<String, Object>> branchComparison = buildBranchComparison(incomeBranch, expenseBranch);

    List<Map<String, Object>> recent = new ArrayList<>();
    for (FeeCollectionEntity fee :
        feeRepo.recentApproved(
            scope.organizationId(),
            fromTs,
            toTs,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId,
            12)) {
      recent.add(feeTxn(fee));
    }
    for (IncomeRecordEntity income :
        incomeRepo.recent(
            scope.organizationId(),
            fromDate,
            toDate,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId,
            12)) {
      recent.add(incomeTxn(income));
    }
    for (ExpenseRecordEntity exp :
        expenseRepo.recent(
            scope.organizationId(),
            fromDate,
            toDate,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId,
            true,
            "",
            12)) {
      recent.add(expenseTxn(exp));
    }
    recent.sort(
        (a, b) ->
            String.valueOf(b.get("date")).compareTo(String.valueOf(a.get("date"))));
    if (recent.size() > 20) {
      recent = new ArrayList<>(recent.subList(0, 20));
    }

    List<Map<String, Object>> topExpenses = new ArrayList<>();
    for (ExpenseRecordEntity exp :
        expenseRepo.topByAmount(
            scope.organizationId(),
            fromDate,
            toDate,
            branches.allBranches,
            branches.csv,
            sessionBlank,
            sessionId == null ? "" : sessionId,
            true,
            "",
            8)) {
      topExpenses.add(expenseTxn(exp));
    }

    // Operating expenses only here; UI merges salary paid from payroll-service.
    double totalExpense = operatingExpense;
    double net = totalIncome - totalExpense;
    double profitPct =
        totalIncome <= 0
            ? 0
            : BigDecimal.valueOf(net * 100.0 / totalIncome)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();

    Map<String, Object> kpis = new LinkedHashMap<>();
    kpis.put("totalIncome", round(totalIncome));
    kpis.put("feeIncome", round(feeIncome));
    kpis.put("otherIncome", round(otherIncome));
    kpis.put("totalExpense", round(totalExpense));
    kpis.put("operatingExpense", round(operatingExpense));
    kpis.put("salaryPaid", 0);
    kpis.put("netProfit", round(net));
    kpis.put("profitPercent", profitPct);
    kpis.put("pendingFees", round(pendingFees));
    kpis.put("overdueFees", round(overdueFees));
    kpis.put("collectedToday", round(collectedToday));
    kpis.put("collectedThisMonth", round(collectedMonth));
    kpis.put("collectedThisYear", round(collectedYear));
    kpis.put("cashInHand", round(cashMode(incomeByPaymentMode) - cashMode(expenseByPaymentMode)));
    kpis.put(
        "bankBalance",
        round(
            bankishMode(incomeByPaymentMode)
                - bankishMode(expenseByPaymentMode)));

    Map<String, Object> feeAnalysis = new LinkedHashMap<>();
    feeAnalysis.put("collectedToday", round(collectedToday));
    feeAnalysis.put("collectedThisMonth", round(collectedMonth));
    feeAnalysis.put("collectedThisYear", round(collectedYear));
    feeAnalysis.put("pendingFees", round(pendingFees));
    feeAnalysis.put("overdueFees", round(overdueFees));
    feeAnalysis.put("advanceCollection", 0);
    feeAnalysis.put("scholarshipDiscount", 0);
    feeAnalysis.put("fineCollected", findAmount(incomeBySource, "Late", "Fine"));
    feeAnalysis.put("refundAmount", 0);

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("generatedAt", Instant.now().toString());
    out.put("organizationId", scope.organizationId());
    out.put("branchScope", branches.allBranches ? "ALL" : "SELECTED");
    out.put("branchIds", branches.ids);
    out.put("academicSessionId", sessionId);
    out.put("datePreset", window.preset);
    out.put("fromDate", fromDate);
    out.put("toDate", toDate);
    out.put("currency", "INR");
    out.put("kpis", kpis);
    out.put("incomeBySource", incomeBySource);
    out.put("expenseByCategory", expenseByCategory);
    out.put("incomeByPaymentMode", incomeByPaymentMode);
    out.put("expenseByPaymentMode", expenseByPaymentMode);
    out.put("monthlyTrend", monthlyTrend);
    out.put("branchComparison", branchComparison);
    out.put("feeAnalysis", feeAnalysis);
    out.put("topExpenses", topExpenses);
    out.put("recentTransactions", recent);
    out.put("expenseCategories", expenseService.categories());
    out.put("incomeSources", incomeService.sources());
    out.put(
        "canWrite",
        !"AUDITOR".equals(PersonaRoles.normalize(scope.roleCode()))
            && !PersonaRoles.isPortalReadOnly(scope.roleCode()));
    out.put(
        "canViewAllBranches",
        PersonaRoles.isElevated(scope.roleCode())
            || "ACCOUNTANT".equals(PersonaRoles.normalize(scope.roleCode())));
    return out;
  }

  private static ReportWindow resolveWindow(Map<String, String> params) {
    String preset = firstNonBlank(params.get("preset"), params.get("datePreset"), "THIS_MONTH");
    preset = preset.trim().toUpperCase(Locale.ROOT);
    if ("CUSTOM".equals(preset)) {
      LocalDate from = LocalDate.parse(params.getOrDefault("fromDate", LocalDate.now().withDayOfMonth(1).toString()));
      LocalDate to = LocalDate.parse(params.getOrDefault("toDate", LocalDate.now().toString()));
      if (to.isBefore(from)) {
        LocalDate tmp = from;
        from = to;
        to = tmp;
      }
      return new ReportWindow(
          "CUSTOM",
          from.atStartOfDay(ZONE),
          to.plusDays(1).atStartOfDay(ZONE));
    }
    return preset(preset);
  }

  private static ReportWindow preset(String presetRaw) {
    String preset = presetRaw == null ? "THIS_MONTH" : presetRaw.trim().toUpperCase(Locale.ROOT);
    ZonedDateTime now = ZonedDateTime.now(ZONE);
    LocalDate today = now.toLocalDate();
    return switch (preset) {
      case "TODAY" -> new ReportWindow("TODAY", today.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      case "YESTERDAY" -> {
        LocalDate y = today.minusDays(1);
        yield new ReportWindow("YESTERDAY", y.atStartOfDay(ZONE), today.atStartOfDay(ZONE));
      }
      case "THIS_WEEK" -> {
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        yield new ReportWindow("THIS_WEEK", start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
      case "LAST_MONTH" -> {
        LocalDate first = today.minusMonths(1).withDayOfMonth(1);
        LocalDate next = first.plusMonths(1);
        yield new ReportWindow("LAST_MONTH", first.atStartOfDay(ZONE), next.atStartOfDay(ZONE));
      }
      case "QUARTER" -> {
        int q = ((today.getMonthValue() - 1) / 3) * 3 + 1;
        LocalDate start = LocalDate.of(today.getYear(), q, 1);
        yield new ReportWindow("QUARTER", start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
      case "FINANCIAL_YEAR", "FY" -> {
        // Indian FY: 1 Apr – 31 Mar
        int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        LocalDate start = LocalDate.of(fyStartYear, 4, 1);
        yield new ReportWindow(
            "FINANCIAL_YEAR", start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
      default -> {
        LocalDate start = today.withDayOfMonth(1);
        yield new ReportWindow("THIS_MONTH", start.atStartOfDay(ZONE), today.plusDays(1).atStartOfDay(ZONE));
      }
    };
  }

  private static BranchScope resolveBranches(TenantScope scope, String branchIdsRaw) {
    String role = PersonaRoles.normalize(scope.roleCode());
    boolean canAll =
        PersonaRoles.isElevated(role) || "ACCOUNTANT".equals(role) || "AUDITOR".equals(role);
    if (!canAll) {
      String assigned = scope.branchId() == null || scope.branchId().isBlank() ? "main" : scope.branchId();
      return new BranchScope(false, List.of(assigned), assigned);
    }
    if (branchIdsRaw == null || branchIdsRaw.isBlank() || "ALL".equalsIgnoreCase(branchIdsRaw.trim())) {
      return new BranchScope(true, List.of(), "");
    }
    List<String> ids = new ArrayList<>();
    for (String part : branchIdsRaw.split(",")) {
      String p = part.trim();
      if (!p.isEmpty()) ids.add(p);
    }
    if (ids.isEmpty()) {
      return new BranchScope(true, List.of(), "");
    }
    return new BranchScope(false, ids, String.join(",", ids));
  }

  private static List<Map<String, Object>> rows(List<Object[]> raw) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object[] row : raw) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("key", String.valueOf(row[0] == null ? "Other" : row[0]));
      m.put("label", String.valueOf(row[0] == null ? "Other" : row[0]));
      m.put("amount", round(toDouble(row[1])));
      out.add(m);
    }
    return out;
  }

  private static List<Map<String, Object>> mergeRows(
      List<Map<String, Object>> first, List<Map<String, Object>> second) {
    Map<String, Double> totals = new LinkedHashMap<>();
    Map<String, String> labels = new LinkedHashMap<>();
    for (Map<String, Object> row : first) {
      String key = String.valueOf(row.get("key"));
      labels.put(key, String.valueOf(row.getOrDefault("label", key)));
      totals.merge(key, toDouble(row.get("amount")), Double::sum);
    }
    for (Map<String, Object> row : second) {
      String key = String.valueOf(row.get("key"));
      labels.putIfAbsent(key, String.valueOf(row.getOrDefault("label", key)));
      totals.merge(key, toDouble(row.get("amount")), Double::sum);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, Double> entry : totals.entrySet()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("key", entry.getKey());
      row.put("label", labels.get(entry.getKey()));
      row.put("amount", round(entry.getValue()));
      out.add(row);
    }
    out.sort((a, b) -> Double.compare(toDouble(b.get("amount")), toDouble(a.get("amount"))));
    return out;
  }

  private static void mergeAmounts(Map<String, Double> target, Map<String, Double> additions) {
    for (Map.Entry<String, Double> entry : additions.entrySet()) {
      target.merge(entry.getKey(), entry.getValue(), Double::sum);
    }
  }

  private static Map<String, Double> toMonthMap(List<Object[]> raw) {
    Map<String, Double> map = new LinkedHashMap<>();
    for (Object[] row : raw) {
      map.put(String.valueOf(row[0]), toDouble(row[1]));
    }
    return map;
  }

  private static Map<String, Double> toKeyMap(List<Object[]> raw) {
    return toMonthMap(raw);
  }

  private static List<Map<String, Object>> buildMonthlyTrend(
      ReportWindow window, Map<String, Double> income, Map<String, Double> expense) {
    List<Map<String, Object>> out = new ArrayList<>();
    LocalDate cursor = window.fromInclusive.toLocalDate().withDayOfMonth(1);
    LocalDate end = window.toExclusive.toLocalDate().minusDays(1).withDayOfMonth(1);
    if (end.isBefore(cursor)) end = cursor;
    // Cap at 18 months for chart readability.
    int guard = 0;
    while (!cursor.isAfter(end) && guard++ < 18) {
      String key = cursor.format(DateTimeFormatter.ofPattern("yyyy-MM"));
      double inc = income.getOrDefault(key, 0d);
      double exp = expense.getOrDefault(key, 0d);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("month", key);
      m.put("label", cursor.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)));
      m.put("income", round(inc));
      m.put("expense", round(exp));
      m.put("net", round(inc - exp));
      out.add(m);
      cursor = cursor.plusMonths(1);
    }
    return out;
  }

  private static List<Map<String, Object>> buildBranchComparison(
      Map<String, Double> income, Map<String, Double> expense) {
    Set<String> keys = new LinkedHashSet<>();
    keys.addAll(income.keySet());
    keys.addAll(expense.keySet());
    List<Map<String, Object>> out = new ArrayList<>();
    for (String key : keys) {
      double inc = income.getOrDefault(key, 0d);
      double exp = expense.getOrDefault(key, 0d);
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("branchId", key);
      m.put("branchName", prettyBranch(key));
      m.put("income", round(inc));
      m.put("expense", round(exp));
      m.put("profit", round(inc - exp));
      out.add(m);
    }
    out.sort((a, b) -> Double.compare(toDouble(b.get("profit")), toDouble(a.get("profit"))));
    return out;
  }

  private static String prettyBranch(String key) {
    if (key == null || key.isBlank()) return "Main";
    if ("main".equalsIgnoreCase(key)) return "Main Campus";
    String[] parts = key.replace('_', ' ').replace('-', ' ').split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p.isEmpty()) continue;
      if (!sb.isEmpty()) sb.append(' ');
      sb.append(Character.toUpperCase(p.charAt(0)));
      if (p.length() > 1) sb.append(p.substring(1));
    }
    return sb.append(" Branch").toString();
  }

  private static Map<String, Object> feeTxn(FeeCollectionEntity e) {
    Map<String, Object> answers = e.getAnswers() == null ? Map.of() : e.getAnswers();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("type", "INCOME");
    m.put("date", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    m.put("voucherNo", "FEE-" + e.getId().toString().substring(0, 8).toUpperCase());
    m.put(
        "description",
        String.valueOf(answers.getOrDefault("studentName", "Fee collection"))
            + " · "
            + String.valueOf(answers.getOrDefault("feeHead", "Fee")));
    m.put("category", String.valueOf(answers.getOrDefault("feeHead", "Fee")));
    m.put("amount", toDouble(answers.get("amount")));
    m.put("paymentMode", String.valueOf(answers.getOrDefault("paymentMode", "")));
    m.put("createdBy", e.getCreatedBy());
    m.put("branchId", e.getBranchId());
    m.put("drillPath", "/admin/fee?id=" + e.getId());
    return m;
  }

  private static Map<String, Object> incomeTxn(IncomeRecordEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("type", "INCOME");
    m.put("date", e.getIncomeDate() == null ? null : e.getIncomeDate().toString());
    m.put("voucherNo", e.getVoucherNo());
    m.put("description", e.getDescription());
    m.put("category", e.getSource());
    m.put("amount", e.getAmount() == null ? 0 : e.getAmount().doubleValue());
    m.put("paymentMode", e.getPaymentMode());
    m.put("createdBy", e.getCreatedBy());
    m.put("branchId", e.getBranchId());
    m.put("drillPath", "/admin/income-expense?tab=income&incomeId=" + e.getId());
    return m;
  }

  private static Map<String, Object> expenseTxn(ExpenseRecordEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("type", "EXPENSE");
    m.put("date", e.getExpenseDate() == null ? null : e.getExpenseDate().toString());
    m.put("voucherNo", e.getVoucherNo());
    m.put("description", e.getDescription());
    m.put("category", e.getCategory());
    m.put("amount", e.getAmount() == null ? 0 : e.getAmount().doubleValue());
    m.put("paymentMode", e.getPaymentMode());
    m.put("createdBy", e.getCreatedBy());
    m.put("branchId", e.getBranchId());
    m.put("drillPath", "/admin/income-expense?expenseId=" + e.getId());
    return m;
  }

  private static double cashMode(List<Map<String, Object>> modes) {
    return findAmount(modes, "CASH", "Cash");
  }

  private static double bankishMode(List<Map<String, Object>> modes) {
    return findAmount(modes, "BANK", "NEFT", "RTGS", "UPI", "CARD", "ONLINE", "CHEQUE", "Bank");
  }

  private static double findAmount(List<Map<String, Object>> rows, String... needles) {
    double sum = 0;
    for (Map<String, Object> row : rows) {
      String label = String.valueOf(row.getOrDefault("label", row.get("key"))).toUpperCase(Locale.ROOT);
      for (String n : needles) {
        if (label.contains(n.toUpperCase(Locale.ROOT))) {
          sum += toDouble(row.get("amount"));
          break;
        }
      }
    }
    return sum;
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

  private record ReportWindow(String preset, ZonedDateTime fromInclusive, ZonedDateTime toExclusive) {}

  private record BranchScope(boolean allBranches, List<String> ids, String csv) {}
}
