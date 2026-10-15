package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.fee.web.FeeException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeeStructureRulesTest {

  @Test
  void aStructureCanContainOnlyTheHeadsASchoolSelects() {
    assertDoesNotThrow(
        () ->
            FeeStructureRules.validate(
                Map.of(
                    "lines",
                    List.of(
                        Map.of("headKey", "TUITION", "amount", 2000, "frequency", "M"),
                        Map.of("headKey", "ANNUAL", "amount", 5000, "frequency", "Y")))));
  }

  @Test
  void oneActiveFeeHeadIsEnough() {
    assertDoesNotThrow(
        () ->
            FeeStructureRules.validate(
                Map.of("lines", List.of(Map.of("headKey", "TUITION", "amount", 1800, "frequency", "M")))));
  }

  @Test
  void duplicateNegativeAndUnknownFrequencyAreRejected() {
    FeeException duplicate =
        assertThrows(
            FeeException.class,
            () ->
                FeeStructureRules.validate(
                    Map.of(
                        "lines",
                        List.of(
                            Map.of("headKey", "TUITION", "amount", 1, "frequency", "M"),
                            Map.of("headKey", "tuition", "amount", 2, "frequency", "M")))));
    assertTrue(duplicate.getMessage().contains("Duplicate"));

    FeeException negative =
        assertThrows(
            FeeException.class,
            () ->
                FeeStructureRules.validate(
                    Map.of("lines", List.of(Map.of("headKey", "ANNUAL", "amount", -1, "frequency", "Y")))));
    assertTrue(negative.getMessage().contains("negative"));

    FeeException frequency =
        assertThrows(
            FeeException.class,
            () ->
                FeeStructureRules.validate(
                    Map.of("lines", List.of(Map.of("headKey", "LAB", "amount", 100, "frequency", "WEEKLY")))));
    assertTrue(frequency.getMessage().contains("Frequency"));
  }

  @Test
  void aCustomHeadIsAcceptedWithoutForcingTheStandardList() {
    assertDoesNotThrow(
        () ->
            FeeStructureRules.validate(
                Map.of(
                    "lines",
                    List.of(Map.of("headKey", "ROBOTICS", "amount", 750, "frequency", "Q", "discountAllowed", false)))));
  }
}
