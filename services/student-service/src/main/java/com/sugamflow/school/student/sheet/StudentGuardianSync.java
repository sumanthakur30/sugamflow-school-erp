package com.sugamflow.school.student.sheet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps Father and Mother rows on {@code answers.guardians} aligned with flat profile names. */
public final class StudentGuardianSync {

  private StudentGuardianSync() {}

  @SuppressWarnings("unchecked")
  public static void syncFromFlat(Map<String, Object> answers) {
    if (answers == null) {
      return;
    }
    List<Map<String, Object>> guardians = new ArrayList<>();
    Object raw = answers.get("guardians");
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          guardians.add(new LinkedHashMap<>((Map<String, Object>) map));
        }
      }
    }
    boolean changed = false;
    changed |= upsert(guardians, "Father", text(answers.get("fatherName")), text(answers.get("fatherMobile")), text(answers.get("fatherEmail")));
    changed |= upsert(guardians, "Mother", text(answers.get("motherName")), text(answers.get("motherMobile")), text(answers.get("motherEmail")));
    if (changed) {
      answers.put("guardians", guardians);
    }
    fillFlatFromGuardians(answers, guardians);
  }

  public static String nameForRelation(Map<String, Object> answers, String relation) {
    if (answers == null) {
      return "";
    }
    String flatKey = relation.toLowerCase(Locale.ROOT).startsWith("mother") ? "motherName" : "fatherName";
    String direct = text(answers.get(flatKey));
    if (!direct.isEmpty()) {
      return direct;
    }
    Object raw = answers.get("guardians");
    if (!(raw instanceof List<?> list)) {
      return "";
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      String rel = text(map.get("relation")).toLowerCase(Locale.ROOT);
      if (rel.contains(relation.toLowerCase(Locale.ROOT))) {
        String name = text(map.get("fullName"));
        if (name.isEmpty()) {
          name = text(map.get("name"));
        }
        if (!name.isEmpty()) {
          return name;
        }
      }
    }
    return "";
  }

  private static boolean upsert(
      List<Map<String, Object>> guardians, String relation, String name, String mobile, String email) {
    if (name.isEmpty() && mobile.isEmpty() && email.isEmpty()) {
      return false;
    }
    for (Map<String, Object> guardian : guardians) {
      String rel = text(guardian.get("relation")).toLowerCase(Locale.ROOT);
      if (!rel.contains(relation.toLowerCase(Locale.ROOT))) {
        continue;
      }
      boolean changed = false;
      if (!name.isEmpty() && !name.equals(text(guardian.get("fullName")))) {
        guardian.put("fullName", name);
        changed = true;
      }
      if (!mobile.isEmpty() && !mobile.equals(text(guardian.get("mobile")))) {
        guardian.put("mobile", mobile);
        changed = true;
      }
      if (!email.isEmpty() && !email.equals(text(guardian.get("email")))) {
        guardian.put("email", email);
        changed = true;
      }
      return changed;
    }
    if (name.isEmpty()) {
      return false;
    }
    Map<String, Object> created = new LinkedHashMap<>();
    created.put("fullName", name);
    created.put("relation", relation);
    if (!mobile.isEmpty()) {
      created.put("mobile", mobile);
    }
    if (!email.isEmpty()) {
      created.put("email", email);
    }
    created.put("isPrimary", "Father".equals(relation) && guardians.isEmpty());
    guardians.add(created);
    return true;
  }

  private static void fillFlatFromGuardians(
      Map<String, Object> answers, List<Map<String, Object>> guardians) {
    if (text(answers.get("fatherName")).isEmpty()) {
      String name = nameForRelation(Map.of("guardians", guardians), "father");
      if (!name.isEmpty()) {
        answers.put("fatherName", name);
      }
    }
    if (text(answers.get("motherName")).isEmpty()) {
      String name = nameForRelation(Map.of("guardians", guardians), "mother");
      if (!name.isEmpty()) {
        answers.put("motherName", name);
      }
    }
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
