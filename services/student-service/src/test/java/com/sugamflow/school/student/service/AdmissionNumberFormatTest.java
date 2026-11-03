package com.sugamflow.school.student.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdmissionNumberFormatTest {

  @Test
  void schoolFormatUsesPrefixYearAndSequence() {
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("admissionNoPrefix", "HCP");
    settings.put("admissionNoSeparator", "/");
    settings.put("admissionNoYearDigits", 2);
    settings.put("admissionNoIncludeBranch", false);
    settings.put("admissionNoWidth", 4);

    assertEquals(
        "HCP/26/",
        AdmissionNumberFormat.stem(settings, "MAIN", LocalDate.of(2026, 11, 3)));
    assertEquals(
        "HCP/26/0001",
        AdmissionNumberFormat.format(settings, "MAIN", 1, LocalDate.of(2026, 11, 3)));
  }

  @Test
  void defaultFormatKeepsYearAndBranch() {
    Map<String, Object> settings = new LinkedHashMap<>();
    settings.put("admissionNoPrefix", "ADM");
    settings.put("admissionNoSeparator", "-");
    settings.put("admissionNoYearDigits", 4);
    settings.put("admissionNoIncludeBranch", true);
    settings.put("admissionNoWidth", 4);

    assertEquals(
        "ADM-2026-MAIN-0012",
        AdmissionNumberFormat.format(settings, "main", 12, LocalDate.of(2026, 1, 1)));
  }

  @Test
  void sequenceGrowsPastThePadWidth() {
    Map<String, Object> settings = Map.of("admissionNoPrefix", "ADM", "admissionNoWidth", 4);
    assertEquals(
        "ADM-2026-MAIN-10000",
        AdmissionNumberFormat.format(settings, "MAIN", 10000, LocalDate.of(2026, 1, 1)));
  }
}
