package com.sugamflow.school.student.web;

import java.util.Map;

public class StudentException extends RuntimeException {
  private final String code;
  private final Map<String, Object> details;

  public StudentException(String code, String message) {
    this(code, message, null);
  }

  public StudentException(String code, String message, Map<String, Object> details) {
    super(message);
    this.code = code;
    this.details = details;
  }

  public String getCode() {
    return code;
  }

  public Map<String, Object> getDetails() {
    return details;
  }
}
