package com.sugamflow.school.common.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Relationship-scoped access for PARENT / TEACHER personas. When {@link #restricted()} is false,
 * the caller has tenant-wide access (elevated staff). When true, only matching student ids /
 * admission numbers / class sections are visible.
 */
public record AccessScope(
    boolean restricted,
    String persona,
    Set<String> studentIds,
    Set<String> admissionNos,
    Set<String> classSections) {

  public static AccessScope elevated() {
    return new AccessScope(false, "ELEVATED", Set.of(), Set.of(), Set.of());
  }

  public static AccessScope empty(String persona) {
    return new AccessScope(
        true,
        persona == null ? "RESTRICTED" : persona,
        Set.of(),
        Set.of(),
        Set.of());
  }

  public static AccessScope of(
      String persona,
      Collection<String> studentIds,
      Collection<String> admissionNos,
      Collection<String> classSections) {
    return new AccessScope(
        true,
        persona,
        normalizeSet(studentIds),
        normalizeSet(admissionNos),
        normalizeSet(classSections));
  }

  public boolean allowsStudentId(String studentId) {
    if (!restricted) {
      return true;
    }
    return studentId != null && studentIds.contains(normalize(studentId));
  }

  public boolean allowsAdmissionNo(String admissionNo) {
    if (!restricted) {
      return true;
    }
    return admissionNo != null && admissionNos.contains(normalize(admissionNo));
  }

  public boolean allowsClassSection(String classSection) {
    if (!restricted) {
      return true;
    }
    return classSection != null && classSections.contains(normalize(classSection));
  }

  /**
   * Match a student master DTO or a workflow record whose {@code answers} carry admissionNo /
   * classSection / studentId.
   */
  public boolean allowsStudentDto(Map<String, Object> dto) {
    if (!restricted) {
      return true;
    }
    if (dto == null) {
      return false;
    }
    if (allowsStudentId(asString(dto.get("id"))) || allowsStudentId(asString(dto.get("studentId")))) {
      return true;
    }
    if (allowsAdmissionNo(asString(dto.get("admissionNo")))) {
      return true;
    }
    Object answersObj = dto.get("answers");
    if (answersObj instanceof Map<?, ?> answers) {
      return allowsAnswers(answers);
    }
    return allowsClassSection(asString(dto.get("classSection")))
        || allowsClassSection(asString(dto.get("classApplied")));
  }

  public boolean allowsAnswers(Map<?, ?> answers) {
    if (!restricted) {
      return true;
    }
    if (answers == null) {
      return false;
    }
    if (allowsStudentId(asString(answers.get("studentId")))) {
      return true;
    }
    if (allowsAdmissionNo(asString(answers.get("admissionNo")))) {
      return true;
    }
    return allowsClassSection(asString(answers.get("classSection")))
        || allowsClassSection(asString(answers.get("className")))
        || allowsClassSection(asString(answers.get("classApplied")));
  }

  public Map<String, Object> toMap() {
    return Map.of(
        "restricted",
        restricted,
        "persona",
        persona == null ? "" : persona,
        "studentIds",
        studentIds,
        "admissionNos",
        admissionNos,
        "classSections",
        classSections,
        "studentCount",
        studentIds.size());
  }

  private static Set<String> normalizeSet(Collection<String> raw) {
    Set<String> out = new LinkedHashSet<>();
    if (raw == null) {
      return Set.of();
    }
    for (String s : raw) {
      String n = normalize(s);
      if (!n.isEmpty()) {
        out.add(n);
      }
    }
    return Set.copyOf(out);
  }

  private static String normalize(String raw) {
    return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
  }

  private static String asString(Object v) {
    return v == null ? null : String.valueOf(v).trim();
  }
}
