package com.sugamflow.school.payroll.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.payroll.ops.PayrollOpsCatalog;
import com.sugamflow.school.payroll.service.PayrollOpsService;
import java.util.List;
import java.util.Map;
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
@RequestMapping("/api/payroll/ops")
public class PayrollOpsController {

  private final PayrollOpsService service;

  public PayrollOpsController(PayrollOpsService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(wrap(service::bootstrap));
  }

  @GetMapping("/structures")
  public ApiResponse<List<Map<String, Object>>> structures() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(PayrollOpsCatalog.TYPE_SALARY_STRUCTURE)));
  }

  @PutMapping("/structures/{key}")
  public ApiResponse<Map<String, Object>> saveStructure(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(PayrollOpsCatalog.TYPE_SALARY_STRUCTURE, body)));
  }

  @PostMapping("/payslips/preview")
  public ApiResponse<Map<String, Object>> previewPayslip(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.previewPayslip(body)));
  }

  private <T> T wrap(java.util.concurrent.Callable<T> call) {
    try {
      return call.call();
    } catch (PayrollException ex) {
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
