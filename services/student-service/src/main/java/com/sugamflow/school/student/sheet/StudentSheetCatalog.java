package com.sugamflow.school.student.sheet;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared student spreadsheet columns, header aliases, and row checks used by import and directory
 * export. Keeps Excel and CSV on the same field keys stored in {@code student_record.answers}.
 */
public final class StudentSheetCatalog {

  public static final int MAX_ROWS = 2000;
  public static final String MODE_CREATE = "CREATE";
  public static final String MODE_UPDATE = "UPDATE";
  public static final String MODE_UPSERT = "UPSERT";

  public static final List<String> GENDERS = List.of("Male", "Female", "Other");
  public static final List<String> BLOOD_GROUPS =
      List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
  public static final List<String> STATUSES =
      List.of("ACTIVE", "INACTIVE", "LEFT_SCHOOL", "TRANSFERRED", "TC_ISSUED", "ALUMNI");

  private static final Set<String> TEXT_KEYS =
      Set.of(
          "admissionNo",
          "mobile",
          "fatherMobile",
          "motherMobile",
          "pinCode",
          "penNumber",
          "apaarId",
          "samagraId",
          "schoolStudentId",
          "aadhaar",
          "rollNo");

  private static final List<Col> COLUMNS = List.of(
      col("admissionNo", "Admission No", false, "Leave blank to auto-generate on create. Text, do not change format."),
      col("fullName", "Full Name", true, "Student full name. Required for new students."),
      col("dateOfBirth", "Date of Birth", false, "YYYY-MM-DD or DD/MM/YYYY."),
      col("gender", "Gender", false, "Male, Female, or Other."),
      col("bloodGroup", "Blood Group", false, "A+, A-, B+, B-, AB+, AB-, O+, O-."),
      col("status", "Status", false, "ACTIVE, INACTIVE, LEFT_SCHOOL, TRANSFERRED, TC_ISSUED, ALUMNI."),
      col("admissionDate", "Admission Date", false, "YYYY-MM-DD or DD/MM/YYYY."),
      col("classApplied", "Class", true, "Combined class, for example Grade 7-A. Or use Grade and Section."),
      col("classGrade", "Grade", false, "Grade number or name, for example 7."),
      col("sectionLetter", "Section", false, "Section letter, for example A."),
      col("rollNo", "Roll Number", false, "Unique within the class."),
      col("academicSessionId", "Session", false, "Must match the signed-in academic session when filled."),
      col("branchId", "Branch", false, "Must match the signed-in branch when filled."),
      col("mobile", "Mobile", true, "Student or primary contact mobile."),
      col("email", "Email", false, "Optional email."),
      col("fatherName", "Father Name", false, "Also stored as a Father guardian."),
      col("fatherMobile", "Father Mobile", false, "Optional."),
      col("fatherEmail", "Father Email", false, "Optional."),
      col("fatherOccupation", "Father Occupation", false, "Optional."),
      col("motherName", "Mother Name", false, "Also stored as a Mother guardian."),
      col("motherMobile", "Mother Mobile", false, "Optional."),
      col("motherEmail", "Mother Email", false, "Optional."),
      col("motherOccupation", "Mother Occupation", false, "Optional."),
      col("address", "Current Address", false, "Optional."),
      col("city", "City", false, "Optional."),
      col("state", "State", false, "Optional."),
      col("pinCode", "PIN Code", false, "Keep as text so leading zeros stay."),
      col("nationality", "Nationality", false, "Optional."),
      col("religion", "Religion", false, "Optional."),
      col("category", "Category", false, "Optional."),
      col("previousSchool", "Previous School", false, "Optional."),
      col("previousClass", "Previous Class", false, "Optional."),
      col("penNumber", "PEN", false, "Text. Used to match an existing student when admission number is blank."),
      col("apaarId", "APAAR", false, "Text."),
      col("samagraId", "Samagra", false, "Text."),
      col("schoolStudentId", "School Student ID", false, "Text."),
      col("aadhaar", "Aadhaar Number", false, "Optional. 12 digits. Included on the full template only."),
      col("photoUrl", "Photo URL", false, "Only an existing app photo path is kept. Upload photos on the student profile."));

  private static final Map<String, String> ALIASES = aliasMap();

  private StudentSheetCatalog() {}

  public static List<Col> columns() {
    return COLUMNS;
  }

  /** Everyday directory download. Aadhaar stays on the import template and UDISE export. */
  public static List<Col> directoryColumns() {
    List<Col> out = new ArrayList<>();
    for (Col col : COLUMNS) {
      if (!"aadhaar".equals(col.key())) {
        out.add(col);
      }
    }
    return out;
  }

  public static List<String> templateKeys() {
    List<String> keys = new ArrayList<>();
    for (Col col : COLUMNS) {
      keys.add(col.key());
    }
    return keys;
  }

  /**
   * Records an admission number already seen in this file. A second copy, ignoring case, is an
   * error. A blank admission number is not a duplicate.
   */
  public static String noteDuplicateAdmission(Set<String> seen, String admissionNo) {
    if (seen == null || admissionNo == null || admissionNo.isBlank()) {
      return null;
    }
    if (!seen.add(admissionNo.trim().toLowerCase(Locale.ROOT))) {
      return "Duplicate admission number in this file";
    }
    return null;
  }

  public static String normalizeMode(String raw) {
    if (raw == null || raw.isBlank()) {
      return MODE_CREATE;
    }
    String mode = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    return switch (mode) {
      case "UPDATE", "UPDATE_EXISTING" -> MODE_UPDATE;
      case "UPSERT", "CREATE_OR_UPDATE", "IMPORT_AND_UPDATE" -> MODE_UPSERT;
      default -> MODE_CREATE;
    };
  }

  /** Target field key to the header label written on the template. */
  public static Map<String, String> defaultMapping() {
    Map<String, String> mapping = new LinkedHashMap<>();
    for (Col col : COLUMNS) {
      mapping.put(col.key(), col.label());
    }
    return mapping;
  }

  public static String canonicalKey(String header) {
    if (header == null) {
      return null;
    }
    String trimmed = header.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    String folded = fold(trimmed);
    return ALIASES.get(folded);
  }

  public static Map<String, Object> mapRow(Map<String, Object> raw, Map<String, ?> explicitMapping) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    if (explicitMapping != null && !explicitMapping.isEmpty()) {
      for (Map.Entry<String, ?> entry : explicitMapping.entrySet()) {
        String target = entry.getKey();
        if (target == null || target.startsWith("_") || canonicalKey(target) == null && !isKnownKey(target)) {
          continue;
        }
        if (!isKnownKey(target)) {
          continue;
        }
        Object value = lookup(raw, String.valueOf(entry.getValue()));
        putIfPresent(mapped, target, value);
      }
      return mapped;
    }
    if (raw == null) {
      return mapped;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String key = canonicalKey(entry.getKey());
      if (key != null) {
        putIfPresent(mapped, key, entry.getValue());
      }
    }
    return mapped;
  }

  public static List<String> unmappedHeaders(Map<String, Object> raw, Map<String, ?> explicitMapping) {
    if (raw == null || raw.isEmpty()) {
      return List.of();
    }
    Set<String> usedSources = new LinkedHashSet<>();
    if (explicitMapping != null && !explicitMapping.isEmpty()) {
      for (Object source : explicitMapping.values()) {
        if (source != null) {
          usedSources.add(fold(String.valueOf(source)));
        }
      }
    }
    List<String> unmapped = new ArrayList<>();
    for (String header : raw.keySet()) {
      if (header == null || header.isBlank() || header.startsWith("_")) {
        continue;
      }
      if (canonicalKey(header) != null) {
        continue;
      }
      if (usedSources.contains(fold(header))) {
        continue;
      }
      unmapped.add(header.trim());
    }
    return unmapped;
  }

  public static void normalize(Map<String, Object> mapped) {
    if (mapped == null) {
      return;
    }
    String gender = text(mapped.get("gender"));
    if (!gender.isEmpty()) {
      mapped.put("gender", canonicalGender(gender));
    }
    String blood = text(mapped.get("bloodGroup"));
    if (!blood.isEmpty()) {
      mapped.put("bloodGroup", blood.toUpperCase(Locale.ROOT).replace(" ", ""));
    }
    String status = text(mapped.get("status"));
    if (!status.isEmpty()) {
      mapped.put("status", status.toUpperCase(Locale.ROOT).replace(' ', '_'));
    }
    normalizeDate(mapped, "dateOfBirth");
    normalizeDate(mapped, "admissionDate");
    String grade = text(mapped.get("classGrade"));
    String section = text(mapped.get("sectionLetter"));
    if (section.isEmpty()) {
      section = text(mapped.get("section"));
    }
    if (!grade.isEmpty() && !section.isEmpty()) {
      String combined = "Grade " + grade.trim() + "-" + section.trim().toUpperCase(Locale.ROOT);
      mapped.put("classApplied", combined);
      mapped.put("classSection", combined);
      mapped.put("sectionLetter", section.trim().toUpperCase(Locale.ROOT));
    } else if (!section.isEmpty()) {
      mapped.put("sectionLetter", section.trim().toUpperCase(Locale.ROOT));
    }
    String photo = text(mapped.get("photoUrl"));
    if (!photo.isEmpty() && !photo.startsWith("/api/student/attachments/")) {
      mapped.remove("photoUrl");
    }
  }

  /**
   * @return null when the row can be saved, otherwise a short reason
   */
  public static String validate(Map<String, Object> mapped, String mode, String branchId, String sessionId) {
    if (mapped == null || mapped.isEmpty()) {
      return "Empty mapped row";
    }
    String normalizedMode = normalizeMode(mode);
    String fullName = text(mapped.get("fullName"));
    String mobile = text(mapped.get("mobile"));
    String klass = text(mapped.get("classApplied"));
    String admissionNo = text(mapped.get("admissionNo"));
    String pen = text(mapped.get("penNumber"));
    if (!MODE_UPDATE.equals(normalizedMode)) {
      if (fullName.isEmpty()) {
        return "Full Name is required";
      }
      if (mobile.isEmpty()) {
        return "Mobile is required";
      }
      if (klass.isEmpty()) {
        return "Class is required";
      }
    } else if (admissionNo.isEmpty() && pen.isEmpty()) {
      return "Admission No or PEN is required to update a student";
    }
    if (!mobile.isEmpty() && !mobile.matches("^[0-9+\\-\\s]{7,20}$")) {
      return "Mobile format is invalid";
    }
    String gender = text(mapped.get("gender"));
    if (!gender.isEmpty() && !GENDERS.contains(gender)) {
      return "Gender must be Male, Female, or Other";
    }
    String blood = text(mapped.get("bloodGroup"));
    if (!blood.isEmpty() && !BLOOD_GROUPS.contains(blood)) {
      return "Blood Group is not a supported value";
    }
    String status = text(mapped.get("status"));
    if (!status.isEmpty() && !STATUSES.contains(status)) {
      return "Status is not a supported value";
    }
    String dobError = dateError(mapped.get("dateOfBirth"), "Date of Birth");
    if (dobError != null) {
      return dobError;
    }
    String admissionDateError = dateError(mapped.get("admissionDate"), "Admission Date");
    if (admissionDateError != null) {
      return admissionDateError;
    }
    String section = text(mapped.get("sectionLetter"));
    if (!section.isEmpty() && !section.matches("^[A-Za-z0-9]{1,3}$")) {
      return "Section is invalid";
    }
    if (klass.isEmpty() && !section.isEmpty()) {
      return "Class is required when Section is set";
    }
    if (!klass.isEmpty() && (klass.length() > 80 || !klass.matches(".*[A-Za-z0-9].*"))) {
      return "Class is invalid";
    }
    String pin = text(mapped.get("pinCode"));
    if (!pin.isEmpty() && !pin.matches("^[0-9]{4,10}$")) {
      return "PIN Code is invalid";
    }
    String rowBranch = text(mapped.get("branchId"));
    if (!rowBranch.isEmpty() && branchId != null && !branchId.isBlank()
        && !rowBranch.equalsIgnoreCase(branchId.trim())) {
      return "Branch does not match the signed-in school";
    }
    String rowSession = text(mapped.get("academicSessionId"));
    if (!rowSession.isEmpty() && sessionId != null && !sessionId.isBlank()
        && !rowSession.equalsIgnoreCase(sessionId.trim())) {
      return "Session does not match the signed-in academic session";
    }
    return null;
  }

  public static String formulaSafe(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    char first = value.charAt(0);
    if (first == '=' || first == '+' || first == '-' || first == '@') {
      return "'" + value;
    }
    return value;
  }

  public static String csv(Object value) {
    String safe = formulaSafe(value == null ? "" : String.valueOf(value));
    if (safe.contains(",") || safe.contains("\"") || safe.contains("\n")) {
      return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
    return safe;
  }

  public static String exportPhoto(Object value) {
    String url = value == null ? "" : String.valueOf(value).trim();
    return url.startsWith("/api/student/attachments/") ? url : "";
  }

  public static boolean isTextKey(String key) {
    return TEXT_KEYS.contains(key);
  }

  private static boolean isKnownKey(String key) {
    for (Col col : COLUMNS) {
      if (col.key().equals(key)) {
        return true;
      }
    }
    return false;
  }

  private static void putIfPresent(Map<String, Object> mapped, String key, Object value) {
    if (value == null) {
      return;
    }
    String text = String.valueOf(value).trim();
    if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
      return;
    }
    mapped.put(key, text);
  }

  private static Object lookup(Map<String, Object> raw, String source) {
    if (raw == null || source == null) {
      return null;
    }
    if (raw.containsKey(source)) {
      return raw.get(source);
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(source)) {
        return entry.getValue();
      }
    }
    String wanted = canonicalKey(source);
    if (wanted == null) {
      return null;
    }
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      if (wanted.equals(canonicalKey(entry.getKey()))) {
        return entry.getValue();
      }
    }
    return null;
  }

  private static String canonicalGender(String raw) {
    String folded = raw.trim().toLowerCase(Locale.ROOT);
    if (folded.startsWith("m") || "boy".equals(folded)) {
      return "Male";
    }
    if (folded.startsWith("f") || "girl".equals(folded)) {
      return "Female";
    }
    if (folded.startsWith("o")) {
      return "Other";
    }
    return raw.trim();
  }

  private static void normalizeDate(Map<String, Object> mapped, String key) {
    String raw = text(mapped.get(key));
    if (raw.isEmpty()) {
      return;
    }
    LocalDate parsed = parseDate(raw);
    if (parsed != null) {
      mapped.put(key, parsed.toString());
    }
  }

  private static String dateError(Object raw, String label) {
    String text = text(raw);
    if (text.isEmpty()) {
      return null;
    }
    LocalDate parsed = parseDate(text);
    if (parsed == null) {
      return label + " format is invalid";
    }
    if (parsed.isAfter(LocalDate.now().plusDays(1))) {
      return label + " cannot be in the future";
    }
    return null;
  }

  private static LocalDate parseDate(String raw) {
    String value = raw.trim();
    if (value.length() >= 10 && value.charAt(4) == '-') {
      value = value.substring(0, 10);
    }
    List<DateTimeFormatter> formats =
        List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"));
    for (DateTimeFormatter format : formats) {
      try {
        return LocalDate.parse(value, format);
      } catch (DateTimeParseException ignored) {
        // try the next format
      }
    }
    return null;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static String fold(String header) {
    return header.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
  }

  private static Col col(String key, String label, boolean required, String hint) {
    return new Col(key, label, required, hint);
  }

  private static Map<String, String> aliasMap() {
    Map<String, String> map = new LinkedHashMap<>();
    for (Col col : COLUMNS) {
      map.put(fold(col.key()), col.key());
      map.put(fold(col.label()), col.key());
    }
    alias(map, "studentname", "fullName");
    alias(map, "name", "fullName");
    alias(map, "admissionnumber", "admissionNo");
    alias(map, "admissionno", "admissionNo");
    alias(map, "dob", "dateOfBirth");
    alias(map, "dateofbirth", "dateOfBirth");
    alias(map, "sex", "gender");
    alias(map, "bloodgroup", "bloodGroup");
    alias(map, "fathersname", "fatherName");
    alias(map, "fathername", "fatherName");
    alias(map, "mothersname", "motherName");
    alias(map, "mothername", "motherName");
    alias(map, "classsection", "classApplied");
    alias(map, "class", "classApplied");
    alias(map, "section", "sectionLetter");
    alias(map, "rollno", "rollNo");
    alias(map, "rollnumber", "rollNo");
    alias(map, "mobileno", "mobile");
    alias(map, "phone", "mobile");
    alias(map, "pincode", "pinCode");
    alias(map, "pin", "pinCode");
    alias(map, "academicsession", "academicSessionId");
    alias(map, "session", "academicSessionId");
    alias(map, "branch", "branchId");
    alias(map, "penno", "penNumber");
    alias(map, "apaarid", "apaarId");
    alias(map, "studentstatus", "status");
    alias(map, "photourl", "photoUrl");
    return map;
  }

  private static void alias(Map<String, String> map, String folded, String key) {
    map.put(folded, key);
  }

  public record Col(String key, String label, boolean required, String hint) {}
}
