package com.sugamflow.school.student.service;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.AcademicClient;
import com.sugamflow.school.student.integration.StaffClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves which students a PARENT / TEACHER may see. Fail-closed: restricted personas with no
 * links get an empty scope.
 */
@Service
public class RelationshipAccessService {

  public static final String GUARDIANS_KEY = "guardians";

  private final StudentRecordRepository repository;
  private final StaffClient staffClient;
  private final AcademicClient academicClient;

  public RelationshipAccessService(
      StudentRecordRepository repository,
      StaffClient staffClient,
      AcademicClient academicClient) {
    this.repository = repository;
    this.staffClient = staffClient;
    this.academicClient = academicClient;
  }

  @Transactional(readOnly = true)
  public AccessScope resolve(TenantScope scope) {
    if (scope == null) {
      return AccessScope.empty("ANONYMOUS");
    }
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return AccessScope.elevated();
    }
    if (PersonaRoles.isParent(scope.roleCode())) {
      return resolveParent(scope);
    }
    if (PersonaRoles.isTeacher(scope.roleCode())) {
      return resolveTeacher(scope);
    }
    // STUDENT persona: no self-link model yet — fail closed.
    return AccessScope.empty(PersonaRoles.normalize(scope.roleCode()));
  }

  private AccessScope resolveParent(TenantScope scope) {
    String user = scope.userId();
    if (user == null || user.isBlank()) {
      return AccessScope.empty("PARENT");
    }
    Set<String> studentIds = new LinkedHashSet<>();
    Set<String> admissionNos = new LinkedHashSet<>();
    Set<String> classSections = new LinkedHashSet<>();

    for (StudentRecordEntity e : loadOrgStudents(scope)) {
      if (!guardianMatches(e.getAnswers(), user)) {
        continue;
      }
      if (e.getId() != null) {
        studentIds.add(e.getId().toString());
      }
      if (e.getAdmissionNo() != null && !e.getAdmissionNo().isBlank()) {
        admissionNos.add(e.getAdmissionNo());
      }
      addClass(classSections, e.getAnswers());
    }
    return AccessScope.of("PARENT", studentIds, admissionNos, classSections);
  }

  private AccessScope resolveTeacher(TenantScope scope) {
    String user = scope.userId();
    if (user == null || user.isBlank()) {
      return AccessScope.empty("TEACHER");
    }
    Set<String> assignedClasses = findAssignedClasses(scope, user);
    if (assignedClasses.isEmpty()) {
      return AccessScope.empty("TEACHER");
    }

    Set<String> studentIds = new LinkedHashSet<>();
    Set<String> admissionNos = new LinkedHashSet<>();
    Set<String> classSections = new LinkedHashSet<>(assignedClasses);

    for (StudentRecordEntity e : loadOrgStudents(scope)) {
      String cls = classOf(e.getAnswers());
      if (cls == null || !assignedClasses.contains(cls.toLowerCase(Locale.ROOT))) {
        continue;
      }
      if (e.getId() != null) {
        studentIds.add(e.getId().toString());
      }
      if (e.getAdmissionNo() != null && !e.getAdmissionNo().isBlank()) {
        admissionNos.add(e.getAdmissionNo());
      }
    }
    return AccessScope.of("TEACHER", studentIds, admissionNos, classSections);
  }

  private Set<String> findAssignedClasses(TenantScope scope, String user) {
    Set<String> out = new LinkedHashSet<>();
    // Primary source: real academic entities (teaching assignments + class-teacher links).
    for (String label : academicClient.teacherClassLabels(scope, user)) {
      if (label != null && !label.isBlank()) {
        out.add(label.trim().toLowerCase(Locale.ROOT));
      }
    }
    // Fallback: legacy free-text staff answers, so scoping keeps working before the academic
    // structure is populated.
    for (Map<String, Object> staff : staffClient.listStaff(scope, 500)) {
      Map<String, Object> answers = answersOf(staff);
      if (!identityMatches(asString(answers.get("authUsername")), user)
          && !identityMatches(asString(staff.get("authUsername")), user)) {
        continue;
      }
      out.addAll(classList(answers.get("assignedClassSections")));
      out.addAll(classList(answers.get("classSections")));
      String single = asString(answers.get("classSection"));
      if (single != null && !single.isBlank()) {
        out.add(single.toLowerCase(Locale.ROOT));
      }
    }
    return out;
  }

  private List<StudentRecordEntity> loadOrgStudents(TenantScope scope) {
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      return repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return repository.findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(scope.organizationId());
  }

  @SuppressWarnings("unchecked")
  static boolean guardianMatches(Map<String, Object> answers, String userId) {
    if (answers == null || userId == null || userId.isBlank()) {
      return false;
    }
    Object raw = answers.get(GUARDIANS_KEY);
    if (!(raw instanceof List<?> list)) {
      return false;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> g)) {
        continue;
      }
      if (identityMatches(asString(g.get("userId")), userId)
          || identityMatches(asString(g.get("authUsername")), userId)
          || identityMatches(asString(g.get("username")), userId)) {
        return true;
      }
    }
    return false;
  }

  /** Match full scoped username or bare local part (before last underscore shop suffix). */
  static boolean identityMatches(String candidate, String userId) {
    if (candidate == null || candidate.isBlank() || userId == null || userId.isBlank()) {
      return false;
    }
    String a = candidate.trim();
    String b = userId.trim();
    if (a.equalsIgnoreCase(b)) {
      return true;
    }
    String aLocal = localPart(a);
    String bLocal = localPart(b);
    return aLocal.equalsIgnoreCase(b) || bLocal.equalsIgnoreCase(a) || aLocal.equalsIgnoreCase(bLocal);
  }

  private static String localPart(String username) {
    int idx = username.lastIndexOf('_');
    if (idx <= 0) {
      return username;
    }
    // Prefer strip of shop suffix when present (admin_NAT-01 -> admin)
    return username.substring(0, idx);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> answersOf(Map<String, Object> staff) {
    Object answers = staff.get("answers");
    if (answers instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return staff;
  }

  private static Collection<String> classList(Object raw) {
    Set<String> out = new LinkedHashSet<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item != null && !String.valueOf(item).isBlank()) {
          out.add(String.valueOf(item).trim().toLowerCase(Locale.ROOT));
        }
      }
    } else if (raw instanceof String s && !s.isBlank()) {
      for (String part : s.split("[,;|]")) {
        if (!part.isBlank()) {
          out.add(part.trim().toLowerCase(Locale.ROOT));
        }
      }
    }
    return out;
  }

  private static void addClass(Set<String> into, Map<String, Object> answers) {
    String cls = classOf(answers);
    if (cls != null) {
      into.add(cls.toLowerCase(Locale.ROOT));
    }
  }

  private static String classOf(Map<String, Object> answers) {
    if (answers == null) {
      return null;
    }
    String a = asString(answers.get("classSection"));
    if (a != null && !a.isBlank()) {
      return a;
    }
    String b = asString(answers.get("classApplied"));
    return b != null && !b.isBlank() ? b : null;
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v).trim();
  }
}
