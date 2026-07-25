package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.persistence.entity.StudentAttachmentEntity;
import com.sugamflow.school.student.service.StudentAttachmentService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentAttachmentController {

  private final StudentAttachmentService service;

  public StudentAttachmentController(StudentAttachmentService service) {
    this.service = service;
  }

  @GetMapping("/students/{id}/attachments")
  public ApiResponse<List<Map<String, Object>>> list(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.listForStudent(id));
  }

  @PostMapping("/students/{id}/attachments")
  public ApiResponse<Map<String, Object>> upload(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.upload(id, body));
  }

  @DeleteMapping("/attachments/{attachmentId}")
  public ApiResponse<Map<String, Object>> delete(@PathVariable("attachmentId") UUID attachmentId) {
    service.delete(attachmentId);
    return ApiResponse.ok(Map.of("deleted", true, "id", attachmentId.toString()));
  }

  @GetMapping("/attachments/{attachmentId}/content")
  public ResponseEntity<byte[]> content(@PathVariable("attachmentId") UUID attachmentId) {
    StudentAttachmentEntity meta = service.require(attachmentId);
    byte[] bytes = service.contentBytes(attachmentId);
    MediaType media =
        meta.getContentType() != null && !meta.getContentType().isBlank()
            ? MediaType.parseMediaType(meta.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM;
    String fileName =
        meta.getFileName() != null && !meta.getFileName().isBlank()
            ? meta.getFileName()
            : "attachment-" + attachmentId;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
        .contentType(media)
        .body(bytes);
  }
}
