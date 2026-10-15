package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.service.AppUserService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/app-users")
public class AppUserController {

  private final AppUserService service;

  public AppUserController(AppUserService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.list());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.save(body == null ? Map.of() : body));
  }

  @PostMapping("/reset-password")
  public ApiResponse<Map<String, Object>> reset(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.reset(body == null ? Map.of() : body));
  }
}
