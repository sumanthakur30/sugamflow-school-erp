package com.sugamflow.school.exam.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.exam.service.AdmitCardPdfService;
import com.sugamflow.school.exam.service.AdmitCardService;
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
@RequestMapping("/api/exam/admit-cards")
public class AdmitCardController {

  private final AdmitCardService service;
  private final AdmitCardPdfService pdf;

  public AdmitCardController(AdmitCardService service, AdmitCardPdfService pdf) {
    this.service = service;
    this.pdf = pdf;
  }

  @GetMapping
  public ApiResponse<Map<String, Object>> pack(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam(name = "termKey", required = false) String termKey) {
    return ApiResponse.ok(service.pack(sectionId, termKey));
  }

  @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> pdf(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam(name = "termKey", required = false) String termKey) {
    Map<String, Object> pack = service.pack(sectionId, termKey);
    service.attachPhotoBytes(pack);
    byte[] bytes = pdf.render(pack, TenantContext.require().organizationId());
    String fileName = "admit-cards-" + safe(String.valueOf(pack.get("sectionLabel"))) + ".pdf";
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(bytes);
  }

  private static String safe(String raw) {
    if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
      return "class";
    }
    return raw.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
