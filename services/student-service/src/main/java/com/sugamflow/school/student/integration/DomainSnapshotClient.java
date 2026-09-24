package com.sugamflow.school.student.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Best-effort snapshots from attendance / exam for Student 360 (never fails the profile). */
@Component
public class DomainSnapshotClient {

  private static final Logger log = LoggerFactory.getLogger(DomainSnapshotClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final StudentProperties properties;

  public DomainSnapshotClient(RestClient.Builder restClientBuilder, StudentProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> recentAttendance(TenantScope scope, UUID studentId, String admissionNo) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("available", false);
    out.put("items", List.of());
    String base = properties.getIntegrations().getAttendanceBaseUrl();
    if (base == null || base.isBlank()) {
      return out;
    }
    try {
      // Parent/staff marks/mine is access-scoped; for staff 360 we ask directory-filtered roster path
      // via published marks list if present. Soft-fail when unavailable.
      Map<String, Object> body =
          get(base + "/api/attendance/marks/mine", scope);
      List<Map<String, Object>> items = extractList(body);
      List<Map<String, Object>> filtered = filterByStudent(items, studentId, admissionNo);
      out.put("available", true);
      out.put("items", filtered.stream().limit(20).toList());
      out.put("count", filtered.size());
    } catch (Exception ex) {
      log.debug("Attendance snapshot skipped: {}", ex.getMessage());
      out.put("error", "unavailable");
    }
    return out;
  }

  public Map<String, Object> publishedMarks(TenantScope scope, UUID studentId, String admissionNo) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("available", false);
    out.put("items", List.of());
    String base = properties.getIntegrations().getExamBaseUrl();
    if (base == null || base.isBlank()) {
      return out;
    }
    try {
      Map<String, Object> body = get(base + "/api/exam/marks/published", scope);
      List<Map<String, Object>> items = extractList(body);
      List<Map<String, Object>> filtered = filterByStudent(items, studentId, admissionNo);
      out.put("available", true);
      out.put("items", filtered.stream().limit(30).toList());
      out.put("count", filtered.size());
    } catch (Exception ex) {
      log.debug("Exam snapshot skipped: {}", ex.getMessage());
      out.put("error", "unavailable");
    }
    return out;
  }

  /**
   * Campus ops cards for Student 360 — open library loans, active hostel bed, transport route.
   * Soft-fails per domain so a down service does not blank the profile.
   */
  public Map<String, Object> campusOps(TenantScope scope, String admissionNo) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("openLoans", List.of());
    out.put("openLoanCount", 0);
    out.put("bed", Map.of());
    out.put("route", Map.of());
    if (admissionNo == null || admissionNo.isBlank()) {
      return out;
    }
    String encoded = java.net.URLEncoder.encode(admissionNo.trim(), java.nio.charset.StandardCharsets.UTF_8);

    String libraryBase = properties.getIntegrations().getLibraryBaseUrl();
    if (libraryBase != null && !libraryBase.isBlank()) {
      try {
        Map<String, Object> body =
            get(libraryBase + "/api/library/circulation/issues?admissionNo=" + encoded, scope);
        List<Map<String, Object>> loans = extractList(body);
        out.put("openLoans", loans);
        out.put("openLoanCount", loans.size());
        out.put("libraryAvailable", true);
      } catch (Exception ex) {
        log.debug("Library ops snapshot skipped: {}", ex.getMessage());
        out.put("libraryAvailable", false);
      }
    }

    String hostelBase = properties.getIntegrations().getHostelBaseUrl();
    if (hostelBase != null && !hostelBase.isBlank()) {
      try {
        Map<String, Object> bed =
            get(hostelBase + "/api/hostel/beds/occupancies?admissionNo=" + encoded, scope);
        out.put("bed", bed == null || bed.isEmpty() ? Map.of() : bed);
        out.put("hostelAvailable", true);
      } catch (Exception ex) {
        log.debug("Hostel ops snapshot skipped: {}", ex.getMessage());
        out.put("hostelAvailable", false);
      }
    }

    String transportBase = properties.getIntegrations().getTransportBaseUrl();
    if (transportBase != null && !transportBase.isBlank()) {
      try {
        Map<String, Object> route =
            get(transportBase + "/api/transport/routes/assignments?admissionNo=" + encoded, scope);
        out.put("route", route == null || route.isEmpty() ? Map.of() : route);
        out.put("transportAvailable", true);
      } catch (Exception ex) {
        log.debug("Transport ops snapshot skipped: {}", ex.getMessage());
        out.put("transportAvailable", false);
      }
    }
    return out;
  }

  private List<Map<String, Object>> filterByStudent(
      List<Map<String, Object>> items, UUID studentId, String admissionNo) {
    List<Map<String, Object>> out = new ArrayList<>();
    String sid = studentId == null ? null : studentId.toString();
    for (Map<String, Object> row : items) {
      String rowSid = str(row.get("studentId"));
      String rowAdm = str(row.get("admissionNo"));
      if (sid != null && sid.equalsIgnoreCase(rowSid)) {
        out.add(row);
      } else if (admissionNo != null && admissionNo.equalsIgnoreCase(rowAdm)) {
        out.add(row);
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> extractList(Map<String, Object> body) {
    if (body == null) {
      return List.of();
    }
    Object value = body.get("value");
    if (value instanceof List<?> list) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          out.add((Map<String, Object>) m);
        }
      }
      return out;
    }
    if (body.get("items") instanceof List<?> list) {
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          out.add((Map<String, Object>) m);
        }
      }
      return out;
    }
    return List.of();
  }

  private Map<String, Object> get(String url, TenantScope scope) {
    Map<String, Object> envelope =
        restClientBuilder
            .build()
            .get()
            .uri(url)
            .headers(h -> TenantHeaders.apply(h, scope))
            .retrieve()
            .body(MAP_TYPE);
    if (envelope == null) {
      return Map.of();
    }
    Object data = envelope.get("data");
    if (data instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> m = (Map<String, Object>) map;
      return m;
    }
    if (data instanceof List<?> list) {
      Map<String, Object> wrap = new LinkedHashMap<>();
      wrap.put("value", list);
      return wrap;
    }
    return envelope;
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }
}
