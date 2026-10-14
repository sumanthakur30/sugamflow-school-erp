package com.sugamflow.school.student.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StudentSheetCatalogTest {

  @Test
  void templateHeadersIncludeDemographicsAndOmitAadhaarFromDirectory() {
    List<String> labels = StudentSheetCatalog.columns().stream().map(StudentSheetCatalog.Col::label).toList();
    assertTrue(labels.contains("Gender"));
    assertTrue(labels.contains("Date of Birth"));
    assertTrue(labels.contains("Blood Group"));
    assertTrue(labels.contains("Father Name"));
    assertTrue(labels.contains("Mother Name"));
    assertTrue(labels.contains("Aadhaar Number"));
    List<String> directory =
        StudentSheetCatalog.directoryColumns().stream().map(StudentSheetCatalog.Col::key).toList();
    assertFalse(directory.contains("aadhaar"));
    assertTrue(directory.contains("gender"));
    assertTrue(directory.contains("fatherName"));
    assertTrue(directory.contains("pinCode"));
  }

  @Test
  void mapsAliasesAndRoundsGenderParentsAndClass() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("Father's Name", "Ravi Rao");
    raw.put("Mother's Name", "Meera Rao");
    raw.put("DOB", "12/05/2014");
    raw.put("Blood Group", "o +");
    raw.put("Gender", "F");
    raw.put("Full Name", "Asha Rao");
    raw.put("Mobile", "9800000001");
    raw.put("Grade", "7");
    raw.put("Section", "a");
    raw.put("Photo URL", "https://example.test/photo.jpg");

    Map<String, Object> mapped = StudentSheetCatalog.mapRow(raw, null);
    StudentSheetCatalog.normalize(mapped);
    assertEquals("Female", mapped.get("gender"));
    assertEquals("O+", mapped.get("bloodGroup"));
    assertEquals("2014-05-12", mapped.get("dateOfBirth"));
    assertEquals("Ravi Rao", mapped.get("fatherName"));
    assertEquals("Meera Rao", mapped.get("motherName"));
    assertEquals("Grade 7-A", mapped.get("classApplied"));
    assertFalse(mapped.containsKey("photoUrl"));
    assertNull(StudentSheetCatalog.validate(mapped, "CREATE", "BR-1", "2026-27"));

    StudentGuardianSync.syncFromFlat(mapped);
    assertEquals("Ravi Rao", StudentGuardianSync.nameForRelation(mapped, "father"));
    assertEquals("Meera Rao", StudentGuardianSync.nameForRelation(mapped, "mother"));
  }

  @Test
  void blankCellsAreOmittedSoAnUpdateCanKeepStoredValues() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("Admission No", "ADM-1");
    raw.put("Full Name", "Asha Rao");
    raw.put("Gender", "Male");
    raw.put("Father Name", "   ");
    raw.put("Blood Group", "");
    Map<String, Object> incoming = StudentSheetCatalog.mapRow(raw, StudentSheetCatalog.defaultMapping());
    assertEquals("Male", incoming.get("gender"));
    assertFalse(incoming.containsKey("fatherName"));
    assertFalse(incoming.containsKey("bloodGroup"));

    Map<String, Object> stored = new LinkedHashMap<>();
    stored.put("gender", "Female");
    stored.put("fatherName", "Ravi Rao");
    stored.put("bloodGroup", "A+");
    for (Map.Entry<String, Object> entry : incoming.entrySet()) {
      if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
        stored.put(entry.getKey(), entry.getValue());
      }
    }
    assertEquals("Male", stored.get("gender"));
    assertEquals("Ravi Rao", stored.get("fatherName"));
    assertEquals("A+", stored.get("bloodGroup"));
  }

  @Test
  void flagsARepeatedAdmissionNumberAndKeepsBlankOnes() {
    Set<String> seen = new HashSet<>();
    assertNull(StudentSheetCatalog.noteDuplicateAdmission(seen, "ADM-9"));
    assertEquals(
        "Duplicate admission number in this file",
        StudentSheetCatalog.noteDuplicateAdmission(seen, " adm-9 "));
    assertNull(StudentSheetCatalog.noteDuplicateAdmission(seen, " "));
  }

  @Test
  void oldEightColumnCsvStillMaps() {
    Map<String, Object> raw = new LinkedHashMap<>();
    raw.put("fullName", "Asha Rao");
    raw.put("admissionNo", "ADM-8");
    raw.put("age", "12");
    raw.put("mobile", "9800000001");
    raw.put("email", "asha@example.com");
    raw.put("classApplied", "Grade 8-A");
    raw.put("gender", "girl");
    raw.put("rollNo", "1");
    Map<String, Object> mapped = StudentSheetCatalog.mapRow(raw, StudentSheetCatalog.defaultMapping());
    StudentSheetCatalog.normalize(mapped);
    assertEquals("Asha Rao", mapped.get("fullName"));
    assertEquals("Female", mapped.get("gender"));
    assertEquals("Grade 8-A", mapped.get("classApplied"));
    assertEquals("1", mapped.get("rollNo"));
    assertNull(StudentSheetCatalog.validate(mapped, "CREATE", null, null));
  }

  @Test
  void rejectsInvalidClassSectionBranchAndFormulaText() {
    Map<String, Object> badSection = row("Asha Rao", "9800000001");
    badSection.put("classApplied", "Grade 7");
    badSection.put("sectionLetter", "A-1");
    assertEquals("Section is invalid", StudentSheetCatalog.validate(badSection, "CREATE", "BR", "SES"));

    Map<String, Object> sectionWithoutClass = row("Asha Rao", "9800000001");
    sectionWithoutClass.put("admissionNo", "ADM-1");
    sectionWithoutClass.put("sectionLetter", "A");
    assertEquals(
        "Class is required when Section is set",
        StudentSheetCatalog.validate(sectionWithoutClass, "UPDATE", "BR", "SES"));

    Map<String, Object> badClass = row("Asha Rao", "9800000001");
    badClass.put("classApplied", "---");
    assertEquals("Class is invalid", StudentSheetCatalog.validate(badClass, "CREATE", "BR", "SES"));

    Map<String, Object> otherBranch = row("Asha Rao", "9800000001");
    otherBranch.put("classApplied", "Grade 7-A");
    otherBranch.put("branchId", "OTHER");
    assertEquals(
        "Branch does not match the signed-in school",
        StudentSheetCatalog.validate(otherBranch, "CREATE", "BR", "SES"));

    assertEquals("'=cmd", StudentSheetCatalog.formulaSafe("=cmd"));
    assertEquals("", StudentSheetCatalog.exportPhoto("https://example.test/a.jpg"));
    assertTrue(StudentSheetCatalog.exportPhoto("/api/student/attachments/1/content").startsWith("/api/"));
  }

  private static Map<String, Object> row(String name, String mobile) {
    Map<String, Object> mapped = new LinkedHashMap<>();
    mapped.put("fullName", name);
    mapped.put("mobile", mobile);
    return mapped;
  }
}
