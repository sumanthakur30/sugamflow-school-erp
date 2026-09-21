package com.sugamflow.school.academic.service;

import com.sugamflow.school.academic.persistence.entity.ClassSectionEntity;
import com.sugamflow.school.academic.persistence.entity.TeachingAssignmentEntity;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.TeachingAssignmentRepository;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the class/section labels a teacher is responsible for (as class teacher or via a
 * teaching assignment). Consumed by student-service RBAC to scope teacher visibility to real
 * academic entities rather than free-text staff answers.
 */
@Service
public class TeacherScopeService {

  private final TeachingAssignmentRepository assignments;
  private final ClassSectionRepository sections;

  public TeacherScopeService(
      TeachingAssignmentRepository assignments, ClassSectionRepository sections) {
    this.assignments = assignments;
    this.sections = sections;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> resolve(String username) {
    TenantScope scope = TenantContext.require();
    String teacher = (username == null || username.isBlank()) ? scope.userId() : username.trim();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("organizationId", scope.organizationId());
    out.put("teacherUsername", teacher);

    Set<String> sectionIds = new LinkedHashSet<>();
    Set<String> studentLabels = new LinkedHashSet<>();
    Set<String> sectionCodes = new LinkedHashSet<>();
    Set<String> sectionNames = new LinkedHashSet<>();

    if (teacher == null || teacher.isBlank()) {
      out.put("sectionIds", sectionIds);
      out.put("studentLabels", studentLabels);
      out.put("sectionCodes", sectionCodes);
      out.put("sectionNames", sectionNames);
      return out;
    }

    Set<UUID> matchedSectionIds = new LinkedHashSet<>();
    for (TeachingAssignmentEntity a :
        assignments.findByOrganizationIdAndTeacherUsername(scope.organizationId(), teacher)) {
      if (a.getSectionId() != null) {
        matchedSectionIds.add(a.getSectionId());
      }
    }

    List<ClassSectionEntity> orgSections =
        sections.findByOrganizationIdOrderByNameAsc(scope.organizationId());
    for (ClassSectionEntity s : orgSections) {
      boolean isClassTeacher =
          s.getClassTeacherUsername() != null
              && s.getClassTeacherUsername().equalsIgnoreCase(teacher);
      if (isClassTeacher || matchedSectionIds.contains(s.getId())) {
        sectionIds.add(s.getId().toString());
        addIfPresent(studentLabels, s.getStudentLabel());
        addIfPresent(sectionCodes, s.getCode());
        addIfPresent(sectionNames, s.getName());
      }
    }

    out.put("sectionIds", sectionIds);
    out.put("studentLabels", studentLabels);
    out.put("sectionCodes", sectionCodes);
    out.put("sectionNames", sectionNames);
    return out;
  }

  private static void addIfPresent(Set<String> into, String value) {
    if (value != null && !value.isBlank()) {
      into.add(value.trim());
    }
  }
}
