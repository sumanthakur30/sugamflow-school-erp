package com.sugamflow.school.fee.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.fee.service.FeeClearanceService;
import com.sugamflow.school.fee.service.FeeCollectionService;
import com.sugamflow.school.fee.service.FeeDueReminderService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fee")
public class FeeController {

  private final FeeCollectionService service;
  private final FeeClearanceService clearanceService;
  private final FeeDueReminderService dueReminders;

  public FeeController(
      FeeCollectionService service,
      FeeClearanceService clearanceService,
      FeeDueReminderService dueReminders) {
    this.service = service;
    this.clearanceService = clearanceService;
    this.dueReminders = dueReminders;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/collections")
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(service.list(page, size));
  }

  @GetMapping("/collections/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping("/collections")
  public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.submit(body));
  }

  @PostMapping("/collections/{id}/actions")
  public ApiResponse<Map<String, Object>> act(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.act(id, body));
  }

  @GetMapping("/collections/{id}/receipt")
  public ResponseEntity<byte[]> receipt(@PathVariable("id") UUID id) {
    byte[] pdf = service.getFeeReceiptPdf(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fee-receipt-" + id + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }

  @GetMapping("/clearance/{admissionNo}")
  public ApiResponse<Map<String, Object>> clearance(@PathVariable("admissionNo") String admissionNo) {
    return ApiResponse.ok(clearanceService.snapshot(admissionNo));
  }

  /** Alias for collection UIs: fee dues snapshot by admission number. */
  @GetMapping("/students/{admissionNo}/fee-summary")
  public ApiResponse<Map<String, Object>> feeSummary(
      @PathVariable("admissionNo") String admissionNo) {
    return ApiResponse.ok(clearanceService.snapshot(admissionNo));
  }

  /** Alias for pending/open fee items by admission number. */
  @GetMapping("/students/{admissionNo}/pending-fees")
  public ApiResponse<Map<String, Object>> pendingFees(
      @PathVariable("admissionNo") String admissionNo) {
    return ApiResponse.ok(clearanceService.snapshot(admissionNo));
  }

  @PostMapping("/due-reminders/run")
  public ApiResponse<Map<String, Object>> runDueReminders(
      @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(dueReminders.run(body == null ? Map.of() : body));
  }

  @GetMapping("/due-reminders/history")
  public ApiResponse<List<Map<String, Object>>> dueReminderHistory(
      @RequestParam(value = "admissionNo", required = false) String admissionNo) {
    return ApiResponse.ok(dueReminders.history(admissionNo));
  }
}
