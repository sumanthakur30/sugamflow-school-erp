package com.sugamflow.school.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sugamflow.school.academic.persistence.entity.AcademicClassEntity;
import com.sugamflow.school.academic.persistence.repo.AcademicClassRepository;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.SubjectRepository;
import com.sugamflow.school.academic.persistence.repo.TeachingAssignmentRepository;
import com.sugamflow.school.academic.web.AcademicException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcademicStructureServiceTest {

  @Mock private AcademicClassRepository classes;
  @Mock private ClassSectionRepository sections;
  @Mock private SubjectRepository subjects;
  @Mock private TeachingAssignmentRepository assignments;
  @InjectMocks private AcademicStructureService service;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void parentCannotCreateClass() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "parent_NAT-01", "PARENT"));
    assertThrows(
        SecurityException.class, () -> service.createClass(Map.of("name", "Grade 1")));
  }

  @Test
  void staffCanCreateClass() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "admin_NAT-01", "ADMIN"));
    when(classes.save(any(AcademicClassEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Map<String, Object> created = service.createClass(Map.of("name", "Grade 1", "sequenceNo", 1));
    assertEquals("Grade 1", created.get("name"));
    assertEquals(1, created.get("sequenceNo"));
  }

  @Test
  void catalogKeepsSchoolNameAndHidesInactiveStep() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "admin_NAT-01", "ADMIN"));
    AcademicClassEntity grade7 = cls("Grade 7", "ACTIVE", 7);
    AcademicClassEntity nursery = cls("Nursery", "INACTIVE", 1);
    when(classes.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderBySequenceNoAscNameAsc(
            "NAT-01", "main", "2025-26"))
        .thenReturn(List.of(nursery, grade7));

    List<String> names =
        service.classCatalog().stream().map(row -> String.valueOf(row.get("name"))).toList();

    assertTrue(names.contains("Nursery") == false);
    assertTrue(names.contains("Grade 7"));
    assertFalse(names.contains("Class 7"));
    assertTrue(names.indexOf("LKG") < names.indexOf("Grade 7"));
    assertTrue(names.contains("Class 12"));
  }

  @Test
  void duplicateClassNameIsRejected() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "admin_NAT-01", "ADMIN"));
    when(classes.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderBySequenceNoAscNameAsc(
            "NAT-01", "main", "2025-26"))
        .thenReturn(List.of(cls("Grade 7", "ACTIVE", 7)));

    AcademicException error =
        assertThrows(AcademicException.class, () -> service.createClass(Map.of("name", "Class 7")));
    assertEquals("Class already exists: Grade 7", error.getMessage());
  }

  private static AcademicClassEntity cls(String name, String status, int sequence) {
    AcademicClassEntity entity = new AcademicClassEntity();
    entity.setId(UUID.randomUUID());
    entity.setName(name);
    entity.setStatus(status);
    entity.setSequenceNo(sequence);
    return entity;
  }
}
