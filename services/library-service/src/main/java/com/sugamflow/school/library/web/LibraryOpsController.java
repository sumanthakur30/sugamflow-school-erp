package com.sugamflow.school.library.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.library.ops.LibraryOpsCatalog;
import com.sugamflow.school.library.service.LibraryOpsService;
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
@RequestMapping("/api/library/ops")
public class LibraryOpsController {

  private final LibraryOpsService service;

  public LibraryOpsController(LibraryOpsService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(wrap(service::bootstrap));
  }

  @GetMapping("/categories")
  public ApiResponse<List<Map<String, Object>>> categories() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(LibraryOpsCatalog.TYPE_BOOK_CATEGORY)));
  }

  @PutMapping("/categories/{key}")
  public ApiResponse<Map<String, Object>> saveCategory(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(LibraryOpsCatalog.TYPE_BOOK_CATEGORY, body)));
  }

  @GetMapping("/fine-policies")
  public ApiResponse<List<Map<String, Object>>> finePolicies() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(LibraryOpsCatalog.TYPE_FINE_POLICY)));
  }

  @PutMapping("/fine-policies/{key}")
  public ApiResponse<Map<String, Object>> saveFinePolicy(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(
        wrap(() -> service.saveDefinition(LibraryOpsCatalog.TYPE_FINE_POLICY, body)));
  }

  @PostMapping("/fines/preview")
  public ApiResponse<Map<String, Object>> previewFine(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.previewFine(body)));
  }

  private <T> T wrap(java.util.concurrent.Callable<T> call) {
    try {
      return call.call();
    } catch (LibraryException ex) {
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
