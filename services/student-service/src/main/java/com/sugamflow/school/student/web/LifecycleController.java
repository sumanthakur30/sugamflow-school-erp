package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.lifecycle.LifecycleCatalog;
import com.sugamflow.school.student.service.LifecycleService;
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
@RequestMapping("/api/student/lifecycle")
public class LifecycleController {

  private final LifecycleService service;

  public LifecycleController(LifecycleService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(wrap(service::bootstrap));
  }

  @GetMapping("/promotion-maps")
  public ApiResponse<List<Map<String, Object>>> promotionMaps() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(LifecycleCatalog.TYPE_PROMOTION_MAP)));
  }

  @PutMapping("/promotion-maps/{key}")
  public ApiResponse<Map<String, Object>> savePromotionMap(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(LifecycleCatalog.TYPE_PROMOTION_MAP, body)));
  }

  @GetMapping("/sessions")
  public ApiResponse<List<Map<String, Object>>> sessions() {
    return ApiResponse.ok(
        wrap(() -> service.listDefinitions(LifecycleCatalog.TYPE_ACADEMIC_SESSION)));
  }

  @PutMapping("/sessions/{key}")
  public ApiResponse<Map<String, Object>> saveSession(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(LifecycleCatalog.TYPE_ACADEMIC_SESSION, body)));
  }

  @GetMapping("/status-policies")
  public ApiResponse<List<Map<String, Object>>> statusPolicies() {
    return ApiResponse.ok(
        wrap(() -> service.listDefinitions(LifecycleCatalog.TYPE_STATUS_POLICY)));
  }

  @PutMapping("/status-policies/{key}")
  public ApiResponse<Map<String, Object>> saveStatusPolicy(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(LifecycleCatalog.TYPE_STATUS_POLICY, body)));
  }

  @GetMapping("/tc-policies")
  public ApiResponse<List<Map<String, Object>>> tcPolicies() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(LifecycleCatalog.TYPE_TC_POLICY)));
  }

  @PutMapping("/tc-policies/{key}")
  public ApiResponse<Map<String, Object>> saveTcPolicy(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(LifecycleCatalog.TYPE_TC_POLICY, body)));
  }

  @PostMapping("/promote")
  public ApiResponse<Map<String, Object>> promote(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.promote(body)));
  }

  @PostMapping("/promote-by-class")
  public ApiResponse<Map<String, Object>> promoteByClass(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.promoteByClass(body)));
  }

  @PostMapping("/rollover")
  public ApiResponse<Map<String, Object>> rollover(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.rollover(body)));
  }

  @PostMapping("/rollover-by-class")
  public ApiResponse<Map<String, Object>> rolloverByClass(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.rolloverByClass(body)));
  }

  @PostMapping("/tc/clearance/preview")
  public ApiResponse<Map<String, Object>> previewTcClearance(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.previewTcClearance(body)));
  }

  @PostMapping("/tc")
  public ApiResponse<Map<String, Object>> issueTc(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.issueTc(body)));
  }

  @PostMapping("/dropout")
  public ApiResponse<Map<String, Object>> dropout(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.markDropout(body)));
  }

  @PostMapping("/alumni")
  public ApiResponse<Map<String, Object>> alumni(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.markAlumni(body)));
  }

  @GetMapping("/events")
  public ApiResponse<List<Map<String, Object>>> events(
      @RequestParam(required = false) UUID studentId) {
    return ApiResponse.ok(wrap(() -> service.listEvents(studentId)));
  }

  private <T> T wrap(java.util.concurrent.Callable<T> call) {
    try {
      return call.call();
    } catch (StudentException ex) {
      HttpStatus status =
          "NOT_FOUND".equals(ex.getCode())
              ? HttpStatus.NOT_FOUND
              : "FEATURE_OFF".equals(ex.getCode())
                  ? HttpStatus.FORBIDDEN
                  : "BLOCK_TC".equals(ex.getCode())
                      ? HttpStatus.BAD_REQUEST
                      : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
  }
}
