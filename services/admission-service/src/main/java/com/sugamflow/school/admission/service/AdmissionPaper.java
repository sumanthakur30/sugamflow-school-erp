package com.sugamflow.school.admission.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Keeps an uploaded admission paper on the application the clerk saves. */
public final class AdmissionPaper {

  public static final String TYPE = "ADMISSION_PAPER";

  private AdmissionPaper() {}

  public static List<Map<String, Object>> attach(
      List<Map<String, Object>> existing, List<Map<String, Object>> incoming) {
    List<Map<String, Object>> kept = new ArrayList<>();
    if (existing != null) {
      for (Map<String, Object> doc : existing) {
        if (doc != null && !TYPE.equals(String.valueOf(doc.get("type")))) {
          kept.add(doc);
        }
      }
    }
    if (incoming == null) {
      return kept;
    }
    for (Map<String, Object> doc : incoming) {
      if (doc == null) {
        continue;
      }
      String fileName = text(doc.get("fileName"));
      if (fileName == null) {
        continue;
      }
      Map<String, Object> paper = new LinkedHashMap<>();
      paper.put("type", TYPE);
      paper.put("fileName", fileName);
      String mediaType = text(doc.get("mediaType"));
      if (mediaType != null) {
        paper.put("mediaType", mediaType);
      }
      kept.add(paper);
    }
    return kept;
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
  }
}
