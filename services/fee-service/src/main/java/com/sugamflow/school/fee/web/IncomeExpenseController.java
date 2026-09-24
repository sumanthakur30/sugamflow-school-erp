package com.sugamflow.school.fee.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.fee.service.IncomeExpenseReportService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fee/finance")
public class IncomeExpenseController {

  private final IncomeExpenseReportService reportService;

  public IncomeExpenseController(IncomeExpenseReportService reportService) {
    this.reportService = reportService;
  }

  @GetMapping("/income-expense")
  public ApiResponse<Map<String, Object>> incomeExpense(
      @RequestParam(required = false) String preset,
      @RequestParam(required = false) String datePreset,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) String branchIds,
      @RequestParam(required = false) String academicSessionId,
      @RequestParam(required = false) String sessionId,
      @RequestParam(required = false) String feeType,
      @RequestParam(required = false) String incomeSource,
      @RequestParam(required = false) String expenseCategory,
      @RequestParam(required = false) String paymentMode,
      @RequestParam(required = false) String collectedBy) {
    Map<String, String> params = new LinkedHashMap<>();
    if (preset != null) params.put("preset", preset);
    if (datePreset != null) params.put("datePreset", datePreset);
    if (fromDate != null) params.put("fromDate", fromDate);
    if (toDate != null) params.put("toDate", toDate);
    if (branchIds != null) params.put("branchIds", branchIds);
    if (academicSessionId != null) params.put("academicSessionId", academicSessionId);
    if (sessionId != null) params.put("sessionId", sessionId);
    if (feeType != null) params.put("feeType", feeType);
    if (incomeSource != null) params.put("incomeSource", incomeSource);
    if (expenseCategory != null) params.put("expenseCategory", expenseCategory);
    if (paymentMode != null) params.put("paymentMode", paymentMode);
    if (collectedBy != null) params.put("collectedBy", collectedBy);
    return ApiResponse.ok(reportService.report(params));
  }
}
