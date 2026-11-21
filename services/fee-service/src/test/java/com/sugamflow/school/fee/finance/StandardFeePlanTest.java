package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StandardFeePlanTest {

  private static final List<StandardFeePlan.Category> FEES =
      List.of(
          new StandardFeePlan.Category("ADMISSION", "Admission Fee", "OT"),
          new StandardFeePlan.Category("TUITION", "Tuition Fee", "M"));

  @Test
  void oneFeeListIsStoredWithADifferentAmountForEachClass() {
    StandardFeePlan.Result result =
        StandardFeePlan.build(
            "2026-27",
            FEES,
            List.of(
                draft("Nursery", line("ADMISSION", 2000, true), line("TUITION", 800, true)),
                draft("Class 1", line("ADMISSION", 2500, true), line("TUITION", 1200, false))),
            List.of());

    assertEquals(2, result.heads().size());
    assertEquals(2, result.structures().size());
    assertEquals("std_nursery", result.structures().get(0).get("definitionKey"));
    assertEquals("std_class_1", result.structures().get(1).get("definitionKey"));
    Map<?, ?> classOne = line(result.structures().get(1), "TUITION");
    assertEquals(new BigDecimal("1200"), classOne.get("amount"));
    assertEquals(false, classOne.get("active"));
    assertEquals("M", classOne.get("frequency"));
    assertTrue(result.retireKeys().isEmpty());
  }

  @Test
  void clearingAClassRetiresItsStructureAndLeavesAnotherYearAlone() {
    StandardFeePlan.Existing current =
        new StandardFeePlan.Existing("std_nursery", "Nursery", "2026-27", Map.of());
    StandardFeePlan.Existing previous =
        new StandardFeePlan.Existing(
            "std_nursery_old",
            "Nursery",
            "2025-26",
            Map.of("eligibility", Map.of("classSection", "Nursery", "academicSessionId", "2025-26")));
    StandardFeePlan.Existing admission =
        new StandardFeePlan.Existing("admission_pack", "", "", Map.of());

    StandardFeePlan.Result result =
        StandardFeePlan.build(
            "2026-27",
            FEES,
            List.of(draft("Nursery", line("TUITION", 0, false))),
            List.of(current, previous, admission));

    assertTrue(result.structures().isEmpty());
    assertEquals(List.of("std_nursery"), result.retireKeys());
  }

  @Test
  void anUnchangedClassIsNotWrittenAgain() {
    Map<String, Object> payload =
        Map.of(
            "definitionKey",
            "std_nursery",
            "eligibility",
            Map.of("classSection", "Nursery", "academicSessionId", "2026-27"),
            "lines",
            List.of(
                Map.of(
                    "headKey",
                    "TUITION",
                    "amount",
                    800,
                    "frequency",
                    "M",
                    "active",
                    true,
                    "discountAllowed",
                    true)));
    StandardFeePlan.Result result =
        StandardFeePlan.build(
            "2026-27",
            List.of(new StandardFeePlan.Category("TUITION", "Tuition Fee", "M")),
            List.of(draft("Nursery", line("TUITION", 800, true))),
            List.of(new StandardFeePlan.Existing("std_nursery", "Nursery", "2026-27", payload)));

    assertTrue(result.structures().isEmpty());
    assertTrue(result.retireKeys().isEmpty());
  }

  @Test
  void aDifferentAcademicYearKeepsItsOwnStructure() {
    StandardFeePlan.Result result =
        StandardFeePlan.build(
            "2026-27",
            List.of(new StandardFeePlan.Category("TUITION", "Tuition Fee", "M")),
            List.of(draft("Nursery", line("TUITION", 900, true))),
            List.of(new StandardFeePlan.Existing("std_nursery", "Nursery", "2025-26", Map.of())));

    assertEquals("std_nursery_2", result.structures().get(0).get("definitionKey"));
    assertTrue(result.retireKeys().isEmpty());
  }

  @Test
  void anActiveFeeWithoutAnAmountIsRejected() {
    FeeException error =
        assertThrows(
            FeeException.class,
            () ->
                StandardFeePlan.build(
                    "2026-27",
                    FEES,
                    List.of(draft("LKG", line("ADMISSION", 0, true))),
                    List.of()));
    assertTrue(error.getMessage().contains("Admission Fee"));
    assertTrue(error.getMessage().contains("LKG"));
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> line(Map<String, Object> structure, String head) {
    for (Object item : (List<?>) structure.get("lines")) {
      Map<String, Object> line = (Map<String, Object>) item;
      if (head.equals(line.get("headKey"))) {
        return line;
      }
    }
    throw new AssertionError(head);
  }

  private static StandardFeePlan.ClassDraft draft(String className, StandardFeePlan.LineDraft... lines) {
    return new StandardFeePlan.ClassDraft(className, List.of(lines));
  }

  private static StandardFeePlan.LineDraft line(String head, int amount, boolean active) {
    return new StandardFeePlan.LineDraft(head, BigDecimal.valueOf(amount), active);
  }
}
