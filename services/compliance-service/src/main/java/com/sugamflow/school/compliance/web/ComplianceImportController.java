package com.sugamflow.school.compliance.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.compliance.dto.ImportBootstrapResponse;
import com.sugamflow.school.compliance.dto.ImportCommitResponse;
import com.sugamflow.school.compliance.dto.ImportJobResponse;
import com.sugamflow.school.compliance.service.ComplianceImportService;

@RestController
@RequestMapping("/api/compliance/import")
public class ComplianceImportController {
  private final ComplianceImportService importService;

  public ComplianceImportController(ComplianceImportService importService) {
    this.importService = importService;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<ImportBootstrapResponse> bootstrap() {
    return ApiResponse.ok(importService.bootstrap());
  }

  @GetMapping("/template.csv")
  public ResponseEntity<org.springframework.core.io.Resource> template(
      @RequestParam(defaultValue = "STUDENT") String entityType) {
    byte[] csv = importService.templateCsv(entityType);
    String name = entityType.trim().toLowerCase() + "-compliance-import-template.csv";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(new org.springframework.core.io.ByteArrayResource(csv));
  }

  @GetMapping("/jobs")
  public ApiResponse<List<ImportJobResponse>> listJobs() {
    return ApiResponse.ok(importService.listJobs());
  }

  @GetMapping("/jobs/{id}")
  public ApiResponse<ImportJobResponse> getJob(@PathVariable("id") Long id) {
    return ApiResponse.ok(importService.getJob(id));
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ImportJobResponse> upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(defaultValue = "STUDENT") String entityType,
      @RequestParam(required = false) Boolean fillBlankOnly)
      throws IOException {
    return ApiResponse.ok(importService.upload(file, entityType, fillBlankOnly));
  }

  @PostMapping("/jobs/{id}/validate")
  public ApiResponse<ImportJobResponse> validate(@PathVariable("id") Long id) {
    return ApiResponse.ok(importService.validate(id));
  }

  @PostMapping("/jobs/{id}/commit")
  public ApiResponse<ImportCommitResponse> commit(@PathVariable("id") Long id) {
    return ApiResponse.ok(importService.commit(id));
  }
}
