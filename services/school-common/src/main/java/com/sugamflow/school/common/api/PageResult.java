package com.sugamflow.school.common.api;

import java.util.List;

/** Stable page envelope for list APIs (avoids unbounded org-wide payloads). */
public record PageResult<T>(
    List<T> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {

  public static <T> PageResult<T> of(List<T> items, int page, int size, long totalElements) {
    int safeSize = Math.max(1, size);
    int totalPages = (int) Math.ceil((double) totalElements / (double) safeSize);
    boolean hasNext = page + 1 < totalPages;
    return new PageResult<>(items, page, safeSize, totalElements, totalPages, hasNext);
  }

  public static <T> PageResult<T> empty(int page, int size) {
    return of(List.of(), page, size, 0);
  }
}
