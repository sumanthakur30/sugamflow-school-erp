package com.sugamflow.school.payroll.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.payroll.config.PayrollProperties;
import com.sugamflow.school.payroll.integration.ApprovedLeaveClient;
import com.sugamflow.school.payroll.integration.ConfigEngineClient;
import com.sugamflow.school.payroll.integration.NotificationDeliveryClient;
import com.sugamflow.school.payroll.integration.StaffAttendanceClient;
import com.sugamflow.school.payroll.persistence.repo.PayrollRecordRepository;
import com.sugamflow.school.payroll.web.PayrollException;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PayrollAccessTest {

  @Mock private PayrollRecordRepository repository;
  @Mock private ConfigEngineClient engines;
  @Mock private NotificationDeliveryClient notificationDelivery;
  @Mock private PayrollOpsService opsService;
  @Mock private StaffAttendanceClient staffAttendance;
  @Mock private ApprovedLeaveClient approvedLeave;

  private PayrollRecordService records;
  private PayrollReportService reports;

  @BeforeEach
  void setUp() {
    records =
        new PayrollRecordService(
            repository,
            engines,
            notificationDelivery,
            new PayrollProperties(),
            opsService,
            staffAttendance,
            approvedLeave);
    reports = new PayrollReportService(repository);
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void teacherCannotReadPayroll() {
    TenantContext.set(new TenantScope("SCH-A", "main", "2025-26", "teacher-a", "TEACHER"));
    when(engines.isFeatureEnabled(any(), eq(PayrollRecordService.FEATURE_PAYROLL))).thenReturn(true);

    PayrollException list = assertThrows(PayrollException.class, () -> records.list(0, 20));
    PayrollException one =
        assertThrows(PayrollException.class, () -> records.get(UUID.randomUUID()));
    PayrollException summary =
        assertThrows(PayrollException.class, () -> reports.salarySummary(Map.of()));

    assertEquals("FORBIDDEN", list.getCode());
    assertEquals("FORBIDDEN", one.getCode());
    assertEquals("FORBIDDEN", summary.getCode());
    verifyNoInteractions(repository);
  }

  @Test
  void accountantCanListThisSchoolsPayroll() {
    TenantContext.set(new TenantScope("SCH-A", "main", "2025-26", "accounts", "ACCOUNTANT"));
    when(engines.isFeatureEnabled(any(), eq(PayrollRecordService.FEATURE_PAYROLL))).thenReturn(true);
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
            eq("SCH-A"), eq("main"), eq("2025-26"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of()));

    var page = records.list(0, 20);

    assertEquals(0, page.items().size());
  }
}
