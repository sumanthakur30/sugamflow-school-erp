package com.sugamflow.school.reportbuilder.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.reportbuilder.service.ReportElementCatalog;
import com.sugamflow.school.reportbuilder.service.ReportTemplateService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
public class ReportBuilderController {
  private final ReportTemplateService service;

  public ReportBuilderController(ReportTemplateService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/element-types")
  public ApiResponse<List<Map<String, Object>>> elementTypes() {
    return ApiResponse.ok(ReportElementCatalog.ELEMENT_TYPES);
  }

  @GetMapping("/templates")
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.list(TenantContext.require().organizationId()));
  }

  @GetMapping("/templates/{templateKey}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("templateKey") String templateKey) {
    return ApiResponse.ok(service.get(TenantContext.require().organizationId(), templateKey));
  }

  @PutMapping("/templates/{templateKey}")
  public ApiResponse<Map<String, Object>> save(
      @PathVariable("templateKey") String templateKey, @RequestBody Map<String, Object> body) {
    try {
      return ApiResponse.ok(
          service.save(TenantContext.require().organizationId(), templateKey, body));
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/templates")
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    String key = String.valueOf(body.getOrDefault("templateKey", UUID.randomUUID()));
    try {
      return ApiResponse.ok(service.save(TenantContext.require().organizationId(), key, body));
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PostMapping("/templates/{templateKey}/render")
  public ApiResponse<Map<String, Object>> render(
      @PathVariable("templateKey") String templateKey, @RequestBody Map<String, Object> body) {
    try {
      return ApiResponse.ok(
          service.render(TenantContext.require().organizationId(), templateKey, body));
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  /** Preview unsaved designer canvas (body = template + optional data). */
  @PostMapping("/preview")
  public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
    try {
      return ApiResponse.ok(service.previewLayout(body));
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @GetMapping("/export-formats")
  public ApiResponse<List<String>> formats() {
    return ApiResponse.ok(List.of("PDF", "EXCEL", "CSV", "PRINT"));
  }
}
