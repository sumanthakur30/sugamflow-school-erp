package com.sugamflow.school.fee.finance;

import java.util.List;
import java.util.Locale;

/**
 * Picks the fee structure for a student's class and academic year.
 * A section structure (Grade 9-A) beats the class structure (Grade 9).
 * A different class is never used as a substitute.
 */
public final class FeeStructureMatcher {

  public record Candidate(String definitionKey, String classSection, String academicSessionId) {}

  private FeeStructureMatcher() {}

  public static String pick(List<Candidate> candidates, String studentClass, String sessionId) {
    if (candidates == null || candidates.isEmpty()) {
      return null;
    }
    String student = norm(studentClass);
    if (student.isEmpty()) {
      return null;
    }
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
      int score;
      if (configured.equals(student)) {
        score = 100;
      } else if (student.startsWith(configured + "-") || student.startsWith(configured + " ")) {
        score = 40;
      } else {
        continue;
      }
      if (!configuredSession.isEmpty() && configuredSession.equals(session)) {
        score += 10;
      }
      score += Math.min(configured.length(), 30);
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    return best == null ? null : best.definitionKey();
  }

  private static String norm(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
