package com.sugamflow.school.compliance.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.config.ComplianceProperties;

/**
 * Reads student/staff projections without owning master schemas. Student directory exposes common
 * CBSE fields; full student/staff records supply answers (DOB, etc.).
 */
@Component
public class MasterDataClient {
  private static final Logger log = LoggerFactory.getLogger(MasterDataClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final int PAGE_SIZE = 200;
  private static final int MAX_PAGES = 25;

  private final RestClient.Builder restClientBuilder;
  private final ComplianceProperties properties;

  public MasterDataClient(RestClient.Builder restClientBuilder, ComplianceProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public List<Map<String, Object>> listStudentProjections(TenantScope scope) {
    Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    for (Map<String, Object> row : pageAll(scope, studentDirectoryUrl(0), "directory")) {
      String id = str(row.get("id"));
      if (!id.isEmpty()) {
        byId.put(id, new LinkedHashMap<>(row));
      }
    }
    for (Map<String, Object> record : pageAll(scope, studentRecordsUrl(0), "records")) {
      String id = str(record.get("id"));
      if (id.isEmpty()) {
        continue;
      }
      Map<String, Object> flat = byId.computeIfAbsent(id, k -> new LinkedHashMap<>());
      flat.put("id", id);
      if (record.get("admissionNo") != null) {
        flat.put("admissionNo", record.get("admissionNo"));
      }
      if (record.get("status") != null) {
        flat.put("status", record.get("status"));
      }
      mergeAnswers(flat, record.get("answers"));
      enrichStudentAliases(flat);
    }
    return new ArrayList<>(byId.values());
  }

  public List<Map<String, Object>> listStaffProjections(TenantScope scope) {
    Map<String, Map<String, Object>> byId = new LinkedHashMap<>();
    for (Map<String, Object> row : pageAll(scope, staffDirectoryUrl(0), "directory")) {
      String id = str(row.get("id"));
      if (!id.isEmpty()) {
        byId.put(id, new LinkedHashMap<>(row));
      }
    }
    for (Map<String, Object> record : pageAll(scope, staffRecordsUrl(0), "records")) {
      String id = str(record.get("id"));
      if (id.isEmpty()) {
        continue;
      }
      Map<String, Object> flat = byId.computeIfAbsent(id, k -> new LinkedHashMap<>());
      flat.put("id", id);
      if (record.get("employeeNo") != null) {
        flat.put("employeeNo", record.get("employeeNo"));
      }
      if (record.get("status") != null) {
        flat.put("status", record.get("status"));
      }
      mergeAnswers(flat, record.get("answers"));
    }
    return new ArrayList<>(byId.values());
  }

  /** Partial answers merge on student master (PUT /api/student/students/{id}). */
  public Map<String, Object> patchStudentAnswers(
      TenantScope scope, String studentId, Map<String, Object> answers) {
    return putAnswers(
        scope,
        properties.getIntegrations().getStudentBaseUrl() + "/api/student/students/" + studentId,
        answers,
        "student");
  }

  /** Partial answers merge on staff master (PUT /api/staff/staff/{id}). */
  public Map<String, Object> patchStaffAnswers(
      TenantScope scope, String staffId, Map<String, Object> answers) {
    return putAnswers(
        scope,
        properties.getIntegrations().getStaffBaseUrl() + "/api/staff/staff/" + staffId,
        answers,
        "staff");
  }

  private Map<String, Object> putAnswers(
      TenantScope scope, String url, Map<String, Object> answers, String kind) {
    try {
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("answers", answers != null ? answers : Map.of());
      body.put("reason", "Compliance Import Center gap-fill");
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .put()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .body(body)
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null) {
        return Map.of();
      }
      Object data = envelope.get("data");
      if (data instanceof Map<?, ?> m) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) m;
        return cast;
      }
      return Map.of();
    } catch (Exception ex) {
      log.warn("Master data {} patch failed ({}): {}", kind, url, ex.getMessage());
      throw new IllegalStateException("Failed to update " + kind + " record: " + ex.getMessage(), ex);
    }
  }

  private List<Map<String, Object>> pageAll(TenantScope scope, String firstUrlTemplate, String kind) {
    List<Map<String, Object>> all = new ArrayList<>();
    for (int page = 0; page < MAX_PAGES; page++) {
      String url =
          firstUrlTemplate.contains("page=0")
              ? firstUrlTemplate.replace("page=0", "page=" + page)
              : firstUrlTemplate + (firstUrlTemplate.contains("?") ? "&" : "?") + "page=" + page;
      List<Map<String, Object>> items = fetchItems(scope, url, kind);
      all.addAll(items);
      if (items.size() < PAGE_SIZE) {
        break;
      }
    }
    return all;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> fetchItems(TenantScope scope, String url, String kind) {
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null) {
        return List.of();
      }
      Object data = envelope.get("data");
      if (!(data instanceof Map<?, ?> page)) {
        return List.of();
      }
      Object items = page.get("items");
      if (!(items instanceof List<?> list)) {
        return List.of();
      }
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          out.add((Map<String, Object>) m);
        }
      }
      return out;
    } catch (Exception ex) {
      log.warn("Master data {} fetch failed ({}): {}", kind, url, ex.getMessage());
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static void mergeAnswers(Map<String, Object> flat, Object answersObj) {
    if (!(answersObj instanceof Map<?, ?> answers)) {
      return;
    }
    for (Map.Entry<?, ?> e : answers.entrySet()) {
      if (e.getKey() == null) {
        continue;
      }
      String key = String.valueOf(e.getKey());
      Object existing = flat.get(key);
      if (existing == null || str(existing).isEmpty()) {
        flat.put(key, e.getValue());
      }
    }
    Object guardians = answers.get("guardians");
    if (guardians instanceof List<?> list && !list.isEmpty()) {
      flat.putIfAbsent("guardians", list);
    }
  }

  private static void enrichStudentAliases(Map<String, Object> flat) {
    putIfBlank(flat, "fullName", firstNonBlank(str(flat.get("fullName")), str(flat.get("studentName"))));
    putIfBlank(
        flat,
        "classSection",
        firstNonBlank(str(flat.get("classSection")), str(flat.get("classApplied"))));
    putIfBlank(
        flat,
        "dateOfBirth",
        firstNonBlank(str(flat.get("dateOfBirth")), str(flat.get("dob"))));
    putIfBlank(
        flat,
        "apaarId",
        firstNonBlank(str(flat.get("apaarId")), str(flat.get("apaarNumber"))));
    putIfBlank(
        flat,
        "aadhaar",
        firstNonBlank(str(flat.get("aadhaar")), str(flat.get("aadhaarNumber"))));
    if (blank(flat.get("parentName"))) {
      String parent =
          firstNonBlank(
              str(flat.get("parentName")),
              str(flat.get("fatherName")),
              str(flat.get("motherName")),
              str(flat.get("guardianName")),
              guardianFromList(flat.get("guardians")));
      if (!parent.isEmpty()) {
        flat.put("parentName", parent);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static String guardianFromList(Object guardiansObj) {
    if (!(guardiansObj instanceof List<?> list) || list.isEmpty()) {
      return "";
    }
    for (Object g : list) {
      if (!(g instanceof Map<?, ?> m)) {
        continue;
      }
      String name =
          firstNonBlank(str(m.get("fullName")), str(m.get("name")));
      if (!name.isEmpty()) {
        return name;
      }
    }
    return "";
  }

  private String studentDirectoryUrl(int page) {
    return properties.getIntegrations().getStudentBaseUrl()
        + "/api/student/directory/students?page="
        + page
        + "&size="
        + PAGE_SIZE;
  }

  private String studentRecordsUrl(int page) {
    return properties.getIntegrations().getStudentBaseUrl()
        + "/api/student/students?page="
        + page
        + "&size="
        + PAGE_SIZE;
  }

  private String staffDirectoryUrl(int page) {
    return properties.getIntegrations().getStaffBaseUrl()
        + "/api/staff/directory/staff?page="
        + page
        + "&size="
        + PAGE_SIZE;
  }

  private String staffRecordsUrl(int page) {
    return properties.getIntegrations().getStaffBaseUrl()
        + "/api/staff/staff?page="
        + page
        + "&size="
        + PAGE_SIZE;
  }

  private static void putIfBlank(Map<String, Object> flat, String key, String value) {
    if (blank(flat.get(key)) && value != null && !value.isBlank()) {
      flat.put(key, value);
    }
  }

  private static boolean blank(Object v) {
    return v == null || String.valueOf(v).trim().isEmpty();
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String firstNonBlank(String... values) {
    if (values == null) {
      return "";
    }
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.trim();
      }
    }
    return "";
  }
}
