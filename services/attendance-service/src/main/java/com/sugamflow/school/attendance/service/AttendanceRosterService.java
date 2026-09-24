package com.sugamflow.school.attendance.service;

import com.sugamflow.school.attendance.integration.AcademicClient;
import com.sugamflow.school.attendance.integration.ConfigEngineClient;
import com.sugamflow.school.attendance.integration.StudentAccessClient;
import com.sugamflow.school.attendance.integration.StudentDirectoryClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceMarkRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceSessionRepository;
import com.sugamflow.school.attendance.web.AttendanceException;
import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Roster-first daily/period attendance on top of academic sections + student directory. */
@Service
public class AttendanceRosterService {

  private static final Set<String> MARK_STATUSES =
      Set.of("PRESENT", "ABSENT", "LATE", "LEAVE");

  private final AttendanceSessionRepository sessions;
  private final AttendanceMarkRepository marks;
  private final StudentDirectoryClient directory;
  private final AcademicClient academic;
  private final StudentAccessClient studentAccess;
  private final AttendanceAlertService alerts;
  private final ConfigEngineClient engines;

  public AttendanceRosterService(
      AttendanceSessionRepository sessions,
      AttendanceMarkRepository marks,
      StudentDirectoryClient directory,
      AcademicClient academic,
      StudentAccessClient studentAccess,
      AttendanceAlertService alerts,
      ConfigEngineClient engines) {
    this.sessions = sessions;
    this.marks = marks;
    this.directory = directory;
    this.academic = academic;
    this.studentAccess = studentAccess;
    this.alerts = alerts;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> roster(UUID sectionId, LocalDate date, UUID periodId) {
    TenantScope scope = TenantContext.require();
    if (sectionId == null || date == null) {
      throw new AttendanceException("VALIDATION", "sectionId and date are required");
    }
    requireSectionAccess(scope, sectionId);

    Map<String, Object> section = academic.getSection(scope, sectionId.toString());
    if (section.isEmpty()) {
      throw new AttendanceException("NOT_FOUND", "Section not found");
    }
    String label =
        firstNonBlank(
            asString(section.get("studentLabel")),
            asString(section.get("name")),
            sectionId.toString());

    AttendanceSessionEntity session = findSession(scope.organizationId(), sectionId, date, periodId);
    Map<UUID, AttendanceMarkEntity> byStudent = new LinkedHashMap<>();
    Map<String, AttendanceMarkEntity> byAdmission = new LinkedHashMap<>();
    if (session != null) {
      for (AttendanceMarkEntity m : marks.findBySessionIdOrderByStudentNameAsc(session.getId())) {
        if (m.getStudentId() != null) {
          byStudent.put(m.getStudentId(), m);
        }
        if (m.getAdmissionNo() != null) {
          byAdmission.put(m.getAdmissionNo().toLowerCase(Locale.ROOT), m);
        }
      }
    }

    List<Map<String, Object>> students = new ArrayList<>();
    for (Map<String, Object> row : directory.listByClassSection(scope, label, 500)) {
      UUID studentId = parseUuid(row.get("id"));
      String admission = asString(row.get("admissionNo"));
      AttendanceMarkEntity existing =
          studentId != null
              ? byStudent.get(studentId)
              : (admission == null ? null : byAdmission.get(admission.toLowerCase(Locale.ROOT)));
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("studentId", studentId == null ? null : studentId.toString());
      out.put("admissionNo", admission);
      out.put(
          "studentName",
          firstNonBlank(asString(row.get("fullName")), asString(row.get("studentName")), admission));
      out.put("classSection", firstNonBlank(asString(row.get("classSection")), label));
      out.put("photoUrl", asString(row.get("photoUrl")));
      out.put("markStatus", existing == null ? null : existing.getStatus());
      out.put("remark", existing == null ? null : existing.getRemark());
      students.add(out);
    }

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("sectionId", sectionId.toString());
    result.put("sectionLabel", label);
    result.put("date", date.toString());
    result.put("periodId", periodId == null ? null : periodId.toString());
    result.put("session", session == null ? null : sessionToMap(session));
    result.put("students", students);
    return result;
  }

  /** Printable class attendance register (PDF/Excel/CSV via report-builder tabular export). */
  @Transactional(readOnly = true)
  public Map<String, Object> registerExport(
      UUID sectionId, LocalDate date, UUID periodId, String format) {
    TenantScope scope = TenantContext.require();
    Map<String, Object> roster = roster(sectionId, date, periodId);
    List<Map<String, Object>> columns =
        List.of(
            Map.of("key", "admissionNo", "label", "Admission No"),
            Map.of("key", "studentName", "label", "Student Name"),
            Map.of("key", "classSection", "label", "Class"),
            Map.of("key", "markStatus", "label", "Status"),
            Map.of("key", "remark", "label", "Remark"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students =
        roster.get("students") instanceof List<?> list
            ? (List<Map<String, Object>>) list
            : List.of();
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> s : students) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("admissionNo", s.get("admissionNo"));
      row.put("studentName", s.get("studentName"));
      row.put("classSection", s.get("classSection"));
      row.put("markStatus", s.get("markStatus") == null ? "" : s.get("markStatus"));
      row.put("remark", s.get("remark") == null ? "" : s.get("remark"));
      rows.add(row);
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("title", "Attendance Register");
    data.put(
        "subtitle",
        stringOr(roster.get("sectionLabel"), "")
            + " · "
            + date
            + (periodId != null ? " · Period " + periodId : ""));
    data.put("columns", columns);
    data.put("rows", rows);
    Map<String, Object> rendered =
        engines.renderReport(scope, "attendance_register", data, format == null ? "PDF" : format);
    if (rendered == null || rendered.get("contentBase64") == null) {
      throw new AttendanceException("RENDER_FAILED", "Attendance register render returned no content");
    }
    return rendered;
  }

  private static String stringOr(Object v, String fallback) {
    if (v == null) return fallback;
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? fallback : s;
  }

  @Transactional
  public Map<String, Object> bulkMark(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    UUID sectionId = parseUuid(body.get("sectionId"));
    LocalDate date = parseDate(body.get("date"));
    UUID periodId = parseUuid(body.get("periodId"));
    if (sectionId == null || date == null) {
      throw new AttendanceException("VALIDATION", "sectionId and date are required");
    }
    requireSectionAccess(scope, sectionId);

    AttendanceSessionEntity session = findSession(scope.organizationId(), sectionId, date, periodId);
    if (session == null) {
      session = new AttendanceSessionEntity();
      session.setId(UUID.randomUUID());
      session.setOrganizationId(scope.organizationId());
      session.setBranchId(scope.branchId());
      session.setAcademicSessionId(scope.academicSessionId());
      session.setSectionId(sectionId);
      session.setPeriodId(periodId);
      session.setAttendanceDate(date);
      session.setStatus("DRAFT");
      session.setCreatedAt(Instant.now());
    }
    if ("LOCKED".equals(session.getStatus())) {
      throw new AttendanceException("LOCKED", "Attendance session is locked");
    }
    session.setMarkedBy(scope.userId());
    session.setUpdatedAt(Instant.now());
    boolean submitted = "SUBMITTED".equalsIgnoreCase(asString(body.get("submit")));
    if (submitted) {
      session.setStatus("SUBMITTED");
    } else if (!"SUBMITTED".equals(session.getStatus()) && !"LOCKED".equals(session.getStatus())) {
      session.setStatus("DRAFT");
    }
    session = sessions.save(session);

    Object rawMarks = body.get("marks");
    if (!(rawMarks instanceof List<?> list)) {
      throw new AttendanceException("VALIDATION", "marks array is required");
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> mark = (Map<String, Object>) m;
      upsertMark(session, scope, mark);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("session", sessionToMap(session));
    out.put("markCount", marks.findBySessionIdOrderByStudentNameAsc(session.getId()).size());
    if (submitted) {
      out.put("parentAlerts", alerts.onSessionSubmitted(scope, session, sectionLabel(scope, session)));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> submitSession(UUID sessionId) {
    return transition(sessionId, "SUBMITTED");
  }

  @Transactional
  public Map<String, Object> lockSession(UUID sessionId) {
    return transition(sessionId, "LOCKED");
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> sessionAlerts(UUID sessionId) {
    TenantScope scope = TenantContext.require();
    AttendanceSessionEntity session =
        sessions
            .findByIdAndOrganizationId(sessionId, scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Session not found"));
    requireSectionAccess(scope, session.getSectionId());
    return alerts.deliveryHistory(session.getId());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> myMarks() {
    TenantScope scope = TenantContext.require();
    AccessScope access = studentAccess.resolve(scope);
    if (!access.restricted()) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (String studentId : access.studentIds()) {
      UUID id = parseUuid(studentId);
      if (id == null) {
        continue;
      }
      for (AttendanceMarkEntity m :
          marks.findByOrganizationIdAndStudentIdOrderByMarkedAtDesc(scope.organizationId(), id)) {
        AttendanceSessionEntity session =
            sessions.findByIdAndOrganizationId(m.getSessionId(), scope.organizationId()).orElse(null);
        if (session == null || "DRAFT".equals(session.getStatus())) {
          continue;
        }
        out.add(markToMap(m, session));
      }
    }
    for (String admission : access.admissionNos()) {
      for (AttendanceMarkEntity m :
          marks.findByOrganizationIdAndAdmissionNoOrderByMarkedAtDesc(
              scope.organizationId(), admission)) {
        if (m.getStudentId() != null) {
          continue;
        }
        AttendanceSessionEntity session =
            sessions.findByIdAndOrganizationId(m.getSessionId(), scope.organizationId()).orElse(null);
        if (session == null || "DRAFT".equals(session.getStatus())) {
          continue;
        }
        out.add(markToMap(m, session));
      }
    }
    return out;
  }

  private Map<String, Object> transition(UUID sessionId, String status) {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    AttendanceSessionEntity session =
        sessions
            .findByIdAndOrganizationId(sessionId, scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Session not found"));
    requireSectionAccess(scope, session.getSectionId());
    if ("LOCKED".equals(session.getStatus()) && !"LOCKED".equals(status)) {
      throw new AttendanceException("LOCKED", "Session is locked");
    }
    session.setStatus(status);
    session.setUpdatedAt(Instant.now());
    session.setMarkedBy(scope.userId());
    Map<String, Object> out = sessionToMap(sessions.save(session));
    if ("SUBMITTED".equals(status)) {
      out.put("parentAlerts", alerts.onSessionSubmitted(scope, session, sectionLabel(scope, session)));
    }
    return out;
  }

  private String sectionLabel(TenantScope scope, AttendanceSessionEntity session) {
    Map<String, Object> section = academic.getSection(scope, session.getSectionId().toString());
    return firstNonBlank(
        asString(section.get("studentLabel")),
        asString(section.get("name")),
        session.getSectionId().toString());
  }

  private void upsertMark(AttendanceSessionEntity session, TenantScope scope, Map<String, Object> mark) {
    String status = asString(mark.get("status"));
    if (status == null || !MARK_STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
      throw new AttendanceException(
          "VALIDATION", "status must be one of PRESENT, ABSENT, LATE, LEAVE");
    }
    status = status.toUpperCase(Locale.ROOT);
    UUID studentId = parseUuid(mark.get("studentId"));
    String admission = asString(mark.get("admissionNo"));
    String name = asString(mark.get("studentName"));

    AttendanceMarkEntity entity = null;
    if (studentId != null) {
      entity = marks.findBySessionIdAndStudentId(session.getId(), studentId).orElse(null);
    } else if (admission != null) {
      entity = marks.findBySessionIdAndAdmissionNo(session.getId(), admission).orElse(null);
    }
    if (entity == null) {
      entity = new AttendanceMarkEntity();
      entity.setId(UUID.randomUUID());
      entity.setOrganizationId(scope.organizationId());
      entity.setSessionId(session.getId());
      entity.setCreatedAt(Instant.now());
    }
    entity.setStudentId(studentId);
    entity.setAdmissionNo(admission);
    entity.setStudentName(name);
    entity.setStatus(status);
    entity.setRemark(asString(mark.get("remark")));
    entity.setMarkedBy(scope.userId());
    entity.setMarkedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    marks.save(entity);
  }

  private AttendanceSessionEntity findSession(
      String org, UUID sectionId, LocalDate date, UUID periodId) {
    if (periodId == null) {
      return sessions
          .findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(org, sectionId, date)
          .orElse(null);
    }
    return sessions
        .findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodId(org, sectionId, date, periodId)
        .orElse(null);
  }

  private void requireSectionAccess(TenantScope scope, UUID sectionId) {
    if (!PersonaRoles.isRelationshipRestricted(scope.roleCode())) {
      return;
    }
    if (!PersonaRoles.isTeacher(scope.roleCode())) {
      throw new SecurityException("Only staff/teachers can mark attendance for a section");
    }
    Map<String, Object> teacherScope = academic.teacherScope(scope);
    Object ids = teacherScope.get("sectionIds");
    if (ids instanceof List<?> list) {
      for (Object id : list) {
        if (sectionId.toString().equalsIgnoreCase(String.valueOf(id))) {
          return;
        }
      }
    }
    throw new SecurityException("Teacher is not assigned to section " + sectionId);
  }

  private static Map<String, Object> sessionToMap(AttendanceSessionEntity s) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", s.getId().toString());
    m.put("sectionId", s.getSectionId().toString());
    m.put("periodId", s.getPeriodId() == null ? null : s.getPeriodId().toString());
    m.put("date", s.getAttendanceDate().toString());
    m.put("status", s.getStatus());
    m.put("markedBy", s.getMarkedBy());
    m.put("updatedAt", s.getUpdatedAt() == null ? null : s.getUpdatedAt().toString());
    return m;
  }

  private static Map<String, Object> markToMap(AttendanceMarkEntity m, AttendanceSessionEntity s) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", m.getId().toString());
    out.put("status", m.getStatus());
    out.put("remark", m.getRemark());
    out.put("studentId", m.getStudentId() == null ? null : m.getStudentId().toString());
    out.put("admissionNo", m.getAdmissionNo());
    out.put("studentName", m.getStudentName());
    out.put("date", s.getAttendanceDate().toString());
    out.put("sectionId", s.getSectionId().toString());
    out.put("sessionStatus", s.getStatus());
    return out;
  }

  private static UUID parseUuid(Object raw) {
    if (raw == null) return null;
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) return null;
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static LocalDate parseDate(Object raw) {
    String s = asString(raw);
    if (s == null) return null;
    try {
      return LocalDate.parse(s);
    } catch (Exception ex) {
      throw new AttendanceException("VALIDATION", "Invalid date: " + s);
    }
  }

  private static String asString(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) return v;
    }
    return null;
  }
}
