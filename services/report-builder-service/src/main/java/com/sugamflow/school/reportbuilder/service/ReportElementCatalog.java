package com.sugamflow.school.reportbuilder.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Palette + normalization for drag-drop report designer elements (metadata-driven). */
public final class ReportElementCatalog {

  /** Designer sample portrait so {{student.photoDirectUrl}} previews before a student photo exists. */
  public static final String SAMPLE_PHOTO_DATA_URL =
      "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAABACAYAAABcIPRGAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAFQSURBVGhD7dQxDsIwDEZhDssRuAVHZGXmCqAMldBrVduJG6eNh29Bavy/hdvr/fme2Y0/nE0GRMuAaBkQLQPo/niK+E0LtwCO1OAbNVwCOMyCb1k1B3BQDb5p0RTAIS34tlZ1AAd44A2NOQN42BNvSTLAG29J5gvgQW+8JzEHFDzqibckGeCNtyRzBhQ87IE3NOYNKDigBd/WagooOKQG37RoDig4yIJvWbkEFBymwTdquAUsOHILv2nhHtBbBkTLgGgZEM0lgP/zFnzLqjqAQzzwhoY5gEePwJt71AE80gM3bFEF8OGeuIXEAD4YgZvUAXwoEreJAXxgBNx43QB+OBJuzYDeuHUVwA9Gw72rgNEjuDUDeuPWzYBRI7jx2gGjRXCbKmCUCG4yBURHcAupAgo+3AM3bFEHLHjkCLy5xxyw4FEPvKFRHfCPQyz4ltUP8fzWUNoQNcsAAAAASUVORK5CYII=";

  private ReportElementCatalog() {}

  public static final List<Map<String, Object>> ELEMENT_TYPES =
      List.of(
          type("heading", "Heading", 420, 28, true),
          type("text", "Text", 420, 40, true),
          type("field", "Data field", 280, 24, false),
          type("line", "Horizontal line", 500, 2, false),
          type("box", "Box / frame", 320, 80, false),
          type("image", "Image placeholder", 120, 120, false),
          type("qr", "QR code", 120, 120, true));

  private static Map<String, Object> type(
      String key, String label, int defaultWidth, int defaultHeight, boolean hasText) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", key);
    m.put("label", label);
    m.put("defaultWidth", defaultWidth);
    m.put("defaultHeight", defaultHeight);
    m.put("hasText", hasText);
    return m;
  }

  public static Map<String, Object> newElement(String type, int x, int y) {
    Map<String, Object> def =
        ELEMENT_TYPES.stream()
            .filter(t -> type.equals(t.get("type")))
            .findFirst()
            .orElse(ELEMENT_TYPES.get(1));
    Map<String, Object> el = new LinkedHashMap<>();
    el.put("id", UUID.randomUUID().toString());
    el.put("type", def.get("type"));
    el.put("x", x);
    el.put("y", y);
    el.put("width", def.get("defaultWidth"));
    el.put("height", def.get("defaultHeight"));
    el.put("fontSize", "heading".equals(type) ? 18 : 11);
    el.put("align", "left");
    el.put("bold", "heading".equals(type));
    switch (String.valueOf(def.get("type"))) {
      case "heading" -> el.put("text", "Heading");
      case "text" -> el.put("text", "Sample text {{student.name}}");
      case "field" -> {
        el.put("bind", "student.name");
        el.put("text", "{{student.name}}");
      }
      case "line" -> el.put("text", "");
      case "box" -> el.put("text", "");
      case "image" -> {
        el.put("text", "{{student.photoDirectUrl}}");
        el.put("bind", "student.photoDirectUrl");
        el.put("objectFit", "cover");
      }
      case "qr" -> {
        el.put("bind", "context.verifyUrl");
        el.put("text", "{{context.verifyUrl}}");
      }
      default -> el.put("text", "");
    }
    return el;
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> normalizeElements(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    java.util.ArrayList<Map<String, Object>> out = new java.util.ArrayList<>();
    int i = 0;
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> m)) {
        continue;
      }
      Map<String, Object> el = new LinkedHashMap<>((Map<String, Object>) m);
      if (el.get("id") == null || String.valueOf(el.get("id")).isBlank()) {
        el.put("id", "el-" + (++i) + "-" + UUID.randomUUID().toString().substring(0, 8));
      }
      String type = String.valueOf(el.getOrDefault("type", "text"));
      el.put("type", type);
      el.put("x", intOr(el.get("x"), 40));
      el.put("y", intOr(el.get("y"), 40 + i * 28));
      el.put("width", intOr(el.get("width"), defaultWidth(type)));
      el.put("height", intOr(el.get("height"), defaultHeight(type)));
      el.put("fontSize", intOr(el.get("fontSize"), "heading".equals(type) ? 18 : 11));
      el.put("align", String.valueOf(el.getOrDefault("align", "left")));
      if (!el.containsKey("bold")) {
        el.put("bold", "heading".equals(type));
      }
      if (!el.containsKey("text") && el.containsKey("bind")) {
        el.put("text", "{{" + el.get("bind") + "}}");
      }
      if ("image".equals(type)) {
        String text = String.valueOf(el.getOrDefault("text", ""));
        String bind = String.valueOf(el.getOrDefault("bind", ""));
        if (text.contains("student.photoBase64") || "student.photoBase64".equals(bind)) {
          el.put("text", "{{student.photoDirectUrl}}");
          el.put("bind", "student.photoDirectUrl");
        } else if (text.contains("student.photoDirectUrl")
            && (bind.isBlank() || "null".equals(bind))) {
          el.put("bind", "student.photoDirectUrl");
        }
        if (!el.containsKey("objectFit") || String.valueOf(el.get("objectFit")).isBlank()) {
          el.put("objectFit", "cover");
        }
      }
      out.add(el);
    }
    return out;
  }

  private static int defaultWidth(String type) {
    return switch (type) {
      case "line" -> 500;
      case "box" -> 320;
      case "image" -> 120;
      case "qr" -> 120;
      case "field" -> 280;
      default -> 420;
    };
  }

  private static int defaultHeight(String type) {
    return switch (type) {
      case "heading" -> 28;
      case "line" -> 2;
      case "box" -> 80;
      case "image" -> 120;
      case "qr" -> 120;
      default -> 24;
    };
  }

  private static int intOr(Object v, int d) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return v != null ? Integer.parseInt(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }

  public static Map<String, Object> samplePreviewData() {
    Map<String, Object> student = new LinkedHashMap<>();
    student.put("name", "Priya Nair");
    student.put("grade", "Grade 8");
    student.put("section", "A");
    student.put("admissionNo", "ADM-1001");
    student.put("classApplied", "Grade 8");
    student.put("classSection", "Grade 8 - A");
    student.put("rollLine", "Adm  ADM-1001");
    student.put("dob", "10/12/2007");
    student.put("dateOfBirth", "10/12/2007");
    student.put("bloodGroup", "O+");
    student.put("mobileNo", "9999900001");
    student.put("emergencyNo", "9871986210");
    student.put("emergencyContact", "9871986210");
    student.put("pen", "PEN1234567");
    student.put("transportMode", "Bus");
    student.put("penNumber", "PEN1234567");
    student.put("apaarId", "APAAR-998877");
    student.put("photoBase64", "");
    student.put("photoDirectUrl", SAMPLE_PHOTO_DATA_URL);
    student.put("photoContentUrl", "");
    Map<String, Object> application = new LinkedHashMap<>();
    application.put("fullName", "Priya Nair");
    application.put("classApplied", "Grade 8");
    application.put("email", "priya@example.com");
    application.put("mobile", "9999900001");
    Map<String, Object> payment = new LinkedHashMap<>();
    payment.put("studentName", "Priya Nair");
    payment.put("admissionNo", "ADM-1001");
    payment.put("feeHead", "Tuition");
    payment.put("amount", "12500");
    payment.put("paymentMode", "UPI");
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", "demo-school");
    context.put("organizationName", "Demo School");
    context.put("branchId", "main");
    context.put("branchName", "Main");
    context.put("academicSessionId", "2025-26");
    context.put("sessionLabel", "2025-2026");
    context.put("issuedAt", "16 Jul 2026");
    context.put("expiresAt", "31 Mar 2027");
    context.put("offerLetterUrl", "https://example.local/offer.pdf");
    context.put("feeReceiptUrl", "https://example.local/receipt.pdf");
    context.put("verifyUrl", "https://example.local/verify/document/demo-token");
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("student", student);
    data.put("application", application);
    data.put("payment", payment);
    data.put("document", Map.of("type", "ID_CARD", "referenceNo", "ID-1001"));
    data.put("context", context);
    return data;
  }
}
