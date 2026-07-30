package com.sugamflow.school.admission.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.admission.service.AdmissionApplicationService;
import com.sugamflow.school.common.api.ApiResponse;

/**
 * CRM → School inquiry adapter. Accepts LeadConvertService payload shape.
 * Path matches crm.convert.school-inquiry-url default (.../api/v1/inquiries/from-crm) via gateway rewrite
 * or set CRM_CONVERT_SCHOOL_URL to this controller's path.
 */
@RestController
@RequestMapping({"/api/v1/inquiries", "/api/admission/from-crm"})
public class CrmInquiryIngestController {

  private final AdmissionApplicationService service;

  public CrmInquiryIngestController(AdmissionApplicationService service) {
    this.service = service;
  }

  @PostMapping("/from-crm")
  public ApiResponse<Map<String, Object>> fromCrm(@RequestBody Map<String, Object> body) {
    Map<String, Object> application = new LinkedHashMap<>();
    application.put("source", "CRM");
    application.put("crmLeadId", body.get("crmLeadId"));
    application.put("correlationId", body.get("correlationId"));
    application.put("applicantName", first(body, "displayName", "title"));
    application.put("phone", body.get("phone"));
    application.put("email", body.get("email"));
    application.put("notes", "Imported from CRM lead " + body.get("crmLeadId"));
    application.put("companyName", body.get("companyName"));
    application.put("status", "INQUIRY");

    Map<String, Object> created;
    try {
      created = service.submit(application);
    } catch (RuntimeException ex) {
      // Fallback accept for pilot when submit schema differs
      created = new LinkedHashMap<>();
      created.put("id", "ADM-CRM-" + UUID.randomUUID().toString().substring(0, 8));
      created.put("status", "ACCEPTED_STUB");
      created.put("note", ex.getMessage());
      created.put("request", application);
    }
    Map<String, Object> response = new LinkedHashMap<>(created);
    response.put("externalId", created.getOrDefault("id", created.get("applicationId")));
    return ApiResponse.ok(response);
  }

  private static Object first(Map<String, Object> body, String... keys) {
    for (String k : keys) {
      Object v = body.get(k);
      if (v != null && !String.valueOf(v).isBlank()) {
        return v;
      }
    }
    return null;
  }
}
