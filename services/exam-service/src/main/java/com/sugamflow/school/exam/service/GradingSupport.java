package com.sugamflow.school.exam.service;

import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.exam.persistence.entity.GradingSchemeEntity;
import com.sugamflow.school.exam.persistence.repo.GradingSchemeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Resolves percentage, grade and pass/fail from a school scheme, or the built-in bands. */
@Component
public class GradingSupport {

  private final GradingSchemeRepository schemes;

  public GradingSupport(GradingSchemeRepository schemes) {
    this.schemes = schemes;
  }

  public Map<String, Object> assess(TenantScope scope, BigDecimal obtained, BigDecimal max) {
    BigDecimal pct = percentage(obtained, max);
    List<Map<String, Object>> bands = bandsFor(scope);
    return apply(pct, bands);
  }

  public List<Map<String, Object>> bandsFor(TenantScope scope) {
    if (schemes == null || scope == null) {
      return defaultBands();
    }
    List<GradingSchemeEntity> found =
        schemes.findByOrganizationIdAndActiveTrueOrderByUpdatedAtDesc(scope.organizationId());
    if (found == null || found.isEmpty()) {
      return defaultBands();
    }
    String session = scope.academicSessionId();
    GradingSchemeEntity chosen = null;
    for (GradingSchemeEntity scheme : found) {
      if (session != null
          && !session.isBlank()
          && session.equals(scheme.getAcademicSessionId())) {
        chosen = scheme;
        break;
      }
    }
    if (chosen == null) {
      for (GradingSchemeEntity scheme : found) {
        if (scheme.getAcademicSessionId() == null || scheme.getAcademicSessionId().isBlank()) {
          chosen = scheme;
          break;
        }
      }
    }
    if (chosen == null) {
      chosen = found.get(0);
    }
    if (chosen.getBands() == null || chosen.getBands().isEmpty()) {
      return defaultBands();
    }
    return chosen.getBands();
  }

  public static Map<String, Object> apply(BigDecimal pct, List<Map<String, Object>> bands) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("percentage", pct);
    if (pct == null) {
      out.put("grade", null);
      out.put("gradePoint", null);
      out.put("pass", null);
      return out;
    }
    List<Map<String, Object>> ordered = new ArrayList<>(bands == null ? defaultBands() : bands);
    ordered.sort(
        Comparator.comparing(
                (Map<String, Object> b) -> toDecimal(b.get("minPercentage")))
            .reversed());
    Map<String, Object> match = ordered.isEmpty() ? null : ordered.get(ordered.size() - 1);
    for (Map<String, Object> band : ordered) {
      if (pct.compareTo(toDecimal(band.get("minPercentage"))) >= 0) {
        match = band;
        break;
      }
    }
    if (match == null) {
      out.put("grade", null);
      out.put("gradePoint", null);
      out.put("pass", null);
      return out;
    }
    out.put("grade", match.get("grade"));
    out.put("gradePoint", match.get("gradePoint"));
    Object pass = match.get("pass");
    out.put("pass", pass == null ? !"F".equals(String.valueOf(match.get("grade"))) : Boolean.TRUE.equals(pass) || "true".equalsIgnoreCase(String.valueOf(pass)));
    return out;
  }

  public static BigDecimal percentage(BigDecimal obtained, BigDecimal max) {
    if (obtained == null || max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
      return null;
    }
    return obtained.multiply(new BigDecimal("100")).divide(max, 2, RoundingMode.HALF_UP);
  }

  public static List<Map<String, Object>> defaultBands() {
    List<Map<String, Object>> bands = new ArrayList<>();
    bands.add(band(90, "A+", 10, true));
    bands.add(band(80, "A", 9, true));
    bands.add(band(70, "B+", 8, true));
    bands.add(band(60, "B", 7, true));
    bands.add(band(50, "C", 6, true));
    bands.add(band(40, "D", 5, true));
    bands.add(band(33, "E", 4, true));
    bands.add(band(0, "F", 0, false));
    return bands;
  }

  private static Map<String, Object> band(int min, String grade, int point, boolean pass) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("minPercentage", min);
    m.put("grade", grade);
    m.put("gradePoint", point);
    m.put("pass", pass);
    return m;
  }

  private static BigDecimal toDecimal(Object raw) {
    if (raw == null) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(String.valueOf(raw).trim());
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }
}
