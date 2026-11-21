package com.sugamflow.school.transport.service;

/** A route belongs to the campus that saved it. */
public final class CampusRoutes {

  private CampusRoutes() {}

  public static boolean visible(String campus, String storedCampus) {
    if (campus == null || campus.isBlank()) {
      return true;
    }
    return campus.equals(storedCampus);
  }
}
