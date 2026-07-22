package com.sugamflow.school.exam.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.exam.service.HomeworkService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exam/homework")
public class HomeworkController {

  private final HomeworkService service;

  public HomeworkController(HomeworkService service) {
    this.service = service;
  }

  @GetMapping("/mine")
  public ApiResponse<List<Map<String, Object>>> mine() {
    return ApiResponse.ok(service.listMine());
  }

  @PostMapping("/{id}/submissions/mine")
  public ApiResponse<Map<String, Object>> submitMine(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.submitMine(id, body == null ? Map.of() : body));
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list(
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "sectionId", required = false) String sectionId) {
    return ApiResponse.ok(service.list(status, sectionId));
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @GetMapping("/{id}/submissions")
  public ApiResponse<List<Map<String, Object>>> submissions(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.listSubmissions(id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.create(body));
  }

  @PostMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> status(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateStatus(id, body));
  }

  @PostMapping("/{id}/submissions")
  public ApiResponse<Map<String, Object>> submit(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.submit(id, body));
  }

  @PostMapping("/submissions/{submissionId}/grade")
  public ApiResponse<Map<String, Object>> grade(
      @PathVariable("submissionId") UUID submissionId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.grade(submissionId, body));
  }
}
