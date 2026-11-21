package com.sugamflow.school.fee.finance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dated portions of one annual fee. A paid portion keeps the amount that was billed. */
public final class InstallmentSchedule {

  private InstallmentSchedule() {}

  public static BigDecimal amountFor(List<?> portions, String portionKey) {
    Map<String, Object> match = find(portions, portionKey);
    if (match == null) {
      return null;
    }
    Object amount = match.get("amount");
    if (amount == null) {
      return null;
    }
    return new BigDecimal(String.valueOf(amount));
  }

  /** True only when every dated portion has been paid. One paid portion leaves the year open. */
  public static boolean yearSettled(List<?> portions, Collection<?> paidKeys) {
    if (portions == null || portions.isEmpty()) {
      return false;
    }
    for (Object item : portions) {
      if (!(item instanceof Map<?, ?> map)) {
        return false;
      }
      Object key = map.get("key");
      if (key == null || !containsKey(paidKeys, String.valueOf(key))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Next year's schedule may change unpaid portions. A portion already paid keeps the amount stored
   * on that receipt.
   */
  public static List<Map<String, Object>> reviseUnpaid(
      List<Map<String, Object>> paidSnapshots, List<Map<String, Object>> nextPortions) {
    Map<String, Map<String, Object>> paid = new LinkedHashMap<>();
    if (paidSnapshots != null) {
      for (Map<String, Object> snapshot : paidSnapshots) {
        if (snapshot != null && snapshot.get("key") != null) {
          paid.put(norm(snapshot.get("key")), snapshot);
        }
      }
    }
    List<Map<String, Object>> out = new ArrayList<>();
    if (nextPortions == null) {
      return out;
    }
    for (Map<String, Object> next : nextPortions) {
      if (next == null || next.get("key") == null) {
        continue;
      }
      Map<String, Object> kept = paid.get(norm(next.get("key")));
      out.add(new LinkedHashMap<>(kept == null ? next : kept));
    }
    return out;
  }

  private static Map<String, Object> find(List<?> portions, String portionKey) {
    if (portions == null || portionKey == null || portionKey.isBlank()) {
      return null;
    }
    String wanted = norm(portionKey);
    for (Object item : portions) {
      if (item instanceof Map<?, ?> map && wanted.equals(norm(map.get("key")))) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) map;
        return cast;
      }
    }
    return null;
  }

  private static boolean containsKey(Collection<?> paidKeys, String key) {
    if (paidKeys == null) {
      return false;
    }
    String wanted = norm(key);
    for (Object paid : paidKeys) {
      if (wanted.equals(norm(paid))) {
        return true;
      }
    }
    return false;
  }

  private static String norm(Object value) {
    return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
  }
}
