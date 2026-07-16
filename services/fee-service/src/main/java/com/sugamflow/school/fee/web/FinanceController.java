package com.sugamflow.school.fee.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.fee.finance.FinanceCatalog;
import com.sugamflow.school.fee.service.FinanceService;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/fee/finance")
public class FinanceController {

  private final FinanceService service;

  public FinanceController(FinanceService service) {
    this.service = service;
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

  @PostMapping("/payments/intents")
  public ApiResponse<Map<String, Object>> createIntent(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.createPaymentIntent(body)));
  }

  @PostMapping("/payments/intents/{id}/simulate-capture")
  public ApiResponse<Map<String, Object>> simulateCapture(@PathVariable("id") UUID id) {
    return ApiResponse.ok(wrap(() -> service.simulateCapture(id)));
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
              : "FEATURE_OFF".equals(ex.getCode())
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
