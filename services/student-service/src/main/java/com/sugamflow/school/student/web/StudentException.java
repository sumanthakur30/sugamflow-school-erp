package com.sugamflow.school.student.web;

public class StudentException extends RuntimeException {
  private final String code;

  public StudentException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
