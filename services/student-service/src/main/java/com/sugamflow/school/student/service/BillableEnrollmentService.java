package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Count used by SugamFlow platform billing. Does not read school fee collections. */
@Service
public class BillableEnrollmentService {

  public static final String POLICY = "ACTIVE_ON_RUN";

  private final StudentRecordRepository repository;

  public BillableEnrollmentService(StudentRecordRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> countForCurrentTenant() {
    TenantScope scope = TenantContext.require();
    String organizationId = scope.organizationId();
    if (organizationId == null || organizationId.isBlank()) {
      throw new StudentException("VALIDATION", "Organization is required");
    }
    return countForOrganization(organizationId.trim());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> countForOrganization(String organizationId) {
    long count =
        repository.countBillableActiveStudents(
            organizationId, BillableEnrollmentPolicy.BILLABLE_STATUS);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", organizationId);
    out.put("policy", POLICY);
    out.put("billableStatus", BillableEnrollmentPolicy.BILLABLE_STATUS);
    out.put("billableCount", count);
    out.put("countedAt", Instant.now().toString());
    out.put(
        "note",
        "Distinct active student records observed now. This is not a historical enrollment snapshot and is not a fee-collection total.");
    return out;
  }
}
