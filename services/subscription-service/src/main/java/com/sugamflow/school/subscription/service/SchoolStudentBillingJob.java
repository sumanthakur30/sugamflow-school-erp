package com.sugamflow.school.subscription.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Calls {@link SchoolStudentBillingService#billOrganization} through the Spring proxy. */
@Service
public class SchoolStudentBillingJob {

  private final SchoolStudentBillingService billing;

  public SchoolStudentBillingJob(SchoolStudentBillingService billing) {
    this.billing = billing;
  }

  public List<Map<String, Object>> runPeriod(YearMonth period) {
    List<Map<String, Object>> results = new ArrayList<>();
    for (String organizationId : billing.activeOrganizationIds()) {
      results.add(billing.billOrganization(organizationId, period));
    }
    return results;
  }
}
