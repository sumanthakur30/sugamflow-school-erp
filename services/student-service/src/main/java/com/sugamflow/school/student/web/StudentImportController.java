package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.service.StudentImportService;
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
@RequestMapping("/api/student/import")
public class StudentImportController {

  private final StudentImportService service;

  public StudentImportController(StudentImportService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/jobs")
  public ApiResponse<List<Map<String, Object>>> listJobs() {
    return ApiResponse.ok(service.listJobs());
  }

  @GetMapping("/jobs/{id}")
  public ApiResponse<Map<String, Object>> getJob(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.getJob(id));
  }

  @PostMapping("/jobs")
  public ApiResponse<Map<String, Object>> createJob(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createJob(body));
  }

  @PostMapping("/jobs/{id}/dry-run")
  public ApiResponse<Map<String, Object>> dryRun(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.dryRun(id));
  }

  @PostMapping("/jobs/{id}/commit")
  public ApiResponse<Map<String, Object>> commit(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.commit(id));
  }
}
