package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.service.CampusDeskService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/desk")
public class CampusDeskController {

  private final CampusDeskService service;

  public CampusDeskController(CampusDeskService service) {
    this.service = service;
  }

  @GetMapping("/{kind}")
  public ApiResponse<List<Map<String, Object>>> list(@PathVariable("kind") String kind) {
    return ApiResponse.ok(service.list(kind));
  }

  @PostMapping("/{kind}")
  public ApiResponse<Map<String, Object>> create(
      @PathVariable("kind") String kind, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.create(kind, body));
  }

  @PostMapping("/items/{id}/status")
  public ApiResponse<Map<String, Object>> updateStatus(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateStatus(id, body));
  }
}
