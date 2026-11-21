package com.sugamflow.school.fee.finance;

import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns one class-wise fee grid into fee-head and fee-structure versions.
 * Issued demands are stored on finance transactions and are not part of this plan.
 */
public final class StandardFeePlan {

  public record Category(String headKey, String label, String frequency) {}

  public record LineDraft(String headKey, BigDecimal amount, boolean active) {}

  public record ClassDraft(String className, List<LineDraft> lines) {}

  public record Existing(
      String definitionKey, String classSection, String academicSessionId, Map<String, Object> payload) {}

  public record Result(
      List<Category> heads, List<Map<String, Object>> structures, List<String> retireKeys) {}

  private static final Set<String> FREQUENCIES =
      Set.of(
          FinanceCatalog.FREQ_MONTHLY,
          FinanceCatalog.FREQ_QUARTERLY,
          FinanceCatalog.FREQ_HALF_YEARLY,
          FinanceCatalog.FREQ_YEARLY,
          FinanceCatalog.FREQ_ONE_TIME);

  private StandardFeePlan() {}

  public static Result build(
      String sessionId, List<Category> categories, List<ClassDraft> classes, List<Existing> existing) {
    if (classes == null || classes.isEmpty()) {
      throw new FeeException("VALIDATION", "Add classes before saving the fee structure");
    }
    Map<String, Category> byHead = new LinkedHashMap<>();
    for (Category category : categories == null ? List.<Category>of() : categories) {
      if (category == null || category.headKey() == null || category.headKey().isBlank()) {
        throw new FeeException("VALIDATION", "Each fee needs a name");
      }
      String key = category.headKey().trim().toUpperCase(Locale.ROOT);
      String label = category.label() == null ? "" : category.label().trim();
      if (label.isBlank()) {
        throw new FeeException("VALIDATION", "Each fee needs a name");
      }
      String frequency = category.frequency() == null ? "" : category.frequency().trim().toUpperCase(Locale.ROOT);
      if (!FREQUENCIES.contains(frequency)) {
        throw new FeeException("VALIDATION", "Collection frequency for " + label + " is not supported");
      }
      if (byHead.put(key, new Category(key, label, frequency)) != null) {
        throw new FeeException("VALIDATION", "Duplicate fee " + label);
      }
    }
    Set<String> classNames = new LinkedHashSet<>();
    for (ClassDraft draft : classes) {
      String name = draft == null || draft.className() == null ? "" : draft.className().trim();
      if (name.isBlank()) {
        throw new FeeException("VALIDATION", "Each column needs a class");
      }
      if (!classNames.add(norm(name))) {
        throw new FeeException("VALIDATION", name + " is listed twice");
      }
    }

    List<Existing> rows = existing == null ? List.of() : existing;
    Set<String> taken = new LinkedHashSet<>();
    for (Existing row : rows) {
      if (row != null && row.definitionKey() != null && !row.definitionKey().isBlank()) {
        taken.add(row.definitionKey());
      }
    }
    Set<String> used = new LinkedHashSet<>();
    List<Map<String, Object>> structures = new ArrayList<>();
    List<String> retire = new ArrayList<>();
    List<ClassDraft> clearing = new ArrayList<>();

    for (ClassDraft draft : classes) {
      if (activeCount(draft, byHead) == 0) {
        clearing.add(draft);
        continue;
      }
      String key = reuseKey(draft.className().trim(), sessionId, rows, used);
      if (key == null) {
        key = freshKey(draft.className().trim(), taken);
        taken.add(key);
      }
      used.add(key);
      Existing current = find(rows, key);
      Map<String, Object> payload = structurePayload(draft, byHead, key, sessionId, current);
      FeeStructureRules.validate(payload);
      if (current == null || !same(current.payload(), payload)) {
        structures.add(payload);
      }
    }

    for (ClassDraft draft : clearing) {
      String key = reuseKey(draft.className().trim(), sessionId, rows, used);
      if (key != null && used.add(key)) {
        retire.add(key);
      }
    }
    return new Result(List.copyOf(byHead.values()), structures, retire);
  }

  @SuppressWarnings("unchecked")
  public static List<Category> readCategories(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Category> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new FeeException("VALIDATION", "Each fee needs a name and a collection frequency");
      }
      out.add(
          new Category(
              text(map.get("headKey")),
              text(map.get("label")),
              text(map.get("frequency"))));
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  public static List<ClassDraft> readClasses(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<ClassDraft> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> map)) {
        throw new FeeException("VALIDATION", "Each class fee needs a class name");
      }
      List<LineDraft> lines = new ArrayList<>();
      if (map.get("lines") instanceof List<?> rawLines) {
        for (Object lineItem : rawLines) {
          if (!(lineItem instanceof Map<?, ?> line)) {
            throw new FeeException("VALIDATION", "Each fee amount needs a fee and a number");
          }
          lines.add(
              new LineDraft(text(line.get("headKey")), money(line.get("amount")), flagged(line.get("active"), true)));
        }
      }
      out.add(new ClassDraft(text(map.get("className")), lines));
    }
    return out;
  }

  private static int activeCount(ClassDraft draft, Map<String, Category> byHead) {
    int active = 0;
    Set<String> seen = new LinkedHashSet<>();
    for (LineDraft line : draft.lines() == null ? List.<LineDraft>of() : draft.lines()) {
      String head = line.headKey() == null ? "" : line.headKey().trim().toUpperCase(Locale.ROOT);
      if (head.isBlank()) {
        continue;
      }
      Category category = byHead.get(head);
      if (category == null) {
        throw new FeeException("VALIDATION", "Add " + head + " before entering its amount");
      }
      if (!seen.add(head)) {
        throw new FeeException("VALIDATION", "Duplicate fee " + category.label() + " for " + draft.className());
      }
      if (line.amount().compareTo(BigDecimal.ZERO) < 0) {
        throw new FeeException("VALIDATION", "Amount for " + category.label() + " cannot be negative");
      }
      if (line.active()) {
        if (line.amount().compareTo(BigDecimal.ZERO) <= 0) {
          throw new FeeException(
              "VALIDATION",
              category.label() + " for " + draft.className().trim() + " needs an amount, or turn it off");
        }
        active++;
      }
    }
    return active;
  }

  private static Map<String, Object> structurePayload(
      ClassDraft draft,
      Map<String, Category> byHead,
      String key,
      String sessionId,
      Existing current) {
    String className = draft.className().trim();
    List<Map<String, Object>> lines = new ArrayList<>();
    for (LineDraft line : draft.lines()) {
      String head = line.headKey() == null ? "" : line.headKey().trim().toUpperCase(Locale.ROOT);
      Category category = byHead.get(head);
      if (category == null || head.isBlank()) {
        continue;
      }
      if (!line.active() && line.amount().compareTo(BigDecimal.ZERO) <= 0) {
        continue;
      }
      Map<String, Object> saved = new LinkedHashMap<>();
      saved.put("headKey", head);
      saved.put("amount", line.amount());
      saved.put("frequency", category.frequency());
      saved.put("active", line.active());
      saved.put("optional", false);
      saved.put("discountAllowed", previousDiscount(current, head));
      lines.add(saved);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("definitionKey", key);
    payload.put("name", className + " fees");
    payload.put("currency", "INR");
    payload.put(
        "eligibility",
        Map.of("classSection", className, "academicSessionId", sessionId == null ? "" : sessionId));
    payload.put("lines", lines);
    payload.put("notes", "Standard fee structure. Receipts and dues already recorded stay as they are.");
    return payload;
  }

  private static String reuseKey(String className, String sessionId, List<Existing> rows, Set<String> used) {
    List<FeeStructureMatcher.Candidate> candidates = new ArrayList<>();
    for (Existing row : rows) {
      if (row == null || row.definitionKey() == null || used.contains(row.definitionKey())) {
        continue;
      }
      candidates.add(
          new FeeStructureMatcher.Candidate(
              row.definitionKey(),
              row.classSection() == null ? "" : row.classSection(),
              row.academicSessionId() == null ? "" : row.academicSessionId()));
    }
    String key = FeeStructureMatcher.pick(candidates, className, sessionId);
    if (key == null) {
      return null;
    }
    Existing row = find(rows, key);
    String rowSession = row == null || row.academicSessionId() == null ? "" : norm(row.academicSessionId());
    String current = norm(sessionId);
    if (!rowSession.isEmpty() && !current.isEmpty() && !rowSession.equals(current)) {
      return null;
    }
    return key;
  }

  private static String freshKey(String className, Set<String> taken) {
    String slug = className.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    if (slug.isBlank()) {
      slug = "class";
    }
    String key = "std_" + slug;
    int n = 2;
    while (taken.contains(key)) {
      key = "std_" + slug + "_" + n++;
    }
    return key;
  }

  private static boolean same(Map<String, Object> existing, Map<String, Object> next) {
    if (existing == null) {
      return false;
    }
    if (!norm(eligibility(existing, "classSection")).equals(norm(eligibility(next, "classSection")))) {
      return false;
    }
    if (!norm(eligibility(existing, "academicSessionId")).equals(norm(eligibility(next, "academicSessionId")))) {
      return false;
    }
    return linesOf(existing).equals(linesOf(next));
  }

  @SuppressWarnings("unchecked")
  private static Set<String> linesOf(Map<String, Object> payload) {
    Set<String> out = new LinkedHashSet<>();
    Object raw = payload.get("lines");
    if (!(raw instanceof List<?> list)) {
      return out;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> line)) {
        continue;
      }
      String head = text(line.get("headKey")).toUpperCase(Locale.ROOT);
      BigDecimal amount = money(line.get("amount"));
      String frequency = text(line.get("frequency")).toUpperCase(Locale.ROOT);
      boolean active = flagged(line.get("active"), true);
      boolean discount = flagged(line.get("discountAllowed"), true);
      out.add(head + "|" + amount.stripTrailingZeros().toPlainString() + "|" + frequency + "|" + active + "|" + discount);
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static boolean previousDiscount(Existing current, String head) {
    if (current == null || current.payload() == null || !(current.payload().get("lines") instanceof List<?> list)) {
      return true;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> line)) {
        continue;
      }
      if (head.equalsIgnoreCase(text(line.get("headKey")))) {
        return flagged(line.get("discountAllowed"), true);
      }
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  private static String eligibility(Map<String, Object> payload, String field) {
    if (payload.get("eligibility") instanceof Map<?, ?> eligibility) {
      return text(eligibility.get(field));
    }
    return "";
  }

  private static Existing find(List<Existing> rows, String key) {
    for (Existing row : rows) {
      if (row != null && key.equals(row.definitionKey())) {
        return row;
      }
    }
    return null;
  }

  private static BigDecimal money(Object raw) {
    if (raw == null || String.valueOf(raw).isBlank()) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(String.valueOf(raw).trim());
    } catch (NumberFormatException ex) {
      throw new FeeException("VALIDATION", "Fee amount must be a number");
    }
  }

  private static boolean flagged(Object raw, boolean whenMissing) {
    if (raw == null || String.valueOf(raw).isBlank()) {
      return whenMissing;
    }
    if (raw instanceof Boolean value) {
      return value;
    }
    return !"false".equalsIgnoreCase(String.valueOf(raw).trim());
  }

  private static String text(Object raw) {
    return raw == null ? "" : String.valueOf(raw).trim();
  }

  private static String norm(String value) {
    return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }
}
