package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.AcademicClient;
import com.sugamflow.school.student.integration.StaffClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RelationshipAccessServiceTest {

  @Mock private StudentRecordRepository repository;
  @Mock private StaffClient staffClient;
  @Mock private AcademicClient academicClient;
  @InjectMocks private RelationshipAccessService service;

  private static StudentRecordEntity student(
      String admissionNo, String classApplied, List<Map<String, Object>> guardians) {
    StudentRecordEntity e = new StudentRecordEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId("NAT-01");
    e.setBranchId("main");
    e.setAcademicSessionId("2025-26");
    e.setAdmissionNo(admissionNo);
    e.setAnswers(
        Map.of(
            "fullName", "Child",
            "classApplied", classApplied,
            "guardians", guardians));
    e.setHistory(List.of());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    return e;
  }

  @Test
  void elevatedRolesAreUnrestricted() {
    AccessScope scope =
        service.resolve(new TenantScope("NAT-01", "main", "2025-26", "owner", "SHOP_OWNER"));
    assertFalse(scope.restricted());
  }

  @Test
  void parentSeesOnlyLinkedChildren() {
    StudentRecordEntity mine =
        student(
            "ADM-1",
            "8-A",
            List.of(Map.of("fullName", "Dad", "userId", "parent_NAT-01", "relation", "Father")));
    StudentRecordEntity other =
        student(
            "ADM-2",
            "8-A",
            List.of(Map.of("fullName", "Other", "userId", "someone_else", "relation", "Mother")));
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            any(), any(), any()))
        .thenReturn(List.of(mine, other));

    AccessScope scope =
        service.resolve(new TenantScope("NAT-01", "main", "2025-26", "parent_NAT-01", "PARENT"));

    assertTrue(scope.restricted());
    assertEquals(1, scope.studentIds().size());
    assertTrue(scope.allowsStudentId(mine.getId().toString()));
    assertFalse(scope.allowsStudentId(other.getId().toString()));
    assertTrue(scope.allowsAdmissionNo("ADM-1"));
    assertFalse(scope.allowsAdmissionNo("ADM-2"));
  }

  @Test
  void parentWithoutLinksGetsEmptyScope() {
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            any(), any(), any()))
        .thenReturn(List.of());
    AccessScope scope =
        service.resolve(new TenantScope("NAT-01", "main", "2025-26", "orphan_parent", "PARENT"));
    assertTrue(scope.restricted());
    assertTrue(scope.studentIds().isEmpty());
  }

  @Test
  void teacherSeesAssignedClassOnly() {
    StudentRecordEntity inClass =
        student("ADM-1", "Grade 8-A", List.of());
    StudentRecordEntity outClass =
        student("ADM-2", "Grade 9-B", List.of());
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            any(), any(), any()))
        .thenReturn(List.of(inClass, outClass));
    when(academicClient.teacherClassLabels(any(), anyString())).thenReturn(Set.of());
    when(staffClient.listStaff(any(), anyInt()))
        .thenReturn(
            List.of(
                Map.of(
                    "answers",
                    Map.of(
                        "authUsername", "teacher_NAT-01",
                        "assignedClassSections", "Grade 8-A, Grade 8-B"))));

    AccessScope scope =
        service.resolve(new TenantScope("NAT-01", "main", "2025-26", "teacher_NAT-01", "TEACHER"));

    assertTrue(scope.allowsStudentId(inClass.getId().toString()));
    assertFalse(scope.allowsStudentId(outClass.getId().toString()));
    assertTrue(scope.allowsClassSection("Grade 8-A"));
  }

  @Test
  void teacherSeesAcademicStructureLabels() {
    StudentRecordEntity inClass = student("ADM-1", "8-A", List.of());
    StudentRecordEntity outClass = student("ADM-2", "9-B", List.of());
    when(repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
            any(), any(), any()))
        .thenReturn(List.of(inClass, outClass));
    when(academicClient.teacherClassLabels(any(), anyString())).thenReturn(Set.of("8-A"));
    when(staffClient.listStaff(any(), anyInt())).thenReturn(List.of());

    AccessScope scope =
        service.resolve(new TenantScope("NAT-01", "main", "2025-26", "teacher_NAT-01", "TEACHER"));

    assertTrue(scope.allowsStudentId(inClass.getId().toString()));
    assertFalse(scope.allowsStudentId(outClass.getId().toString()));
  }

  @Test
  void identityMatchesScopedAndLocalUsernames() {
    assertTrue(RelationshipAccessService.identityMatches("parent", "parent_NAT-01"));
    assertTrue(RelationshipAccessService.identityMatches("parent_NAT-01", "parent"));
    assertFalse(RelationshipAccessService.identityMatches("other", "parent_NAT-01"));
  }
}
