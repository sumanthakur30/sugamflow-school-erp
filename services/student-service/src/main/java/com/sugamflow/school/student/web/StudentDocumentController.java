package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.service.StudentDocumentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentDocumentController {

  private final StudentDocumentService service;

  public StudentDocumentController(StudentDocumentService service) {
    this.service = service;
  }

  @GetMapping("/students/{id}/documents")
  public ApiResponse<List<Map<String, Object>>> list(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.listForStudent(id));
  }

  @PostMapping("/students/{id}/documents")
  public ApiResponse<Map<String, Object>> issue(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.issue(id, body));
  }

  @GetMapping("/documents/{documentId}/pdf")
  public ResponseEntity<byte[]> pdf(@PathVariable("documentId") UUID documentId) {
    byte[] bytes = service.pdfBytes(documentId);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"student-document-" + documentId + ".pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes);
  }

  @PostMapping("/documents/{documentId}/revoke")
  public ApiResponse<Map<String, Object>> revoke(
      @PathVariable("documentId") UUID documentId, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(service.revoke(documentId, body == null ? Map.of() : body));
  }
}
