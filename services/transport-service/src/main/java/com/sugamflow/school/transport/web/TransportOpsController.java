package com.sugamflow.school.transport.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.transport.ops.TransportOpsCatalog;
import com.sugamflow.school.transport.service.TransportOpsService;
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
@RequestMapping("/api/transport/ops")
public class TransportOpsController {

  private final TransportOpsService service;

  public TransportOpsController(TransportOpsService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(wrap(service::bootstrap));
  }

  @GetMapping("/routes")
  public ApiResponse<List<Map<String, Object>>> routes() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(TransportOpsCatalog.TYPE_ROUTE)));
  }

  @PutMapping("/routes/{key}")
  public ApiResponse<Map<String, Object>> saveRoute(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(wrap(() -> service.saveDefinition(TransportOpsCatalog.TYPE_ROUTE, body)));
  }

  @PostMapping("/fares/preview")
  public ApiResponse<Map<String, Object>> previewFare(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.previewFare(body)));
  }

  private <T> T wrap(java.util.concurrent.Callable<T> call) {
    try {
      return call.call();
    } catch (TransportException ex) {
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
