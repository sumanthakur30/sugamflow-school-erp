package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.student.directory.SensitiveExportAuditService;
import com.sugamflow.school.student.directory.StudentDirectoryService;
import com.sugamflow.school.student.directory.UdiseExportService;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/directory")
public class StudentDirectoryController {

  private final StudentDirectoryService service;
  private final UdiseExportService udise;
  private final SensitiveExportAuditService exportAudit;

  public StudentDirectoryController(
      StudentDirectoryService service,
      UdiseExportService udise,
      SensitiveExportAuditService exportAudit) {
    this.service = service;
    this.udise = udise;
    this.exportAudit = exportAudit;
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

  @GetMapping("/export")
  public ApiResponse<Map<String, Object>> exportWorkbook(
      @RequestParam Map<String, String> params,
      @RequestParam(name = "format", defaultValue = "EXCEL") String format) {
    Map<String, String> p = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();
    p.remove("format");
    return ApiResponse.ok(service.exportWorkbook(p, format));
  }

  @GetMapping("/udise-setting")
  public ApiResponse<Map<String, Object>> udiseSetting() {
    return ApiResponse.ok(udise.setting());
  }

  @PutMapping("/udise-setting")
  public ApiResponse<Map<String, Object>> saveUdiseSetting(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(udise.save(body != null ? body : Map.of()));
  }

  @GetMapping("/export-audit")
  public ApiResponse<List<Map<String, Object>>> exportAudit() {
    return ApiResponse.ok(exportAudit.recent());
  }

  @GetMapping(value = "/export-udise.csv", produces = "text/csv")
  public ResponseEntity<byte[]> exportUdise(@RequestParam Map<String, String> params) {
    byte[] body = udise.exportCsv(params != null ? new LinkedHashMap<>(params) : Map.of());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"udise-students.csv\"")
        .contentType(new MediaType("text", "csv"))
        .body(body);
  }
}
