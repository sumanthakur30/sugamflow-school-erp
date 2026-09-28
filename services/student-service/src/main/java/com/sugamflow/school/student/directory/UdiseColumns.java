package com.sugamflow.school.student.directory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Official-style UDISE+ student columns. Order here is the file column order. */
public final class UdiseColumns {

  public record Column(String key, String label, boolean yesNo) {}

  private static final List<Column> ALL =
      List.of(
          col("fullName", "Student Name", false),
          col("dateOfBirth", "Date of Birth", false),
          col("gender", "Gender", false),
          col("aadhaar", "Aadhaar Number", false),
          col("category", "Social Category", false),
          col("className", "Class", false),
          col("admissionNo", "Admission Number", false),
          col("enrollmentType", "Enrollment Type", false),
          col("motherTongue", "Mother Tongue / Medium", false),
          col("penNumber", "PEN No.", false),
          col("apaarId", "APAAR ID", false),
          col("section", "Section", false),
          col("nationality", "Nationality", false),
          col("bloodGroup", "Blood Group", false),
          col("caste", "Caste", false),
          col("religion", "Religion", false),
          col("rte", "RTE Student (Yes/No)", true),
          col("bpl", "BPL Student (Yes/No)", true),
          col("cwsn", "CWSN / Disability (Yes/No)", true),
          col("disabilityRemark", "Disability Remark", false),
          col("fatherName", "Father's Name", false),
          col("fatherAadhaar", "Father's Aadhaar No.", false),
          col("motherName", "Mother's Name", false),
          col("motherAadhaar", "Mother's Aadhaar No.", false),
          col("guardianName", "Guardian's Name", false),
          col("admissionDate", "Admission Date", false),
          col("address", "Address", false),
          col("city", "City", false),
          col("state", "State", false),
          col("pincode", "Pincode", false),
          col("mobile", "Mobile No.", false),
          col("bankAccountHolder", "Bank Account Holder Name", false),
          col("bankName", "Bank Name", false),
          col("bankAccountNo", "Bank Account No.", false),
          col("ifsc", "IFSC Code", false),
          col("governmentStudentId", "Government Student ID", false),
          col("familyId", "Family ID", false),
          col("samagraId", "Samagra ID", false));

  private UdiseColumns() {}

  public static List<Column> all() {
    return ALL;
  }

  public static List<String> defaultKeys() {
    return ALL.stream().map(Column::key).toList();
  }

  /** Keep known keys in catalog order. Unknown keys are dropped. */
  public static List<String> keepKnownInOrder(List<String> requested) {
    if (requested == null || requested.isEmpty()) {
      return List.of();
    }
    List<String> wanted = new ArrayList<>();
    for (String raw : requested) {
      if (raw != null && !raw.isBlank()) {
        wanted.add(raw.trim());
      }
    }
    List<String> ordered = new ArrayList<>();
    for (Column column : ALL) {
      if (wanted.contains(column.key())) {
        ordered.add(column.key());
      }
    }
    return List.copyOf(ordered);
  }

  public static String cell(String admissionNo, Map<String, Object> answers, String key) {
    Map<String, Object> a = answers != null ? answers : Map.of();
    String value =
        switch (key) {
          case "fullName" -> first(a, "fullName", "studentName");
          case "dateOfBirth" -> first(a, "dateOfBirth", "dob");
          case "gender" -> first(a, "gender");
          case "aadhaar" -> first(a, "aadhaar", "aadhaarNumber");
          case "category" -> first(a, "category", "socialCategory");
          case "className" -> splitClass(classSection(a))[0];
          case "admissionNo" -> admissionNo == null ? "" : admissionNo.trim();
          case "enrollmentType" -> first(a, "enrollmentType", "admissionType");
          case "motherTongue" -> first(a, "motherTongue", "medium");
          case "penNumber" -> first(a, "penNumber", "pen");
          case "apaarId" -> first(a, "apaarId", "apaarNumber");
          case "section" -> section(a);
          case "nationality" -> first(a, "nationality");
          case "bloodGroup" -> first(a, "bloodGroup");
          case "caste" -> first(a, "caste");
          case "religion" -> first(a, "religion");
          case "rte" -> rte(a);
          case "bpl" -> yesNo(a, "bpl", "bplStudent");
          case "cwsn" -> yesNo(a, "cwsn", "disability", "isDisabled");
          case "disabilityRemark" -> first(a, "disabilityRemark", "disabilityDetails");
          case "fatherName" -> fatherName(a);
          case "fatherAadhaar" -> first(a, "fatherAadhaar", "fatherAadhaarNumber");
          case "motherName" -> first(a, "motherName");
          case "motherAadhaar" -> first(a, "motherAadhaar", "motherAadhaarNumber");
          case "guardianName" -> first(a, "guardianName");
          case "admissionDate" -> first(a, "admissionDate", "dateOfAdmission");
          case "address" -> first(a, "address", "addressLine1");
          case "city" -> first(a, "city");
          case "state" -> first(a, "state");
          case "pincode" -> first(a, "pinCode", "pincode");
          case "mobile" -> first(a, "mobile", "mobileNo");
          case "bankAccountHolder" -> first(a, "bankAccountHolder", "accountHolderName");
          case "bankName" -> first(a, "bankName");
          case "bankAccountNo" -> first(a, "bankAccountNo", "accountNumber");
          case "ifsc" -> first(a, "ifsc", "ifscCode");
          case "governmentStudentId" -> first(a, "governmentStudentId", "schoolStudentId");
          case "familyId" -> first(a, "familyId", "householdId");
          case "samagraId" -> first(a, "samagraId");
          default -> "";
        };
    return guard(value);
  }

  public static String csv(String value) {
    String s = value == null ? "" : value;
    if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  public static String header(List<String> keys) {
    Map<String, String> labels = new LinkedHashMap<>();
    for (Column column : ALL) {
      labels.put(column.key(), column.label());
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < keys.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(csv(labels.getOrDefault(keys.get(i), keys.get(i))));
    }
    return sb.toString();
  }

  private static Column col(String key, String label, boolean yesNo) {
    return new Column(key, label, yesNo);
  }

  private static String classSection(Map<String, Object> answers) {
    return first(answers, "classSection", "classApplied", "className");
  }

  private static String section(Map<String, Object> answers) {
    String direct = first(answers, "section");
    if (!direct.isEmpty()) {
      return direct;
    }
    return splitClass(classSection(answers))[1];
  }

  private static String[] splitClass(String classSection) {
    if (classSection == null || classSection.isBlank()) {
      return new String[] {"", ""};
    }
    String s = classSection.trim();
    int dash = s.lastIndexOf('-');
    if (dash > 0 && dash < s.length() - 1) {
      return new String[] {s.substring(0, dash).trim(), s.substring(dash + 1).trim()};
    }
    int space = s.lastIndexOf(' ');
    if (space > 0 && s.substring(space + 1).length() <= 3) {
      return new String[] {s.substring(0, space).trim(), s.substring(space + 1).trim()};
    }
    return new String[] {s, ""};
  }

  private static String fatherName(Map<String, Object> answers) {
    String direct = first(answers, "fatherName", "parentName");
    if (!direct.isEmpty()) {
      return direct;
    }
    return guardianByRelation(answers, "father", "dad", "papa");
  }

  private static String guardianByRelation(Map<String, Object> answers, String... needles) {
    Object guardians = answers.get("guardians");
    if (!(guardians instanceof List<?> list)) {
      return "";
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        continue;
      }
      String relation = text(map.get("relation")).toLowerCase(Locale.ROOT);
      for (String needle : needles) {
        if (relation.contains(needle)) {
          String name = text(map.get("fullName"));
          if (name.isEmpty()) {
            name = text(map.get("name"));
          }
          if (!name.isEmpty()) {
            return name;
          }
        }
      }
    }
    return "";
  }

  private static String rte(Map<String, Object> answers) {
    String marked = yesNo(answers, "rte");
    if (!marked.isEmpty()) {
      return marked;
    }
    String category = first(answers, "category", "socialCategory");
    if ("RTE".equalsIgnoreCase(category)) {
      return "Yes";
    }
    return "";
  }

  private static String yesNo(Map<String, Object> answers, String... keys) {
    for (String key : keys) {
      if (!answers.containsKey(key) || answers.get(key) == null) {
        continue;
      }
      String raw = text(answers.get(key));
      if (raw.isEmpty()) {
        continue;
      }
      String s = raw.toLowerCase(Locale.ROOT);
      if ("true".equals(s) || "yes".equals(s) || "y".equals(s) || "1".equals(s)) {
        return "Yes";
      }
      if ("false".equals(s) || "no".equals(s) || "n".equals(s) || "0".equals(s)) {
        return "No";
      }
    }
    return "";
  }

  private static String first(Map<String, Object> answers, String... keys) {
    for (String key : keys) {
      String value = text(answers.get(key));
      if (!value.isEmpty()) {
        return value;
      }
    }
    return "";
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }

  private static String guard(String value) {
    if (value.isEmpty()) {
      return "";
    }
    char c = value.charAt(0);
    if (c == '=' || c == '+' || c == '-' || c == '@') {
      return "'" + value;
    }
    return value;
  }
}
