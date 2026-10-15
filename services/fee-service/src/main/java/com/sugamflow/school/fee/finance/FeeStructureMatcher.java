package com.sugamflow.school.fee.finance;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Picks the fee structure for a student's grade and academic year.
 * Section is ignored for every grade: Grade 9-C uses the Grade 9 structure, Grade 1-A uses Grade 1.
 * When both "Grade 9" and "Grade 9-C" exist, the structure saved without a section wins.
 * A different grade is never used as a substitute.
 */
public final class FeeStructureMatcher {

  public record Candidate(String definitionKey, String classSection, String academicSessionId) {}

  private static final Pattern SECTION = Pattern.compile("(?:[\\s-]+[a-z]|(?<=\\d)[a-z])$");

  private FeeStructureMatcher() {}

  public static String pick(List<Candidate> candidates, String studentClass, String sessionId) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    String student = norm(studentClass);
    if (student.isEmpty()) {
      return null;
    }
    String studentGrade = gradeKey(student);
    String session = norm(sessionId);
    boolean yearSpecific = false;
    if (!session.isEmpty()) {
      for (Candidate candidate : candidates) {
        if (candidate != null && session.equals(norm(candidate.academicSessionId()))) {
          yearSpecific = true;
          break;
        }
      }
    }
    Candidate best = null;
    int bestScore = -1;
    for (Candidate candidate : candidates) {
      if (candidate == null || candidate.definitionKey() == null || candidate.definitionKey().isBlank()) {
        continue;
      }
      String configured = norm(candidate.classSection());
      if (configured.isEmpty()) {
        continue;
      }
      String configuredSession = norm(candidate.academicSessionId());
      if (yearSpecific && !configuredSession.isEmpty() && !configuredSession.equals(session)) {
        continue;
      }
      String configuredGrade = gradeKey(configured);
      int score;
      if (!studentGrade.isEmpty() && studentGrade.equals(configuredGrade)) {
        score = 100;
        if (configured.equals(configuredGrade)) {
          score += 20;
        }
      } else if (configured.equals(student)) {
        score = 80;
      } else if (student.startsWith(configured + "-") || student.startsWith(configured + " ")) {
        score = 40;
      } else {
        continue;
      }
      if (!configuredSession.isEmpty() && configuredSession.equals(session)) {
        score += 10;
      }
      boolean better =
          score > bestScore
              || (score == bestScore
                  && best != null
                  && candidate.definitionKey().compareToIgnoreCase(best.definitionKey()) < 0);
      if (better) {
        bestScore = score;
        best = candidate;
      }
    }
    return best == null ? null : best.definitionKey();
  }

  static String gradeKey(String normalized) {
    if (normalized == null || normalized.isEmpty()) {
      return "";
    }
    return SECTION.matcher(normalized).replaceFirst("");
  }

  private static String norm(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
