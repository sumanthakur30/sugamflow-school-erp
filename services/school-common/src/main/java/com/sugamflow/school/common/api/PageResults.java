package com.sugamflow.school.common.api;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** In-memory page filter for relationship-scoped list results. */
public final class PageResults {

  private PageResults() {}

  public static <T> PageResult<T> filterThenPage(
      List<T> source, Predicate<T> keep, int page, int size) {
    List<T> filtered = source.stream().filter(keep).collect(Collectors.toList());
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, size);
    int from = Math.min(safePage * safeSize, filtered.size());
    int to = Math.min(from + safeSize, filtered.size());
    return PageResult.of(filtered.subList(from, to), safePage, safeSize, filtered.size());
  }
}
