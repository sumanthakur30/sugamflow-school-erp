package com.sugamflow.school.cms.service;

import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** CBSE mandatory public disclosure headings. Documents under these codes are managed in Website CMS. */
public final class DisclosureCategories {

  public record Item(String code, String label) {}

  public static final List<Item> ALL =
      List.of(
          new Item("AFFILIATION", "AFFILIATION / UPGRADATION LETTER"),
          new Item("TRUST_REGISTRATION", "SOCIETIES / TRUST / COMPANY REGISTRATION"),
          new Item("NOC", "NO OBJECTION CERTIFICATE (NOC)"),
          new Item("RECOGNITION", "RECOGNITION CERTIFICATE"),
          new Item("BUILDING_SAFETY", "BUILDING SAFETY CERTIFICATE"),
          new Item("FIRE_SAFETY", "FIRE SAFETY CERTIFICATE"),
          new Item("DEO", "DEO CERTIFICATE"),
          new Item("WATER_HEALTH", "VALIDITY OF WATER, HEALTH AND SANITATION CERTIFICATES"),
          new Item("FEE_STRUCTURE", "FEE STRUCTURE"),
          new Item("ACADEMIC_CALENDAR", "ANNUAL ACADEMIC CALENDAR"),
          new Item("SMC", "LIST OF SCHOOL MANAGEMENT COMMITTEE (SMC)"),
          new Item("PTA", "LIST OF PARENTS TEACHERS ASSOCIATION (PTA)"),
          new Item("BOARD_RESULT", "LAST THREE-YEAR RESULT OF BOARD EXAMINATION"));

  private DisclosureCategories() {}

  public static boolean isDisclosure(String category) {
    if (category == null || category.isBlank()) {
      return false;
    }
    String code = category.trim().toUpperCase(Locale.ROOT);
    return ALL.stream().anyMatch(item -> item.code().equals(code));
  }

  public static String require(String category) {
    if (!isDisclosure(category)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a disclosure category");
    }
    return category.trim().toUpperCase(Locale.ROOT);
  }
}
