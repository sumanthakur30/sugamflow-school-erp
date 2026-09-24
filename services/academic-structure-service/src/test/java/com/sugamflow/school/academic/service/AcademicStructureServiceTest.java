package com.sugamflow.school.academic.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sugamflow.school.academic.persistence.entity.AcademicClassEntity;
import com.sugamflow.school.academic.persistence.repo.AcademicClassRepository;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.SubjectRepository;
import com.sugamflow.school.academic.persistence.repo.TeachingAssignmentRepository;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Map;
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
}
