package com.sugamflow.school.exam.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.exam.service.GradebookService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exam")
public class GradebookController {

  private final GradebookService service;

  public GradebookController(GradebookService service) {
    this.service = service;
  }

  @GetMapping("/definitions")
  public ApiResponse<List<Map<String, Object>>> listDefinitions(
      @RequestParam(name = "sectionId", required = false) UUID sectionId,
      @RequestParam(name = "subjectId", required = false) UUID subjectId) {
    return ApiResponse.ok(service.listDefinitions(sectionId, subjectId));
  }

  @PostMapping("/definitions")
  public ApiResponse<Map<String, Object>> createDefinition(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createDefinition(body));
  }

  @GetMapping("/gradebook")
  public ApiResponse<Map<String, Object>> gradebook(
      @RequestParam("examDefinitionId") UUID examDefinitionId) {
    return ApiResponse.ok(service.gradebook(examDefinitionId));
  }

  @PutMapping("/gradebook/bulk")
  public ApiResponse<Map<String, Object>> bulkSave(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.bulkSave(body));
  }

  @PostMapping("/definitions/{id}/publish")
  public ApiResponse<Map<String, Object>> publish(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.publish(id));
  }

  @PostMapping("/definitions/{id}/lock")
  public ApiResponse<Map<String, Object>> lock(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.lock(id));
  }

  @GetMapping("/marks/published")
  public ApiResponse<List<Map<String, Object>>> publishedMarks() {
    return ApiResponse.ok(service.publishedMarks());
  }
}
