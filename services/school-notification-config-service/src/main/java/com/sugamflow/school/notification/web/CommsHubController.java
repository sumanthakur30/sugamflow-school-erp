package com.sugamflow.school.notification.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.notification.service.CommsHubService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/school/notification-config/comms")
public class CommsHubController {

  private final CommsHubService service;

  public CommsHubController(CommsHubService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/announcements")
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping("/announcements")
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.create(body));
  }
}
