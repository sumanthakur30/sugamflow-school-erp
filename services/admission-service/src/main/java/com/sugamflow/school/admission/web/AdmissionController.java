package com.sugamflow.school.admission.web;

import com.sugamflow.school.admission.service.AdmissionApplicationService;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admission")
public class AdmissionController {

  private final AdmissionApplicationService service;

  public AdmissionController(AdmissionApplicationService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/applications")
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sortBy,
      @RequestParam(required = false) String sortDir) {
    return ApiResponse.ok(service.list(page, size, q, status, sortBy, sortDir));
  }

  @GetMapping("/register")
  public ApiResponse<Map<String, Object>> register(
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String q,
      @RequestParam(name = "format", defaultValue = "PDF") String format) {
    return ApiResponse.ok(service.registerExport(status, q, format));
  }

  @GetMapping("/applications/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping("/applications")
  public ApiResponse<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.submit(body));
  }

  @PutMapping("/applications/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.update(id, body));
  }

  @PostMapping("/applications/{id}/actions")
  public ApiResponse<Map<String, Object>> act(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.act(id, body));
  }

  @GetMapping("/applications/{id}/offer-letter")
  public ResponseEntity<byte[]> offerLetter(@PathVariable("id") UUID id) {
    byte[] pdf = service.getOfferLetterPdf(id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"offer-letter-" + id + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(pdf);
  }
}
