package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.settings.service.OfflineSyncService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/config/offline")
public class OfflineController {

  private final OfflineSyncService service;

  public OfflineController(OfflineSyncService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/manifest")
  public ApiResponse<Map<String, Object>> manifest() {
    return ApiResponse.ok(service.manifest());
  }

  @PostMapping("/sync")
  public ApiResponse<Map<String, Object>> sync(@RequestBody Map<String, Object> body) {
    try {
      return ApiResponse.ok(service.sync(body));
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/batches")
  public ApiResponse<List<Map<String, Object>>> batches() {
    try {
      return ApiResponse.ok(service.listBatches());
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }
}
