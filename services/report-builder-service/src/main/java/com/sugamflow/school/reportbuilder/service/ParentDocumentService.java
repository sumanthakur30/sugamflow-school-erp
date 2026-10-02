package com.sugamflow.school.reportbuilder.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.reportbuilder.config.ReportProperties;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Parent download of their own child's ID card or transfer certificate. */
@Service
public class ParentDocumentService {

  private static final Logger log = LoggerFactory.getLogger(ParentDocumentService.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final Set<String> ALLOWED = Set.of("id_card", "transfer_certificate");

  private final ReportTemplateService templates;
  private final RestClient.Builder restClientBuilder;
  private final ReportProperties properties;

  public ParentDocumentService(
      ReportTemplateService templates,
      RestClient.Builder restClientBuilder,
      ReportProperties properties) {
    this.templates = templates;
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public Map<String, Object> download(String templateKey, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    if (!PersonaRoles.isParent(scope.roleCode()) && !PersonaRoles.isStudent(scope.roleCode())) {
      throw new IllegalStateException("Only a parent or student can download this document");
    }
    String key = templateKey == null ? "" : templateKey.trim().toLowerCase(Locale.ROOT);
    if (!ALLOWED.contains(key)) {
      throw new IllegalArgumentException("This document is not available for download");
    }
    String admission = admissionNo(body);
    if (admission == null) {
      throw new IllegalArgumentException("admissionNo is required");
    }
    AccessScope access = resolve(scope);
    if (!access.allowsAdmissionNo(admission)) {
      throw new IllegalStateException("This document is only available for your own child");
    }
    return templates.renderIssued(scope.organizationId(), key, body == null ? Map.of() : body);
  }

  private AccessScope resolve(TenantScope scope) {
    String base = properties.getIntegrations().getStudentBaseUrl();
    if (base == null || base.isBlank()) {
      return AccessScope.empty(PersonaRoles.normalize(scope.roleCode()));
    }
    String url = base.replaceAll("/$", "") + "/api/student/access-scope";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      Object data = envelope == null ? null : envelope.get("data");
      if (!(data instanceof Map<?, ?> map)) {
        return AccessScope.empty(PersonaRoles.normalize(scope.roleCode()));
      }
      Object persona = map.get("persona");
      return AccessScope.of(
          persona == null ? scope.roleCode() : String.valueOf(persona),
          stringSet(map.get("studentIds")),
          stringSet(map.get("admissionNos")),
          stringSet(map.get("classSections")));
    } catch (Exception ex) {
      log.warn("Parent access-scope lookup failed: {}", ex.getMessage());
      return AccessScope.empty(PersonaRoles.normalize(scope.roleCode()));
    }
  }

  @SuppressWarnings("unchecked")
  private static String admissionNo(Map<String, Object> body) {
    if (body == null) {
      return null;
    }
    Object direct = body.get("admissionNo");
    if (direct != null && !String.valueOf(direct).isBlank()) {
      return String.valueOf(direct).trim();
    }
    Object data = body.get("data");
    if (data instanceof Map<?, ?> map) {
      Object nested = map.get("admissionNo");
      if (nested != null && !String.valueOf(nested).isBlank()) {
        return String.valueOf(nested).trim();
      }
      Object student = map.get("student");
      if (student instanceof Map<?, ?> studentMap && studentMap.get("admissionNo") != null) {
        String value = String.valueOf(studentMap.get("admissionNo")).trim();
        return value.isBlank() ? null : value;
      }
    }
    return null;
  }

  private static Set<String> stringSet(Object raw) {
    Set<String> out = new LinkedHashSet<>();
    if (raw instanceof Collection<?> values) {
      for (Object value : values) {
        if (value != null && !String.valueOf(value).isBlank()) {
          out.add(String.valueOf(value));
        }
      }
    }
    return out;
  }
}
