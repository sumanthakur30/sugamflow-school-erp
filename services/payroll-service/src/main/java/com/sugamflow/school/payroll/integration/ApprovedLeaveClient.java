package com.sugamflow.school.payroll.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.payroll.config.PayrollProperties;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Reads approved campus-desk leave so a payslip can include days taken that month. */
@Component
public class ApprovedLeaveClient {

  private static final Logger log = LoggerFactory.getLogger(ApprovedLeaveClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final PayrollProperties properties;

  public ApprovedLeaveClient(RestClient.Builder restClientBuilder, PayrollProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public int approvedDays(TenantScope scope, String yearMonth, String employeeId, String employeeName) {
    String base = properties.getIntegrations().getStudentBaseUrl();
    if (base == null || base.isBlank() || yearMonth == null || yearMonth.isBlank()) {
      return 0;
    }
    String url = base.replaceAll("/$", "") + "/api/student/desk/LEAVE";
    try {
      Map<String, Object> envelope =
          restClientBuilder
              .build()
              .get()
              .uri(url)
              .headers(h -> TenantHeaders.apply(h, scope))
              .retrieve()
              .body(MAP_TYPE);
      if (envelope == null || !(envelope.get("data") instanceof List<?> rows)) {
        return 0;
      }
      YearMonth month = YearMonth.parse(yearMonth);
      int days = 0;
      for (Object item : rows) {
        if (!(item instanceof Map<?, ?> row)) {
          continue;
        }
        if (!"APPROVED".equalsIgnoreCase(String.valueOf(row.get("status")))) {
          continue;
        }
        if (!matchesEmployee(row, employeeId, employeeName)) {
          continue;
        }
        if (!inMonth(String.valueOf(row.get("createdAt")), month)) {
          continue;
        }
        days += daysOf(row.get("payload"));
      }
      return days;
    } catch (Exception ex) {
      log.warn("Approved leave lookup failed for {}: {}", yearMonth, ex.getMessage());
      return 0;
    }
  }

  private static boolean matchesEmployee(Map<?, ?> row, String employeeId, String employeeName) {
    String ref = text(row.get("subjectRef"));
    String name = text(row.get("subjectName"));
    if (employeeId != null && !employeeId.isBlank() && employeeId.equalsIgnoreCase(ref)) {
      return true;
    }
    if (employeeName != null && !employeeName.isBlank() && employeeName.equalsIgnoreCase(name)) {
      return true;
    }
    return (employeeId == null || employeeId.isBlank())
        && (employeeName == null || employeeName.isBlank())
        && "STAFF".equalsIgnoreCase(text(row.get("subjectType")));
  }

  private static boolean inMonth(String createdAt, YearMonth month) {
    return createdAt != null && createdAt.startsWith(month.toString());
  }

  @SuppressWarnings("unchecked")
  private static int daysOf(Object payload) {
    if (!(payload instanceof Map<?, ?> map)) {
      return 1;
    }
    Object raw = map.get("days");
    if (raw instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    return 1;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
