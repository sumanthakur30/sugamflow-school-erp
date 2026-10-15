package com.sugamflow.school.payroll.integration;

import com.sugamflow.school.common.tenant.TenantHeaders;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.payroll.config.PayrollProperties;
import com.sugamflow.school.payroll.web.PayrollException;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Asks attendance-service whether a payroll month has a submitted staff sheet. */
@Component
public class StaffAttendanceClient {

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  private final RestClient.Builder restClientBuilder;
  private final PayrollProperties properties;

  public StaffAttendanceClient(RestClient.Builder restClientBuilder, PayrollProperties properties) {
    this.restClientBuilder = restClientBuilder;
    this.properties = properties;
  }

  public boolean isMonthSubmitted(TenantScope scope, String yearMonth) {
    String base = properties.getIntegrations().getAttendanceBaseUrl();
    if (base == null || base.isBlank()) {
      throw new PayrollException(
          "ATTENDANCE_REQUIRED", "Staff attendance service is not configured");
    }
    String url = base.replaceAll("/$", "") + "/api/attendance/staff/months/" + yearMonth;
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
      if (!(data instanceof Map<?, ?> sheet)) {
        return false;
      }
      Object submitted = sheet.get("submitted");
      if (submitted instanceof Boolean flag) {
        return flag;
      }
      return "SUBMITTED".equalsIgnoreCase(String.valueOf(sheet.get("status")));
    } catch (PayrollException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PayrollException(
          "ATTENDANCE_REQUIRED",
          "Staff attendance for " + yearMonth + " could not be confirmed");
    }
  }
}
