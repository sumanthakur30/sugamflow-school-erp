package com.sugamflow.school.payroll.ops;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class PayrollOpsCatalog {

  public static final String TYPE_PAY_COMPONENT = "PAY_COMPONENT";
  public static final String TYPE_SALARY_STRUCTURE = "SALARY_STRUCTURE";
  public static final String TYPE_PAY_CYCLE = "PAY_CYCLE";
  public static final String FEATURE_OPS_DEPTH = "FEATURE_OPS_DEPTH";

  private PayrollOpsCatalog() {}

  public static List<Map<String, Object>> defaultComponents() {
    List<Map<String, Object>> list = new ArrayList<>();
    list.add(component("BASIC", "Basic Pay", "EARNING", 0));
    list.add(component("HRA", "House Rent Allowance", "EARNING", 40));
    list.add(component("PF", "Provident Fund", "DEDUCTION", 12));
    return list;
  }

  public static List<Map<String, Object>> defaultStructures() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("definitionKey", "default_staff");
    s.put("name", "Default staff structure");
    s.put(
        "lines",
        List.of(
            line("HRA", "PERCENT_OF_BASIC", 40),
            line("PF", "PERCENT_OF_BASIC", 12)));
    s.put("currency", "INR");
    list.add(s);
    return list;
  }

  public static List<Map<String, Object>> defaultPayCycles() {
    List<Map<String, Object>> list = new ArrayList<>();
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("definitionKey", "monthly");
    c.put("name", "Monthly payroll");
    c.put("frequency", "MONTHLY");
    c.put("cutoffDay", 25);
    list.add(c);
    return list;
  }

  private static Map<String, Object> component(String key, String label, String kind, double defaultValue) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("definitionKey", key);
    m.put("label", label);
    m.put("kind", kind);
    m.put("defaultValue", defaultValue);
    return m;
  }

  private static Map<String, Object> line(String componentKey, String calcType, double value) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("componentKey", componentKey);
    m.put("calcType", calcType);
    m.put("value", value);
    return m;
  }
}
