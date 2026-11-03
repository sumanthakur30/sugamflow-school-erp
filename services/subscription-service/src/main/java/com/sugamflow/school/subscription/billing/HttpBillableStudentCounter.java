package com.sugamflow.school.subscription.billing;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.subscription.config.SchoolBillingProperties;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads student-service. Does not read fee-service. */
@Component
public class HttpBillableStudentCounter implements BillableStudentCounter {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final SchoolBillingProperties properties;

  public HttpBillableStudentCounter(RestClient.Builder restClientBuilder, SchoolBillingProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  @Override
  public int countActive(String organizationId) {
    String base = properties.getStudentBaseUrl();
    if (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    TenantScope scope = new TenantScope(organizationId, null, null, "school-billing", "SUPER_ADMIN");
    Map<String, Object> envelope =
        restClientBuilder
            .build()
            .get()
            .uri(base + "/api/student/billing/billable-count")
            .headers(headers -> TenantHeaders.apply(headers, scope))
            .retrieve()
            .body(MAP);
    if (envelope == null || !(envelope.get("data") instanceof Map<?, ?> data)) {
      throw new IllegalStateException("Student count response missing data for " + organizationId);
    }
    Object count = data.get("billableCount");
    if (!(count instanceof Number number)) {
      throw new IllegalStateException("Student count response missing billableCount for " + organizationId);
    }
    return Math.toIntExact(number.longValue());
  }
}
