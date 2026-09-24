package com.sugamflow.school.fee.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.config.FeeProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Resolves live Student Master identity for fee collection detail screens. */
@Component
public class StudentProfileClient {

  private static final Logger log = LoggerFactory.getLogger(StudentProfileClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final FeeProperties properties;

  public StudentProfileClient(RestClient.Builder restClientBuilder, FeeProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> identityByAdmissionNo(TenantScope scope, String admissionNo) {
    if (scope == null || admissionNo == null || admissionNo.isBlank()) {
      return Map.of();
    }
    String base = properties.getIntegrations().getStudentBaseUrl();
    if (base == null || base.isBlank()) {
      return Map.of();
    }
    String encoded = URLEncoder.encode(admissionNo.trim(), StandardCharsets.UTF_8);
    String url =
        base.replaceAll("/$", "") + "/api/student/students/by-admission/" + encoded + "/identity";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      Map<String, Object> data = unwrap(envelope);
      return data != null ? data : Map.of();
    } catch (Exception ex) {
      log.debug("student identity lookup failed for {}: {}", admissionNo, ex.getMessage());
      return Map.of();
    }
  }

  /** Full student record (includes guardians) for due-reminder fan-out. */
  public Map<String, Object> byAdmissionNo(TenantScope scope, String admissionNo) {
    if (scope == null || admissionNo == null || admissionNo.isBlank()) {
      return Map.of();
    }
    String base = properties.getIntegrations().getStudentBaseUrl();
    if (base == null || base.isBlank()) {
      return Map.of();
    }
    String encoded = URLEncoder.encode(admissionNo.trim(), StandardCharsets.UTF_8);
    String url = base.replaceAll("/$", "") + "/api/student/students/by-admission/" + encoded;
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      Map<String, Object> data = unwrap(envelope);
      return data != null ? data : Map.of();
    } catch (Exception ex) {
      log.debug("student record lookup failed for {}: {}", admissionNo, ex.getMessage());
      return Map.of();
    }
  }

  /** Directory search (classSection / q / page size). Returns item maps or empty. */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> searchDirectory(TenantScope scope, Map<String, String> params) {
    if (scope == null) {
      return List.of();
    }
    String base = properties.getIntegrations().getStudentBaseUrl();
    if (base == null || base.isBlank()) {
      return List.of();
    }
    StringBuilder url = new StringBuilder(base.replaceAll("/$", "")).append("/api/student/students?");
    boolean first = true;
    for (Map.Entry<String, String> e : params.entrySet()) {
      if (e.getValue() == null || e.getValue().isBlank()) continue;
      if (!first) url.append('&');
      first = false;
      url.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
    }
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url.toString())
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      Map<String, Object> data = unwrap(envelope);
      if (data == null) return List.of();
      Object items = data.get("items");
      if (items == null) items = data.get("content");
      if (!(items instanceof List<?> list)) return List.of();
      List<Map<String, Object>> out = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          out.add(new LinkedHashMap<>((Map<String, Object>) m));
        }
      }
      return out;
    } catch (Exception ex) {
      log.debug("student directory search failed: {}", ex.getMessage());
      return List.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrap(Map<String, Object> envelope) {
    if (envelope == null) {
      return null;
    }
    Object data = envelope.get("data");
    if (data instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return envelope;
  }
}
