package com.sugamflow.school.academic.service;

import com.sugamflow.school.academic.persistence.entity.AcademicClassEntity;
import com.sugamflow.school.academic.persistence.entity.ClassSectionEntity;
import com.sugamflow.school.academic.persistence.entity.SubjectEntity;
import com.sugamflow.school.academic.persistence.entity.TeachingAssignmentEntity;
import com.sugamflow.school.academic.persistence.entity.TimetablePeriodEntity;
import com.sugamflow.school.academic.persistence.entity.TimetableSlotEntity;
import java.util.LinkedHashMap;
import java.util.Map;

/** Entity -> API map conversion. Null values are kept so the UI sees a stable shape. */
final class AcademicMapper {

  private AcademicMapper() {}

  static Map<String, Object> classToMap(AcademicClassEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", str(e.getId()));
    m.put("name", e.getName());
    m.put("code", e.getCode());
    m.put("sequenceNo", e.getSequenceNo());
    m.put("status", e.getStatus());
    m.put("attributes", e.getAttributes());
    m.put("createdAt", str(e.getCreatedAt()));
    m.put("updatedAt", str(e.getUpdatedAt()));
    return m;
  }

  static Map<String, Object> sectionToMap(ClassSectionEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", str(e.getId()));
    m.put("classId", str(e.getClassId()));
    m.put("name", e.getName());
    m.put("code", e.getCode());
    m.put("studentLabel", e.getStudentLabel());
    m.put("classTeacherUsername", e.getClassTeacherUsername());
    m.put("room", e.getRoom());
    m.put("capacity", e.getCapacity());
    m.put("status", e.getStatus());
    m.put("attributes", e.getAttributes());
    m.put("createdAt", str(e.getCreatedAt()));
    m.put("updatedAt", str(e.getUpdatedAt()));
    return m;
  }

  static Map<String, Object> subjectToMap(SubjectEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", str(e.getId()));
    m.put("name", e.getName());
    m.put("code", e.getCode());
    m.put("subjectType", e.getSubjectType());
    m.put("status", e.getStatus());
    m.put("attributes", e.getAttributes());
    m.put("createdAt", str(e.getCreatedAt()));
    m.put("updatedAt", str(e.getUpdatedAt()));
    return m;
  }

  static Map<String, Object> assignmentToMap(TeachingAssignmentEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", str(e.getId()));
    m.put("sectionId", str(e.getSectionId()));
    m.put("subjectId", str(e.getSubjectId()));
    m.put("teacherUsername", e.getTeacherUsername());
    m.put("classTeacher", e.isClassTeacher());
    m.put("weeklyPeriods", e.getWeeklyPeriods());
    m.put("maxDailyPeriods", e.getMaxDailyPeriods());
    m.put("preferredRoom", e.getPreferredRoom());
    m.put("unavailableSlots", e.getUnavailableSlots());
    m.put("status", e.getStatus());
    m.put("createdAt", str(e.getCreatedAt()));
    m.put("updatedAt", str(e.getUpdatedAt()));
    return m;
  }

  static Map<String, Object> periodToMap(TimetablePeriodEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", str(e.getId()));
    m.put("periodNo", e.getPeriodNo());
    m.put("label", e.getLabel());
    m.put("startTime", e.getStartTime());
    m.put("endTime", e.getEndTime());
    m.put("breakPeriod", e.isBreakPeriod());
    m.put("createdAt", str(e.getCreatedAt()));
    m.put("updatedAt", str(e.getUpdatedAt()));
    return m;
  }

  static Map<String, Object> slotToMap(TimetableSlotEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", str(e.getId()));
    m.put("sectionId", str(e.getSectionId()));
    m.put("dayOfWeek", e.getDayOfWeek());
    m.put("periodId", str(e.getPeriodId()));
    m.put("subjectId", str(e.getSubjectId()));
    m.put("teacherUsername", e.getTeacherUsername());
    m.put("room", e.getRoom());
    m.put("createdAt", str(e.getCreatedAt()));
    m.put("updatedAt", str(e.getUpdatedAt()));
    return m;
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }
}
