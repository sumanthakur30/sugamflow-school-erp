package com.sugamflow.school.academic.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.sugamflow.school.academic.persistence.entity.ClassSectionEntity;
import com.sugamflow.school.academic.persistence.entity.TeachingAssignmentEntity;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.TeachingAssignmentRepository;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherScopeServiceTest {

  @Mock private TeachingAssignmentRepository assignments;
  @Mock private ClassSectionRepository sections;
  @InjectMocks private TeacherScopeService service;

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private static ClassSectionEntity section(
      UUID id, String name, String code, String label, String classTeacher) {
    ClassSectionEntity s = new ClassSectionEntity();
    s.setId(id);
    s.setOrganizationId("NAT-01");
    s.setName(name);
    s.setCode(code);
    s.setStudentLabel(label);
    s.setClassTeacherUsername(classTeacher);
    return s;
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolvesLabelsFromAssignmentsAndClassTeacher() {
    TenantContext.set(new TenantScope("NAT-01", "main", "2025-26", "teacher_NAT-01", "TEACHER"));

    UUID taught = UUID.randomUUID();
    UUID owned = UUID.randomUUID();
    UUID unrelated = UUID.randomUUID();

    TeachingAssignmentEntity a = new TeachingAssignmentEntity();
    a.setSectionId(taught);
    when(assignments.findByOrganizationIdAndTeacherUsername(eq("NAT-01"), any()))
        .thenReturn(List.of(a));

    when(sections.findByOrganizationIdOrderByNameAsc("NAT-01"))
        .thenReturn(
            List.of(
                section(taught, "Grade 8-A", "8A", "Grade 8-A", null),
                section(owned, "Grade 9-B", "9B", "Grade 9-B", "teacher_NAT-01"),
                section(unrelated, "Grade 10-C", "10C", "Grade 10-C", "someone")));

    Map<String, Object> out = service.resolve("teacher_NAT-01");

    Collection<String> labels = (Collection<String>) out.get("studentLabels");
    Set<String> ids = (Set<String>) out.get("sectionIds");
    assertTrue(labels.contains("Grade 8-A"));
    assertTrue(labels.contains("Grade 9-B"));
    assertTrue(ids.contains(taught.toString()));
    assertTrue(ids.contains(owned.toString()));
    assertTrue(!ids.contains(unrelated.toString()));
  }
}
