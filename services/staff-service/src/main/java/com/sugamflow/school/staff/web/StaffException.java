package com.sugamflow.school.staff.web;

public class StaffException extends RuntimeException {
  private final String code;

  public StaffException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
