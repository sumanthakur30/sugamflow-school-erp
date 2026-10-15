package com.sugamflow.school.fee.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.sugamflow.school.fee.finance.FeeStructureMatcher.Candidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeeStructureMatcherTest {

  private static final List<Candidate> STRUCTURES =
      List.of(
          new Candidate("grade_8", "Grade 8", "2025-26"),
          new Candidate("grade_9", "Grade 9", "2025-26"),
          new Candidate("grade_9a", "Grade 9-A", "2025-26"),
          new Candidate("grade_10", "Grade 10", "2025-26"),
          new Candidate("grade_9_old", "Grade 9", "2024-25"));

  @Test
  void eachClassKeepsItsOwnStructure() {
    assertEquals("grade_8", FeeStructureMatcher.pick(STRUCTURES, "Grade 8", "2025-26"));
    assertEquals("grade_10", FeeStructureMatcher.pick(STRUCTURES, "Grade 10", "2025-26"));
    assertEquals("grade_9", FeeStructureMatcher.pick(STRUCTURES, "Grade 9", "2025-26"));
  }

  @Test
  void sectionOverrideBeatsClassStructure() {
    assertEquals("grade_9a", FeeStructureMatcher.pick(STRUCTURES, "Grade 9-A", "2025-26"));
  }

  @Test
  void academicYearWinsWhenThatYearIsConfigured() {
    assertEquals("grade_9_old", FeeStructureMatcher.pick(STRUCTURES, "Grade 9", "2024-25"));
    assertEquals("grade_9", FeeStructureMatcher.pick(STRUCTURES, "Grade 9", "2025-26"));
  }

  @Test
  void classStructureIsUsedWhenTheYearLabelDoesNotMatchAnyStructure() {
    assertEquals("grade_9", FeeStructureMatcher.pick(STRUCTURES, "Grade 9", "session-uuid"));
  }

  @Test
  void grade10DoesNotMatchGrade1() {
    List<Candidate> structures =
        List.of(new Candidate("grade_1", "Grade 1", "2025-26"), new Candidate("grade_10", "Grade 10", "2025-26"));
    assertEquals("grade_10", FeeStructureMatcher.pick(structures, "Grade 10", "2025-26"));
    assertEquals("grade_1", FeeStructureMatcher.pick(structures, "Grade 1", "2025-26"));
  }

  @Test
  void blankStudentClassDoesNotGuess() {
    assertNull(FeeStructureMatcher.pick(STRUCTURES, "  ", "2025-26"));
  }
}
