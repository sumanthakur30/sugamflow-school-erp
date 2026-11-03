package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillableEnrollmentServiceTest {

  @Mock private StudentRecordRepository repository;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void countsOnlyTheCallerOrganization() {
    when(repository.countBillableActiveStudents("SCH-A", "ACTIVE")).thenReturn(800L);
    BillableEnrollmentService service = new BillableEnrollmentService(repository);
    TenantContext.set(new TenantScope("SCH-A", "main", null, "user", "ADMIN"));

    Map<String, Object> out = service.countForCurrentTenant();

    assertEquals(800L, out.get("billableCount"));
    assertEquals("SCH-A", out.get("organizationId"));
    assertEquals("ACTIVE_ON_RUN", out.get("policy"));
    verify(repository).countBillableActiveStudents("SCH-A", "ACTIVE");
  }
}
