package com.sugamflow.school.student.directory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Groups stored class labels such as {@code Grade 6-A} and {@code Grade 6 - A} into one class and
 * its sections. The raw label is kept so a filter can match the value on the student record.
 */
public final class ClassLabelGroups {

  private static final Pattern NUMBER = Pattern.compile("(\\d+)");

  private ClassLabelGroups() {}

  public record SectionGroup(String name, String label) {}

  public record ClassGroup(String name, List<SectionGroup> sections) {}

  public static List<ClassGroup> group(List<String> labels) {
    Map<String, MutableClass> byKey = new LinkedHashMap<>();
    if (labels != null) {
      for (String raw : labels) {
        if (raw == null || raw.isBlank()) {
          continue;
        }
        Split split = split(raw.trim());
        MutableClass group =
            byKey.computeIfAbsent(normalize(split.className()), key -> new MutableClass(split.className()));
        if (split.section() != null) {
          group.addSection(split.section(), split.label());
        }
      }
    }
    List<ClassGroup> out = new ArrayList<>();
    for (MutableClass group : byKey.values()) {
      out.add(group.freeze());
    }
    out.sort(Comparator.comparingInt((ClassGroup g) -> rank(g.name())).thenComparing(ClassGroup::name));
    return out;
  }

  static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
  }

  private static Split split(String label) {
    int spaced = label.lastIndexOf(" - ");
    if (spaced > 0 && isSection(label.substring(spaced + 3))) {
      return new Split(label.substring(0, spaced).trim(), label.substring(spaced + 3).trim(), label);
    }
    int dash = label.lastIndexOf('-');
    if (dash > 0 && isSection(label.substring(dash + 1))) {
      return new Split(label.substring(0, dash).trim(), label.substring(dash + 1).trim(), label);
    }
    return new Split(label, null, label);
  }

  /** A short letter/digit token is a section. A multi-digit token stays part of the class name. */
  private static boolean isSection(String token) {
    String value = token == null ? "" : token.trim();
    if (!value.matches("[A-Za-z0-9]{1,3}")) {
      return false;
    }
    boolean digits = value.chars().allMatch(Character::isDigit);
    return !digits || value.length() == 1;
  }

  private static int rank(String name) {
    String key = normalize(name);
    if (key.contains("prenursery") || key.contains("playgroup") || key.startsWith("nursery")) {
      return 10;
    }
    if (key.equals("lkg")) {
      return 20;
    }
    if (key.equals("ukg")) {
      return 30;
    }
    Matcher matcher = NUMBER.matcher(name == null ? "" : name);
    if (matcher.find()) {
      try {
        return 100 + Integer.parseInt(matcher.group(1));
      } catch (NumberFormatException ex) {
        return 800;
      }
    }
    return 800;
  }

  private record Split(String className, String section, String label) {}

  private static final class MutableClass {
    private final String name;
    private final Map<String, SectionGroup> sections = new LinkedHashMap<>();

    private MutableClass(String name) {
      this.name = name;
    }

    private void addSection(String section, String label) {
      sections.putIfAbsent(normalize(section), new SectionGroup(section, label));
    }

    private ClassGroup freeze() {
      List<SectionGroup> ordered = new ArrayList<>(sections.values());
      ordered.sort(Comparator.comparing(SectionGroup::name));
      return new ClassGroup(name, List.copyOf(ordered));
    }
  }
}
