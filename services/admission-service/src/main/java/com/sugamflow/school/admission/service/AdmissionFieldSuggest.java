package com.sugamflow.school.admission.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Suggested admission fields from an uploaded paper. Nothing is saved here. */
public final class AdmissionFieldSuggest {

  private static final Pattern DATE =
      Pattern.compile("(\\d{4}-\\d{2}-\\d{2}|\\d{2}[/-]\\d{2}[/-]\\d{4})");
  private static final Pattern MOBILE = Pattern.compile("(?<!\\d)([6-9]\\d{9})(?!\\d)");

  private AdmissionFieldSuggest() {}

  public static Map<String, String> suggest(String fileName, String text) {
    String source = (text == null ? "" : text) + "\n" + (fileName == null ? "" : fileName);
    Map<String, String> fields = new LinkedHashMap<>();
    put(fields, "fullName", labeled(source, "name", "applicant", "student", "नाम"));
    put(fields, "dateOfBirth", date(source));
    put(fields, "mobile", mobile(source));
    put(fields, "classApplied", labeled(source, "class", "कक्षा"));
    put(fields, "guardianName", labeled(source, "father", "guardian", "parent", "पिता"));
    return fields;
  }

  private static void put(Map<String, String> fields, String key, String value) {
    if (value != null && !value.isBlank()) {
      fields.put(key, value.trim());
    }
  }

  private static String labeled(String source, String... labels) {
    String[] lines = source.split("\\R");
    for (String line : lines) {
      String lower = line.toLowerCase(Locale.ROOT);
      for (String label : labels) {
        int at = lower.indexOf(label.toLowerCase(Locale.ROOT));
        if (at < 0) {
          continue;
        }
        String rest = line.substring(at + label.length()).replaceFirst("^\\s*[:\\-]\\s*", "").trim();
        if (!rest.isBlank() && rest.length() <= 80) {
          return rest;
        }
      }
    }
    return null;
  }

  private static String date(String source) {
    Matcher matcher = DATE.matcher(source);
    if (!matcher.find()) {
      return null;
    }
    String found = matcher.group(1);
    if (found.charAt(2) == '/' || found.charAt(2) == '-') {
      String[] parts = found.split("[/-]");
      if (parts.length == 3 && parts[2].length() == 4) {
        return parts[2] + "-" + parts[1] + "-" + parts[0];
      }
    }
    return found;
  }

  private static String mobile(String source) {
    Matcher matcher = MOBILE.matcher(source);
    return matcher.find() ? matcher.group(1) : null;
  }
}
