package com.sugamflow.school.common.api;

/** Normalized page/size query with hard caps. */
public record PageQuery(int page, int size) {

  public static final int DEFAULT_SIZE = 50;
  public static final int MAX_SIZE = 200;

  public static PageQuery of(Integer page, Integer size) {
    int p = page == null || page < 0 ? 0 : page;
    int s = size == null || size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    return new PageQuery(p, s);
  }
}
