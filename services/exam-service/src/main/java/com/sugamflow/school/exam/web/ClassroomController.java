package com.sugamflow.school.exam.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.exam.service.ClassroomService;
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
@RequestMapping("/api/exam/classroom")
public class ClassroomController {

  private final ClassroomService service;

  public ClassroomController(ClassroomService service) {
    this.service = service;
  }

  @GetMapping("/{kind}")
  public ApiResponse<List<Map<String, Object>>> list(@PathVariable("kind") String kind) {
    return ApiResponse.ok(service.list(kind));
  }

  @PostMapping("/{kind}")
  public ApiResponse<Map<String, Object>> create(
      @PathVariable("kind") String kind, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.create(kind, body == null ? Map.of() : body));
  }

  @GetMapping("/items/{id}/responses")
  public ApiResponse<List<Map<String, Object>>> responses(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.listResponses(id));
  }

  @PostMapping("/items/{id}/responses")
  public ApiResponse<Map<String, Object>> respond(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.respond(id, body == null ? Map.of() : body));
  }

  @PostMapping("/items/{id}/marks")
  public ApiResponse<Map<String, Object>> marks(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.importMarks(id, body == null ? Map.of() : body));
  }
}
