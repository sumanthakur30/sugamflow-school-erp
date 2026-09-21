package com.sugamflow.school.admission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.admission.config.AdmissionProperties;
import com.sugamflow.school.admission.integration.ConfigEngineClient;
import com.sugamflow.school.admission.integration.NotificationDeliveryClient;
import com.sugamflow.school.admission.integration.StudentEnrollmentClient;
import com.sugamflow.school.admission.persistence.entity.AdmissionApplicationEntity;
import com.sugamflow.school.admission.persistence.repo.AdmissionApplicationRepository;
import com.sugamflow.school.admission.web.AdmissionException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdmissionApplicationServiceTest {

  @Mock private AdmissionApplicationRepository repository;
  @Mock private ConfigEngineClient engines;
  @Mock private NotificationDeliveryClient notificationDelivery;
  @Mock private StudentEnrollmentClient studentEnrollment;
  @Mock private AdmissionProperties properties;

  @InjectMocks private AdmissionApplicationService service;

  private final UUID appId = UUID.randomUUID();

  @BeforeEach
  void setTenant() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "admin", "TEACHER"));
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void rejectsActionWhenAssigneeRoleDoesNotMatch() {
    AdmissionApplicationEntity entity = openApplication("RECEPTION");
    when(repository.findByIdAndOrganizationId(appId, "demo-school")).thenReturn(Optional.of(entity));
    when(engines.isFeatureEnabled(any(), eq("FEATURE_ADMISSION"))).thenReturn(true);

    AdmissionException ex =
        assertThrows(
            AdmissionException.class,
            () -> service.act(appId, Map.of("action", "APPROVE", "comment", "nope")));
    assertEquals("FORBIDDEN_ROLE", ex.getCode());
    verify(repository, never()).save(any());
  }

  @Test
  void elevatedRoleCanActOnAnyStep() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "owner", "SHOP_OWNER"));
    AdmissionApplicationEntity entity = openApplication("RECEPTION");
    when(repository.findByIdAndOrganizationId(appId, "demo-school")).thenReturn(Optional.of(entity));
    when(engines.isFeatureEnabled(any(), eq("FEATURE_ADMISSION"))).thenReturn(true);
    when(engines.getWorkflow(any(), eq("admission"))).thenReturn(simpleWorkflow());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(engines.previewNotification(any(), any(), any(), any())).thenReturn(Map.of());

    Map<String, Object> dto = service.act(appId, Map.of("action", "REQUEST_INFO", "comment", "docs"));
    assertEquals("INFO_REQUESTED", dto.get("status"));
    assertEquals("More information needed", dto.get("statusLabel"));
    assertTrue(((List<?>) dto.get("allowedActions")).contains("RESUME"));
  }

  @Test
  void resumeMovesInfoRequestedBackToInProgress() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "owner", "ADMIN"));
    AdmissionApplicationEntity entity = openApplication("PRINCIPAL");
    entity.setStatus("INFO_REQUESTED");
    when(repository.findByIdAndOrganizationId(appId, "demo-school")).thenReturn(Optional.of(entity));
    when(engines.isFeatureEnabled(any(), eq("FEATURE_ADMISSION"))).thenReturn(true);
    when(engines.getWorkflow(any(), eq("admission"))).thenReturn(simpleWorkflow());
    when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> dto = service.act(appId, Map.of("action", "RESUME"));
    assertEquals("IN_PROGRESS", dto.get("status"));
    assertEquals("Under review", dto.get("statusLabel"));
  }

  @Test
  void submitRejectsUncheckedMandatoryCheckbox() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "owner", "ADMIN"));
    when(engines.isFeatureEnabled(any(), eq("FEATURE_ADMISSION"))).thenReturn(true);
    when(engines.getModuleSettings(any(), eq("admission")))
        .thenReturn(Map.of("settings", Map.of("enabled", true, "formKey", "admission_form", "workflowKey", "admission")));
    when(engines.getForm(any(), eq("admission_form"))).thenReturn(admissionForm());

    AdmissionException ex =
        assertThrows(
            AdmissionException.class,
            () ->
                service.submit(
                    Map.of(
                        "answers",
                        Map.of(
                            "fullName", "Asha",
                            "age", 8,
                            "mobile", "9999900001",
                            "classApplied", "III",
                            "documentsComplete", false))));
    assertEquals("VALIDATION", ex.getCode());
    assertTrue(ex.getMessage().toLowerCase().contains("documents"));
  }

  @Test
  void detailDtoExposesFriendlyStatusAndCanAct() {
    TenantContext.set(new TenantScope("demo-school", "main", "2025-26", "owner", "ADMIN"));
    AdmissionApplicationEntity entity = openApplication("RECEPTION");
    when(repository.findByIdAndOrganizationId(appId, "demo-school")).thenReturn(Optional.of(entity));
    when(engines.isFeatureEnabled(any(), eq("FEATURE_ADMISSION"))).thenReturn(true);

    Map<String, Object> dto = service.get(appId);
    assertEquals("Under review", dto.get("statusLabel"));
    assertEquals(true, dto.get("canAct"));
    assertFalse((Boolean) dto.get("terminal"));
  }

  private AdmissionApplicationEntity openApplication(String assigneeRole) {
    AdmissionApplicationEntity entity = new AdmissionApplicationEntity();
    entity.setId(appId);
    entity.setOrganizationId("demo-school");
    entity.setBranchId("main");
    entity.setAcademicSessionId("2025-26");
    entity.setFormKey("admission_form");
    entity.setWorkflowKey("admission");
    entity.setStatus("IN_PROGRESS");
    entity.setCurrentStepSequence(1);
    entity.setCurrentStepName("Reception");
    entity.setAssigneeRole(assigneeRole);
    entity.setAnswers(new LinkedHashMap<>(Map.of("fullName", "Asha Verma")));
    entity.setHistory(new ArrayList<>());
    entity.setMatchedActions(new ArrayList<>());
    entity.setNotificationIntents(new ArrayList<>());
    entity.setDocuments(new ArrayList<>());
    entity.setCreatedBy("admin");
    entity.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    entity.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return entity;
  }

  private static Map<String, Object> simpleWorkflow() {
    return Map.of(
        "steps",
        List.of(
            Map.of("sequence", 1, "name", "Reception", "assignRole", "RECEPTION"),
            Map.of("sequence", 2, "name", "Completed", "assignRole", "SYSTEM")));
  }

  private static Map<String, Object> admissionForm() {
    return Map.of(
        "sections",
        List.of(
            Map.of(
                "fields",
                List.of(
                    Map.of("key", "fullName", "label", "Full Name", "type", "TEXTBOX", "mandatory", true),
                    Map.of("key", "age", "label", "Age", "type", "NUMBER", "mandatory", true),
                    Map.of("key", "mobile", "label", "Mobile", "type", "PHONE", "mandatory", true),
                    Map.of("key", "classApplied", "label", "Class", "type", "TEXTBOX", "mandatory", true),
                    Map.of(
                        "key",
                        "documentsComplete",
                        "label",
                        "Documents Complete",
                        "type",
                        "CHECKBOX",
                        "mandatory",
                        true)))));
  }
}
