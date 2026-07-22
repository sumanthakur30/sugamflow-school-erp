package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.service.StudentDocumentService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unauthenticated verification for QR codes printed on student documents. */
@RestController
@RequestMapping("/api/student/public")
public class PublicStudentDocumentController {

  private final StudentDocumentService service;

  public PublicStudentDocumentController(StudentDocumentService service) {
    this.service = service;
  }

  @GetMapping("/documents/verify/{token}")
  public ApiResponse<Map<String, Object>> verify(@PathVariable("token") String token) {
    return ApiResponse.ok(service.verifyPublic(token));
  }
}
