package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.student.directory.StudentDirectoryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/directory")
public class StudentDirectoryController {

  private final StudentDirectoryService service;

  public StudentDirectoryController(StudentDirectoryService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/students")
  public ApiResponse<PageResult<Map<String, Object>>> search(
      @RequestParam Map<String, String> params) {
    return ApiResponse.ok(service.search(params != null ? params : Map.of()));
  }

  @GetMapping("/summary")
  public ApiResponse<Map<String, Object>> summary() {
    return ApiResponse.ok(service.summary());
  }

  @GetMapping(value = "/export.csv", produces = "text/csv")
  public ResponseEntity<byte[]> exportCsv(@RequestParam Map<String, String> params) {
    byte[] body = service.exportCsv(params != null ? new LinkedHashMap<>(params) : Map.of());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"student-directory.csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(body);
  }
}
