package com.sugamflow.school.admission.web;

import com.sugamflow.school.admission.lead.LeadDeskService;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admission/leads")
public class LeadController {

  private final LeadDeskService service;

  public LeadController(LeadDeskService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String classAppliedFor,
      @RequestParam(required = false) String assignedTo,
      @RequestParam(required = false) String scheduledFrom,
      @RequestParam(required = false) String scheduledTo) {
    return ApiResponse.ok(
        service.list(page, size, q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo));
  }

  @GetMapping("/summary")
  public ApiResponse<Map<String, Object>> summary() {
    return ApiResponse.ok(service.summary());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.create(body));
  }

  @PutMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.update(id, body));
  }

  @PatchMapping("/{id}/status")
  public ApiResponse<Map<String, Object>> patchStatus(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.patchStatus(id, body));
  }

  @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<Map<String, Object>> importFile(@RequestParam("file") MultipartFile file) {
    return ApiResponse.ok(service.importFile(file));
  }

  @PostMapping("/example")
  public ApiResponse<Map<String, Object>> example() {
    return ApiResponse.ok(service.loadExample());
  }

  @GetMapping("/export/excel")
  public ResponseEntity<byte[]> excel(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String classAppliedFor,
      @RequestParam(required = false) String assignedTo,
      @RequestParam(required = false) String scheduledFrom,
      @RequestParam(required = false) String scheduledTo) {
    byte[] body = service.exportExcel(q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leads.xlsx\"")
        .contentType(
            MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
        .body(body);
  }

  @GetMapping("/export/pdf")
  public ResponseEntity<byte[]> pdf(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String classAppliedFor,
      @RequestParam(required = false) String assignedTo,
      @RequestParam(required = false) String scheduledFrom,
      @RequestParam(required = false) String scheduledTo) {
    byte[] body = service.exportPdf(q, status, classAppliedFor, assignedTo, scheduledFrom, scheduledTo);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"leads.pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(body);
  }
}
