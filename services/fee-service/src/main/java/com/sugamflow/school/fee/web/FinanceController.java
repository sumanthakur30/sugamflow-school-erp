package com.sugamflow.school.fee.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.fee.finance.FinanceCatalog;
import com.sugamflow.school.fee.service.FinanceService;
import com.sugamflow.school.fee.service.StudentFeeAdjustmentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fee/finance")
public class FinanceController {

  private final FinanceService service;
  private final StudentFeeAdjustmentService adjustments;

  public FinanceController(FinanceService service, StudentFeeAdjustmentService adjustments) {
    this.service = service;
    this.adjustments = adjustments;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(wrap(service::bootstrap));
  }

  @GetMapping("/heads")
  public ApiResponse<List<Map<String, Object>>> heads() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(FinanceCatalog.TYPE_FEE_HEAD)));
  }

  @PutMapping("/heads/{key}")
  public ApiResponse<Map<String, Object>> saveHead(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(wrap(() -> service.saveDefinition(FinanceCatalog.TYPE_FEE_HEAD, body)));
  }

  @GetMapping("/structures")
  public ApiResponse<List<Map<String, Object>>> structures() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(FinanceCatalog.TYPE_FEE_STRUCTURE)));
  }

  @PutMapping("/structures/{key}")
  public ApiResponse<Map<String, Object>> saveStructure(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(FinanceCatalog.TYPE_FEE_STRUCTURE, body)));
  }

  @GetMapping("/concessions")
  public ApiResponse<List<Map<String, Object>>> concessions() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(FinanceCatalog.TYPE_CONCESSION)));
  }

  @PutMapping("/concessions/{key}")
  public ApiResponse<Map<String, Object>> saveConcession(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(wrap(() -> service.saveDefinition(FinanceCatalog.TYPE_CONCESSION, body)));
  }

  @PostMapping("/demands/preview")
  public ApiResponse<Map<String, Object>> previewDemand(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.previewDemand(body)));
  }

  @PostMapping("/demands")
  public ApiResponse<Map<String, Object>> generateDemand(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.generateDemand(body)));
  }

  @PostMapping("/demands/bulk")
  public ApiResponse<Map<String, Object>> generateBulkDemands(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.generateBulkDemands(body)));
  }

  @PostMapping("/waive")
  public ApiResponse<Map<String, Object>> waive(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.waive(body)));
  }

  @PostMapping("/refund")
  public ApiResponse<Map<String, Object>> refund(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.refund(body)));
  }

  @GetMapping("/late-fee-policies")
  public ApiResponse<List<Map<String, Object>>> lateFeePolicies() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(FinanceCatalog.TYPE_LATE_FEE_POLICY)));
  }

  @PutMapping("/late-fee-policies/{key}")
  public ApiResponse<Map<String, Object>> saveLateFeePolicy(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(FinanceCatalog.TYPE_LATE_FEE_POLICY, body)));
  }

  @PostMapping("/payments/intents")
  public ApiResponse<Map<String, Object>> createIntent(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.createPaymentIntent(body)));
  }

  @PostMapping("/payments/intents/{id}/simulate-capture")
  public ApiResponse<Map<String, Object>> simulateCapture(@PathVariable("id") UUID id) {
    return ApiResponse.ok(wrap(() -> service.simulateCapture(id)));
  }

  @PostMapping("/payments/intents/{id}/confirm")
  public ApiResponse<Map<String, Object>> confirmCapture(
      @PathVariable("id") UUID id, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.confirmCapture(id, body == null ? Map.of() : body)));
  }

  @GetMapping("/adjustments")
  public ApiResponse<List<Map<String, Object>>> adjustments(
      @RequestParam(name = "admissionNo", required = false) String admissionNo) {
    return ApiResponse.ok(wrap(() -> adjustments.list(admissionNo)));
  }

  @GetMapping("/adjustments/report")
  public ApiResponse<Map<String, Object>> adjustmentReport() {
    return ApiResponse.ok(wrap(adjustments::report));
  }

  @GetMapping("/adjustments/{id}/history")
  public ApiResponse<List<Map<String, Object>>> adjustmentHistory(@PathVariable("id") String id) {
    return ApiResponse.ok(wrap(() -> adjustments.history(id)));
  }

  @PostMapping("/adjustments")
  public ApiResponse<Map<String, Object>> createAdjustment(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(
        wrap(() -> adjustments.create(body, service.concessionApprovalRequired())));
  }

  @PostMapping("/adjustments/{id}/decision")
  public ApiResponse<Map<String, Object>> decideAdjustment(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(
        wrap(
            () ->
                adjustments.decide(
                    id,
                    body.get("status") == null ? "" : String.valueOf(body.get("status")),
                    body.get("reason") == null ? "" : String.valueOf(body.get("reason")))));
  }

  @PostMapping("/adjustments/{id}/remove")
  public ApiResponse<Map<String, Object>> removeAdjustment(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(
        wrap(
            () ->
                adjustments.remove(
                    id, body.get("reason") == null ? "" : String.valueOf(body.get("reason")))));
  }

  @GetMapping("/transactions")
  public ApiResponse<List<Map<String, Object>>> transactions() {
    return ApiResponse.ok(wrap(service::listTransactions));
  }

  private <T> T wrap(java.util.concurrent.Callable<T> call) {
    try {
      return call.call();
    } catch (FeeException ex) {
      HttpStatus status =
          "NOT_FOUND".equals(ex.getCode())
              ? HttpStatus.NOT_FOUND
              :           "FEATURE_OFF".equals(ex.getCode()) || "FORBIDDEN".equals(ex.getCode())
                  ? HttpStatus.FORBIDDEN
                  : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
  }
}
