package com.sugamflow.school.exam.integration;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.config.ExamProperties;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class StudentAccessClient {

  private static final Logger log = LoggerFactory.getLogger(StudentAccessClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final ExamProperties properties;

  public StudentAccessClient(RestClient.Builder restClientBuilder, ExamProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public AccessScope resolve(TenantScope scope) {
    if (scope == null) {
      return AccessScope.empty("ANONYMOUS");
    }
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return AccessScope.elevated();
    }
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
      Map<String, Object> data = unwrap(envelope);
      if (data == null) {
        return AccessScope.empty(PersonaRoles.normalize(scope.roleCode()));
      }
      boolean restricted = !Boolean.FALSE.equals(data.get("restricted"));
      if (!restricted) {
        return AccessScope.elevated();
      }
      return AccessScope.of(
          String.valueOf(data.getOrDefault("persona", scope.roleCode())),
          stringSet(data.get("studentIds")),
          stringSet(data.get("admissionNos")),
          stringSet(data.get("classSections")));
    } catch (Exception ex) {
      log.warn("access-scope lookup failed: {}", ex.getMessage());
      return AccessScope.empty(PersonaRoles.normalize(scope.roleCode()));
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> unwrap(Map<String, Object> envelope) {
    if (envelope == null) {
      return null;
    }
    Object data = envelope.get("data");
    if (data instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return envelope;
  }

  private static Collection<String> stringSet(Object raw) {
    if (!(raw instanceof Collection<?> c)) {
      return Set.of();
    }
    return c.stream().map(String::valueOf).toList();
  }
}
