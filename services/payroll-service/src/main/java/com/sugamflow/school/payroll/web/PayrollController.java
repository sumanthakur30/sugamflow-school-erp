package com.sugamflow.school.payroll.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.payroll.service.PayrollRecordService;
import com.sugamflow.school.payroll.service.PayrollReportService;
import com.sugamflow.school.payroll.service.PayslipPdfService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

  private final PayrollRecordService service;
  private final PayrollReportService reportService;
  private final PayslipPdfService payslipPdf;

  public PayrollController(
      PayrollRecordService service,
      PayrollReportService reportService,
      PayslipPdfService payslipPdf) {
    this.service = service;
    this.reportService = reportService;
    this.payslipPdf = payslipPdf;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/records")
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(service.list(page, size));
  }

  @GetMapping("/records/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @GetMapping(value = "/records/{id}/payslip.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> payslip(@PathVariable("id") UUID id) {
    Map<String, Object> answers = service.issuePayslip(id);
    byte[] bytes = payslipPdf.render(answers);
    String fileName =
        "payslip-"
            + String.valueOf(answers.getOrDefault("employeeId", id))
            + "-"
            + answers.getOrDefault("year", "")
            + "-"
            + answers.getOrDefault("month", "")
            + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes);
  }

  @PostMapping("/records")
  public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.submit(body));
  }

  @PutMapping("/records/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.update(id, body));
  }

  @PostMapping("/records/{id}/actions")
  public ApiResponse<Map<String, Object>> act(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.act(id, body));
  }

  @GetMapping("/reports/salary-summary")
  public ApiResponse<Map<String, Object>> salarySummary(
      @RequestParam(required = false) String preset,
      @RequestParam(required = false) String datePreset,
      @RequestParam(required = false) String fromDate,
      @RequestParam(required = false) String toDate,
      @RequestParam(required = false) String branchIds,
      @RequestParam(required = false) String academicSessionId,
      @RequestParam(required = false) String sessionId) {
    Map<String, String> params = new LinkedHashMap<>();
    if (preset != null) params.put("preset", preset);
    if (datePreset != null) params.put("datePreset", datePreset);
    if (fromDate != null) params.put("fromDate", fromDate);
    if (toDate != null) params.put("toDate", toDate);
    if (branchIds != null) params.put("branchIds", branchIds);
    if (academicSessionId != null) params.put("academicSessionId", academicSessionId);
    if (sessionId != null) params.put("sessionId", sessionId);
    return ApiResponse.ok(reportService.salarySummary(params));
  }
}
