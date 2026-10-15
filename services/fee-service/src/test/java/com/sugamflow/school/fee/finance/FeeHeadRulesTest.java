package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sugamflow.school.fee.web.FeeException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FeeHeadRulesTest {

  @Test
  void rejectsASecondHeadWithTheSameName() {
    List<Map<String, Object>> heads =
        List.of(Map.of("definitionKey", "LAB", "label", "Science Lab Fee"));
    FeeException error =
        assertThrows(
            FeeException.class,
            () -> FeeHeadRules.validateUniqueLabel("LAB_110205", "science lab fee", heads));
    assertEquals("A fee head named \"science lab fee\" already exists", error.getMessage());
  }

  @Test
  void allowsTheSameHeadToKeepItsName() {
    List<Map<String, Object>> heads = List.of(Map.of("definitionKey", "LAB", "label", "Science Lab Fee"));
    FeeHeadRules.validateUniqueLabel("LAB", "Science Lab Fee", heads);
  }

  @Test
  void catalogHeadNamesAreUnique() {
    long names =
        FinanceCatalog.defaultHeads().stream()
            .map(head -> FeeHeadRules.normalize(String.valueOf(head.get("label"))))
            .distinct()
            .count();
    assertEquals(FinanceCatalog.defaultHeads().size(), names);
  }
}
