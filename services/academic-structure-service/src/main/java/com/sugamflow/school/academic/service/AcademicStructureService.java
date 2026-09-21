package com.sugamflow.school.academic.service;

import com.sugamflow.school.academic.persistence.entity.AcademicClassEntity;
import com.sugamflow.school.academic.persistence.entity.ClassSectionEntity;
import com.sugamflow.school.academic.persistence.entity.SubjectEntity;
import com.sugamflow.school.academic.persistence.entity.TeachingAssignmentEntity;
import com.sugamflow.school.academic.persistence.repo.AcademicClassRepository;
import com.sugamflow.school.academic.persistence.repo.ClassSectionRepository;
import com.sugamflow.school.academic.persistence.repo.SubjectRepository;
import com.sugamflow.school.academic.persistence.repo.TeachingAssignmentRepository;
import com.sugamflow.school.academic.web.AcademicException;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CRUD for classes, sections, subjects and teacher assignments. Writes require staff roles. */
@Service
public class AcademicStructureService {

  private final AcademicClassRepository classes;
  private final ClassSectionRepository sections;
  private final SubjectRepository subjects;
  private final TeachingAssignmentRepository assignments;

  public AcademicStructureService(
      AcademicClassRepository classes,
      ClassSectionRepository sections,
      SubjectRepository subjects,
      TeachingAssignmentRepository assignments) {
    this.classes = classes;
    this.sections = sections;
    this.subjects = subjects;
    this.assignments = assignments;
  }

  // ---- bootstrap / overview -------------------------------------------------

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    String org = scope.organizationId();
    return Map.of(
        "organizationId", org,
        "classCount", classes.countByOrganizationId(org),
        "sectionCount", sections.countByOrganizationId(org),
        "subjectCount", subjects.countByOrganizationId(org),
        "canManage", !PersonaRoles.isRelationshipRestricted(scope.roleCode()));
  }

  // ---- classes --------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listClasses() {
    TenantScope scope = TenantContext.require();
    List<AcademicClassEntity> found =
        hasSessionScope(scope)
            ? classes.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderBySequenceNoAscNameAsc(
                scope.organizationId(), scope.branchId(), scope.academicSessionId())
            : classes.findByOrganizationIdOrderBySequenceNoAscNameAsc(scope.organizationId());
    return found.stream().map(AcademicMapper::classToMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> createClass(Map<String, Object> body) {
    TenantScope scope = requireStaff();
    String name = RequestValues.str(body, "name");
    if (name == null) {
      throw AcademicException.badRequest("Class name is required");
    }
    AcademicClassEntity e = new AcademicClassEntity();
    e.setId(UUID.randomUUID());
    stampTenant(e, scope);
    e.setName(name);
    e.setCode(RequestValues.str(body, "code"));
    e.setSequenceNo(RequestValues.intOr(body, "sequenceNo", 0));
    e.setStatus(RequestValues.strOr(body, "status", "ACTIVE"));
    e.setAttributes(RequestValues.map(body, "attributes"));
    return AcademicMapper.classToMap(classes.save(e));
  }

  @Transactional
  public Map<String, Object> updateClass(UUID id, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    AcademicClassEntity e =
        classes
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Class"));
    if (RequestValues.str(body, "name") != null) {
      e.setName(RequestValues.str(body, "name"));
    }
    if (body.containsKey("code")) {
      e.setCode(RequestValues.str(body, "code"));
    }
    if (body.containsKey("sequenceNo")) {
      e.setSequenceNo(RequestValues.intOr(body, "sequenceNo", e.getSequenceNo()));
    }
    if (RequestValues.str(body, "status") != null) {
      e.setStatus(RequestValues.str(body, "status"));
    }
    if (body.containsKey("attributes")) {
      e.setAttributes(RequestValues.map(body, "attributes"));
    }
    e.setUpdatedAt(Instant.now());
    return AcademicMapper.classToMap(classes.save(e));
  }

  @Transactional
  public void deleteClass(UUID id) {
    TenantScope scope = requireStaff();
    AcademicClassEntity e =
        classes
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Class"));
    if (!sections.findByOrganizationIdAndClassIdOrderByNameAsc(scope.organizationId(), id).isEmpty()) {
      throw AcademicException.badRequest("Remove the sections under this class first");
    }
    classes.delete(e);
  }

  // ---- sections -------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSections(UUID classId) {
    TenantScope scope = TenantContext.require();
    List<ClassSectionEntity> found =
        classId != null
            ? sections.findByOrganizationIdAndClassIdOrderByNameAsc(scope.organizationId(), classId)
            : hasSessionScope(scope)
                ? sections.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByNameAsc(
                    scope.organizationId(), scope.branchId(), scope.academicSessionId())
                : sections.findByOrganizationIdOrderByNameAsc(scope.organizationId());
    return found.stream().map(AcademicMapper::sectionToMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> createSection(Map<String, Object> body) {
    TenantScope scope = requireStaff();
    String name = RequestValues.str(body, "name");
    UUID classId = RequestValues.uuid(body, "classId");
    if (name == null || classId == null) {
      throw AcademicException.badRequest("Section name and classId are required");
    }
    classes
        .findByIdAndOrganizationId(classId, scope.organizationId())
        .orElseThrow(() -> AcademicException.badRequest("Unknown classId"));
    ClassSectionEntity e = new ClassSectionEntity();
    e.setId(UUID.randomUUID());
    stampTenant(e, scope);
    e.setClassId(classId);
    e.setName(name);
    e.setCode(RequestValues.str(body, "code"));
    e.setStudentLabel(RequestValues.str(body, "studentLabel"));
    e.setClassTeacherUsername(RequestValues.str(body, "classTeacherUsername"));
    e.setRoom(RequestValues.str(body, "room"));
    e.setCapacity(RequestValues.intOrNull(body, "capacity"));
    e.setStatus(RequestValues.strOr(body, "status", "ACTIVE"));
    e.setAttributes(RequestValues.map(body, "attributes"));
    return AcademicMapper.sectionToMap(sections.save(e));
  }

  @Transactional
  public Map<String, Object> updateSection(UUID id, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    ClassSectionEntity e =
        sections
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Section"));
    if (RequestValues.str(body, "name") != null) {
      e.setName(RequestValues.str(body, "name"));
    }
    if (body.containsKey("code")) {
      e.setCode(RequestValues.str(body, "code"));
    }
    if (body.containsKey("studentLabel")) {
      e.setStudentLabel(RequestValues.str(body, "studentLabel"));
    }
    if (body.containsKey("classTeacherUsername")) {
      e.setClassTeacherUsername(RequestValues.str(body, "classTeacherUsername"));
    }
    if (body.containsKey("room")) {
      e.setRoom(RequestValues.str(body, "room"));
    }
    if (body.containsKey("capacity")) {
      e.setCapacity(RequestValues.intOrNull(body, "capacity"));
    }
    if (RequestValues.str(body, "status") != null) {
      e.setStatus(RequestValues.str(body, "status"));
    }
    if (body.containsKey("attributes")) {
      e.setAttributes(RequestValues.map(body, "attributes"));
    }
    e.setUpdatedAt(Instant.now());
    return AcademicMapper.sectionToMap(sections.save(e));
  }

  @Transactional
  public void deleteSection(UUID id) {
    TenantScope scope = requireStaff();
    ClassSectionEntity e =
        sections
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Section"));
    for (TeachingAssignmentEntity a :
        assignments.findByOrganizationIdAndSectionId(scope.organizationId(), id)) {
      assignments.delete(a);
    }
    sections.delete(e);
  }

  // ---- subjects -------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listSubjects() {
    TenantScope scope = TenantContext.require();
    List<SubjectEntity> found =
        hasSessionScope(scope)
            ? subjects.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByNameAsc(
                scope.organizationId(), scope.branchId(), scope.academicSessionId())
            : subjects.findByOrganizationIdOrderByNameAsc(scope.organizationId());
    return found.stream()
        .map(AcademicMapper::subjectToMap)
        .collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> createSubject(Map<String, Object> body) {
    TenantScope scope = requireStaff();
    String name = RequestValues.str(body, "name");
    if (name == null) {
      throw AcademicException.badRequest("Subject name is required");
    }
    SubjectEntity e = new SubjectEntity();
    e.setId(UUID.randomUUID());
    stampTenant(e, scope);
    e.setName(name);
    e.setCode(RequestValues.str(body, "code"));
    e.setSubjectType(RequestValues.strOr(body, "subjectType", "CORE"));
    e.setStatus(RequestValues.strOr(body, "status", "ACTIVE"));
    e.setAttributes(RequestValues.map(body, "attributes"));
    return AcademicMapper.subjectToMap(subjects.save(e));
  }

  @Transactional
  public Map<String, Object> updateSubject(UUID id, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    SubjectEntity e =
        subjects
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Subject"));
    if (RequestValues.str(body, "name") != null) {
      e.setName(RequestValues.str(body, "name"));
    }
    if (body.containsKey("code")) {
      e.setCode(RequestValues.str(body, "code"));
    }
    if (RequestValues.str(body, "subjectType") != null) {
      e.setSubjectType(RequestValues.str(body, "subjectType"));
    }
    if (RequestValues.str(body, "status") != null) {
      e.setStatus(RequestValues.str(body, "status"));
    }
    if (body.containsKey("attributes")) {
      e.setAttributes(RequestValues.map(body, "attributes"));
    }
    e.setUpdatedAt(Instant.now());
    return AcademicMapper.subjectToMap(subjects.save(e));
  }

  @Transactional
  public void deleteSubject(UUID id) {
    TenantScope scope = requireStaff();
    SubjectEntity e =
        subjects
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Subject"));
    subjects.delete(e);
  }

  // ---- teaching assignments -------------------------------------------------

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listAssignments(UUID sectionId) {
    TenantScope scope = TenantContext.require();
    List<TeachingAssignmentEntity> found =
        sectionId != null
            ? assignments.findByOrganizationIdAndSectionId(scope.organizationId(), sectionId)
            : assignments.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId());
    return found.stream().map(AcademicMapper::assignmentToMap).collect(Collectors.toList());
  }

  @Transactional
  public Map<String, Object> createAssignment(Map<String, Object> body) {
    TenantScope scope = requireStaff();
    UUID sectionId = RequestValues.uuid(body, "sectionId");
    String teacher = RequestValues.str(body, "teacherUsername");
    if (sectionId == null || teacher == null) {
      throw AcademicException.badRequest("sectionId and teacherUsername are required");
    }
    ClassSectionEntity section =
        sections
            .findByIdAndOrganizationId(sectionId, scope.organizationId())
            .orElseThrow(() -> AcademicException.badRequest("Unknown sectionId"));
    UUID subjectId = RequestValues.uuid(body, "subjectId");
    if (subjectId != null) {
      subjects
          .findByIdAndOrganizationId(subjectId, scope.organizationId())
          .orElseThrow(() -> AcademicException.badRequest("Unknown subjectId"));
    }
    TeachingAssignmentEntity e = new TeachingAssignmentEntity();
    e.setId(UUID.randomUUID());
    stampTenant(e, scope);
    e.setSectionId(sectionId);
    e.setSubjectId(subjectId);
    e.setTeacherUsername(teacher);
    e.setClassTeacher(RequestValues.bool(body, "classTeacher"));
    e.setWeeklyPeriods(Math.max(1, RequestValues.intOr(body, "weeklyPeriods", 4)));
    e.setMaxDailyPeriods(Math.max(1, RequestValues.intOr(body, "maxDailyPeriods", 2)));
    e.setPreferredRoom(RequestValues.str(body, "preferredRoom"));
    e.setUnavailableSlots(listOfMaps(body.get("unavailableSlots")));
    e.setStatus(RequestValues.strOr(body, "status", "ACTIVE"));
    TeachingAssignmentEntity saved = assignments.save(e);
    if (saved.isClassTeacher()) {
      section.setClassTeacherUsername(teacher);
      section.setUpdatedAt(Instant.now());
      sections.save(section);
    }
    return AcademicMapper.assignmentToMap(saved);
  }

  @Transactional
  public Map<String, Object> updateAssignment(UUID id, Map<String, Object> body) {
    TenantScope scope = requireStaff();
    TeachingAssignmentEntity e =
        assignments
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Assignment"));
    if (body.containsKey("weeklyPeriods")) {
      e.setWeeklyPeriods(Math.max(1, RequestValues.intOr(body, "weeklyPeriods", e.getWeeklyPeriods())));
    }
    if (body.containsKey("maxDailyPeriods")) {
      e.setMaxDailyPeriods(
          Math.max(1, RequestValues.intOr(body, "maxDailyPeriods", e.getMaxDailyPeriods())));
    }
    if (body.containsKey("preferredRoom")) {
      e.setPreferredRoom(RequestValues.str(body, "preferredRoom"));
    }
    if (body.containsKey("unavailableSlots")) {
      e.setUnavailableSlots(listOfMaps(body.get("unavailableSlots")));
    }
    if (body.containsKey("status")) {
      e.setStatus(RequestValues.strOr(body, "status", e.getStatus()));
    }
    e.setUpdatedAt(Instant.now());
    return AcademicMapper.assignmentToMap(assignments.save(e));
  }

  @Transactional
  public void deleteAssignment(UUID id) {
    TenantScope scope = requireStaff();
    TeachingAssignmentEntity e =
        assignments
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> AcademicException.notFound("Assignment"));
    assignments.delete(e);
  }

  // ---- helpers --------------------------------------------------------------

  private TenantScope requireStaff() {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    if (PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      throw new SecurityException(
          "Academic structure changes require a staff role: " + scope.roleCode());
    }
    return scope;
  }

  private static boolean hasSessionScope(TenantScope scope) {
    return scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank();
  }

  private static List<Map<String, Object>> listOfMaps(Object raw) {
    List<Map<String, Object>> out = new ArrayList<>();
    if (!(raw instanceof List<?> list)) {
      return out;
    }
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> copy.put(String.valueOf(key), value));
        out.add(copy);
      }
    }
    return out;
  }

  private static void stampTenant(AcademicClassEntity e, TenantScope scope) {
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setAcademicSessionId(scope.academicSessionId());
  }

  private static void stampTenant(ClassSectionEntity e, TenantScope scope) {
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setAcademicSessionId(scope.academicSessionId());
  }

  private static void stampTenant(SubjectEntity e, TenantScope scope) {
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setAcademicSessionId(scope.academicSessionId());
  }

  private static void stampTenant(TeachingAssignmentEntity e, TenantScope scope) {
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setAcademicSessionId(scope.academicSessionId());
  }
}
