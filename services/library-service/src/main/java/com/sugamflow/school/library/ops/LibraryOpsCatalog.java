package com.sugamflow.school.library.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default library ops configuration — no school-specific hardcoding. */
public final class LibraryOpsCatalog {

  public static final String TYPE_BOOK_CATEGORY = "BOOK_CATEGORY";
  public static final String TYPE_FINE_POLICY = "FINE_POLICY";
  public static final String TYPE_CIRCULATION_POLICY = "CIRCULATION_POLICY";

  public static final String FEATURE_OPS_DEPTH = "FEATURE_OPS_DEPTH";

  private LibraryOpsCatalog() {}

  public static List<Map<String, Object>> defaultCategories() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(category("FICTION", "Fiction", 14));
    list.add(category("REFERENCE", "Reference", 7));
    list.add(category("TEXTBOOK", "Textbook", 30));
    return list;
  }

  public static List<Map<String, Object>> defaultFinePolicies() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> p = new LinkedHashMap<>();
    p.put("definitionKey", "default_fine");
    p.put("name", "Default overdue fine");
    p.put("perDayRate", 5);
    p.put("graceDays", 3);
    p.put("maxFine", 500);
    p.put("currency", "INR");
    list.add(p);
    return list;
  }

  public static List<Map<String, Object>> defaultCirculationPolicies() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("definitionKey", "default_circ");
    c.put("name", "Default circulation");
    c.put("maxBooksPerMember", 3);
    c.put("defaultLoanDays", 14);
    c.put("renewalsAllowed", 1);
    list.add(c);
    return list;
  }

  private static Map<String, Object> category(String key, String label, int loanDays) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("definitionKey", key);
    m.put("label", label);
    m.put("loanDays", loanDays);
    m.put("enabled", true);
    return m;
  }
}
