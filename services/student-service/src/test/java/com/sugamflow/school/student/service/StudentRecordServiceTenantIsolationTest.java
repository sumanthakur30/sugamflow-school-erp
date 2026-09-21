package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.config.StudentProperties;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudentRecordServiceTenantIsolationTest {

  private static final String ORG_A = "NAT-01";
  private static final String ORG_B = "demo-school";

  @Mock private StudentRecordRepository repository;
  @Mock private com.sugamflow.school.student.persistence.repo.StudentFieldAuditRepository fieldAuditRepository;
  @Mock private ConfigEngineClient engines;
  @Mock private RelationshipAccessService relationshipAccess;
  @Mock private com.sugamflow.school.student.integration.DomainSnapshotClient domainSnapshots;

  private StudentRecordService service;

  @BeforeEach
  void setUp() {
    service =
        new StudentRecordService(
            repository,
            fieldAuditRepository,
            engines,
            new StudentProperties(),
            relationshipAccess,
            domainSnapshots);
    when(relationshipAccess.resolve(any())).thenReturn(AccessScope.elevated());
  }

  @AfterEach
  void cleanup() {
    TenantContext.clear();
  }

  private void bindTenant(String org) {
    TenantContext.set(new TenantScope(org, "main", "2025-26", "user-1", "SHOP_OWNER"));
    when(engines.isFeatureEnabled(any(), eq(StudentRecordService.FEATURE_STUDENT_MASTER)))
        .thenReturn(true);
  }

  private static StudentRecordEntity entity(String org) {
    StudentRecordEntity e = new StudentRecordEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId(org);
    e.setBranchId("main");
    e.setAcademicSessionId("2025-26");
    e.setStatus("ACTIVE");
    e.setAnswers(Map.of("fullName", "Test Student"));
    e.setHistory(List.of());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    return e;
  }

  @Test
  void listQueriesOnlyTheBoundOrganization() {
    bindTenant(ORG_A);
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            eq(ORG_A), eq("main"), eq("2025-26"), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity(ORG_A))));

    var result = service.list(0, 50);

    assertEquals(1, result.items().size());
    assertEquals(ORG_A, result.items().get(0).get("organizationId"));
    verify(repository, never()).findAll();
    verify(repository, never())
        .findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            eq(ORG_B), anyString(), anyString(), any(Pageable.class));
  }

  @Test
  void getScopesLookupByIdAndOrganization() {
    bindTenant(ORG_A);
    StudentRecordEntity e = entity(ORG_A);
    when(repository.findByIdAndOrganizationId(e.getId(), ORG_A)).thenReturn(Optional.of(e));

    var dto = service.get(e.getId());

    assertEquals(ORG_A, dto.get("organizationId"));
    verify(repository).findByIdAndOrganizationId(e.getId(), ORG_A);
    verify(repository, never()).findById(any());
  }

  @Test
  void getStudentOfAnotherOrgIsNotFound() {
    bindTenant(ORG_A);
    UUID foreignStudentId = UUID.randomUUID();
    when(repository.findByIdAndOrganizationId(foreignStudentId, ORG_A))
        .thenReturn(Optional.empty());

    StudentException ex = assertThrows(StudentException.class, () -> service.get(foreignStudentId));

    assertEquals("NOT_FOUND", ex.getCode());
  }

  @Test
  void enrollStampsOrganizationFromContextNotFromBody() {
    bindTenant(ORG_A);
    UUID applicationId = UUID.randomUUID();
    when(repository.findByOrganizationIdAndSourceApplicationIdAndDeletedAtIsNull(ORG_A, applicationId))
        .thenReturn(Optional.empty());
    when(engines.getModuleSettings(any(), anyString())).thenReturn(Map.of());
    when(engines.getForm(any(), anyString()))
        .thenReturn(
            Map.of(
                "sections",
                List.of(
                    Map.of(
                        "fields",
                        List.of(Map.of("key", "fullName", "type", "TEXTBOX"))))));
    when(repository.save(any(StudentRecordEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    var dto =
        service.enrollFromAdmission(
            Map.of(
                "applicationId", applicationId.toString(),
                "organizationId", ORG_B,
                "answers", Map.of("fullName", "Mallory")));

    assertEquals(ORG_A, dto.get("organizationId"));
  }

  @Test
  void requireTenantContextForAllReads() {
    assertThrows(IllegalStateException.class, () -> service.list(0, 50));
    assertThrows(IllegalStateException.class, () -> service.get(UUID.randomUUID()));
  }

  @Test
  void parentCannotSeeUnlinkedStudent() {
    TenantContext.set(new TenantScope(ORG_A, "main", "2025-26", "parent1", "PARENT"));
    when(engines.isFeatureEnabled(any(), eq(StudentRecordService.FEATURE_STUDENT_MASTER)))
        .thenReturn(true);
    StudentRecordEntity e = entity(ORG_A);
    when(repository.findByIdAndOrganizationId(e.getId(), ORG_A)).thenReturn(Optional.of(e));
    when(relationshipAccess.resolve(any())).thenReturn(AccessScope.empty("PARENT"));

    StudentException ex = assertThrows(StudentException.class, () -> service.get(e.getId()));
    assertEquals("NOT_FOUND", ex.getCode());
  }

  @Test
  void parentCannotEnroll() {
    TenantContext.set(new TenantScope(ORG_A, "main", "2025-26", "parent1", "PARENT"));
    when(engines.isFeatureEnabled(any(), eq(StudentRecordService.FEATURE_STUDENT_MASTER)))
        .thenReturn(true);

    StudentException ex =
        assertThrows(
            StudentException.class,
            () ->
                service.enrollFromAdmission(
                    Map.of("applicationId", UUID.randomUUID().toString())));
    assertEquals("FORBIDDEN", ex.getCode());
  }
}
