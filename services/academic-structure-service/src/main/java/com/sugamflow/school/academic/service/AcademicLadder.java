package com.sugamflow.school.academic.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standard class ladder shared by admission and academic settings.
 * A school's own class row wins when its name means the same step
 * ("Grade 7" and "Class 7" are one class).
 */
public final class AcademicLadder {

  public record Step(String key, String name, int sequence) {}

  private static final Pattern NUMBERED =
      Pattern.compile("^(?:grade|class|std|standard)\\s+(\\d{1,2})$");

  public static final List<Step> STEPS =
      List.of(
          step("nursery", "Nursery", 10),
          step("lkg", "LKG", 20),
          step("ukg", "UKG", 30),
          numbered(1),
          numbered(2),
          numbered(3),
          numbered(4),
          numbered(5),
          numbered(6),
          numbered(7),
          numbered(8),
          numbered(9),
          numbered(10),
          numbered(11),
          numbered(12));

  private AcademicLadder() {}

  /** Stable identity for a class name. Custom names stay distinct from the ladder. */
  public static String key(String raw) {
    if (raw == null) {
      return "";
    }
    String normalized =
        raw.trim().toLowerCase(Locale.ROOT).replaceAll("[._-]+", " ").replaceAll("\\s+", " ");
    if (normalized.isEmpty()) {
      return "";
    }
    if (normalized.equals("nursery") || normalized.equals("pre nursery")) {
      return "nursery";
    }
    if (normalized.equals("lkg") || normalized.equals("lower kindergarten")) {
      return "lkg";
    }
    if (normalized.equals("ukg") || normalized.equals("upper kindergarten")) {
      return "ukg";
    }
    Matcher numbered = NUMBERED.matcher(normalized);
    if (numbered.matches()) {
      return classKey(Integer.parseInt(numbered.group(1)));
    }
    if (normalized.matches("\\d{1,2}")) {
      return classKey(Integer.parseInt(normalized));
    }
    return "custom:" + normalized;
  }

  public static Step find(String key) {
    for (Step step : STEPS) {
      if (step.key().equals(key)) {
        return step;
      }
    }
    return null;
  }

  private static Step numbered(int number) {
    return step("class:" + number, "Class " + number, 30 + (number * 10));
  }

  private static String classKey(int number) {
    if (number < 1 || number > 12) {
      return "custom:" + number;
    }
    return "class:" + number;
  }

  private static Step step(String key, String name, int sequence) {
    return new Step(key, name, sequence);
  }
}
