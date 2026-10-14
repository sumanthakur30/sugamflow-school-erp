package com.sugamflow.school.student.directory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClassLabelGroupsTest {

  @Test
  void groupsSpacingVariantsIntoOneClassAndSection() {
    List<String> labels = new ArrayList<>();
    labels.add("Grade 6-A");
    labels.add("Grade 6 - A");
    labels.add("Grade 6-B");
    labels.add("Grade 6-A");
    labels.add("  ");
    labels.add(null);
    List<ClassLabelGroups.ClassGroup> groups = ClassLabelGroups.group(labels);

    assertEquals(1, groups.size());
    assertEquals("Grade 6", groups.get(0).name());
    assertEquals(List.of("A", "B"), groups.get(0).sections().stream().map(ClassLabelGroups.SectionGroup::name).toList());
    assertEquals("Grade 6-A", groups.get(0).sections().get(0).label());
    assertEquals("Grade 6-B", groups.get(0).sections().get(1).label());
  }

  @Test
  void keepsClassNumbersAndOrdersKnownGrades() {
    List<ClassLabelGroups.ClassGroup> groups =
        ClassLabelGroups.group(List.of("Grade 10", "Nursery", "UKG", "LKG", "Grade 2-A", "VIII-A"));

    assertEquals(
        List.of("Nursery", "LKG", "UKG", "Grade 2", "Grade 10", "VIII"),
        groups.stream().map(ClassLabelGroups.ClassGroup::name).toList());
    assertTrue(groups.get(3).sections().stream().anyMatch(section -> "A".equals(section.name())));
    assertTrue(groups.get(4).sections().isEmpty());
    assertTrue(groups.get(5).sections().stream().anyMatch(section -> "A".equals(section.name())));
  }
}
