package com.sugamflow.school.exam.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.service.ReportCardPdfService;
import com.sugamflow.school.exam.service.ReportCardService;
import com.sugamflow.school.exam.service.ReportCardInsightService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/exam/report-cards")
public class ReportCardController {

  private final ReportCardService service;
  private final ReportCardPdfService pdfService;
  private final ReportCardInsightService insights;

  public ReportCardController(
      ReportCardService service,
      ReportCardPdfService pdfService,
      ReportCardInsightService insights) {
    this.service = service;
    this.pdfService = pdfService;
    this.insights = insights;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> section(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("termKey") String termKey,
      @RequestParam(name = "publishedOnly", defaultValue = "false") boolean publishedOnly) {
    return ApiResponse.ok(service.sectionTermReport(sectionId, termKey, publishedOnly));
  }

  @GetMapping("/student")
  public ApiResponse<Map<String, Object>> student(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("termKey") String termKey,
      @RequestParam(name = "studentId", required = false) String studentId,
      @RequestParam(name = "admissionNo", required = false) String admissionNo,
      @RequestParam(name = "publishedOnly", defaultValue = "false") boolean publishedOnly) {
    return ApiResponse.ok(
        service.studentTermReport(sectionId, termKey, studentKey(studentId, admissionNo), publishedOnly));
  }

  @GetMapping("/student/insights")
  public ApiResponse<Map<String, Object>> studentInsights(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("termKey") String termKey,
      @RequestParam(name = "studentId", required = false) String studentId,
      @RequestParam(name = "admissionNo", required = false) String admissionNo,
      @RequestParam(name = "publishedOnly", defaultValue = "false") boolean publishedOnly) {
    return ApiResponse.ok(
        insights.studentInsights(
            sectionId, termKey, studentKey(studentId, admissionNo), publishedOnly));
  }

  @GetMapping(value = "/student.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> studentPdf(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("termKey") String termKey,
      @RequestParam(name = "studentId", required = false) String studentId,
      @RequestParam(name = "admissionNo", required = false) String admissionNo,
      @RequestParam(name = "publishedOnly", defaultValue = "false") boolean publishedOnly) {
    Map<String, Object> card =
        service.studentTermReport(sectionId, termKey, studentKey(studentId, admissionNo), publishedOnly);
    card.put(
        "insights",
        insights.studentInsights(
            sectionId, termKey, studentKey(studentId, admissionNo), publishedOnly));
    return pdf(card);
  }

  @GetMapping("/mine")
  public ApiResponse<List<Map<String, Object>>> mine(
      @RequestParam(name = "termKey", required = false) String termKey) {
    return ApiResponse.ok(service.myReportCards(termKey));
  }

  @GetMapping("/mine/insights")
  public ApiResponse<Map<String, Object>> mineInsights(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("termKey") String termKey,
      @RequestParam(name = "studentId", required = false) String studentId,
      @RequestParam(name = "admissionNo", required = false) String admissionNo) {
    return ApiResponse.ok(
        insights.myStudentInsights(
            sectionId, termKey, studentKey(studentId, admissionNo)));
  }

  @GetMapping(value = "/mine.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> minePdf(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("termKey") String termKey,
      @RequestParam(name = "studentId", required = false) String studentId,
      @RequestParam(name = "admissionNo", required = false) String admissionNo) {
    Map<String, Object> card =
        service.myStudentTermReport(sectionId, termKey, studentKey(studentId, admissionNo));
    card.put(
        "insights",
        insights.myStudentInsights(
            sectionId, termKey, studentKey(studentId, admissionNo)));
    return pdf(card);
  }

  private ResponseEntity<byte[]> pdf(Map<String, Object> card) {
    TenantScope scope = TenantContext.require();
    byte[] bytes = pdfService.render(card, scope.organizationId());
    @SuppressWarnings("unchecked")
    Map<String, Object> student = (Map<String, Object>) card.get("student");
    String admission = student == null ? null : String.valueOf(student.get("admissionNo"));
    String term = String.valueOf(card.get("termKey"));
    String fileName = "report-card-" + safe(admission) + "-" + safe(term) + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes);
  }

  private static String studentKey(String studentId, String admissionNo) {
    if (studentId != null && !studentId.isBlank()) {
      return studentId.trim();
    }
    if (admissionNo != null && !admissionNo.isBlank()) {
      return admissionNo.trim();
    }
    throw new ExamException("VALIDATION", "studentId or admissionNo is required");
  }

  private static String safe(String raw) {
    if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
      return "student";
    }
    return raw.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
