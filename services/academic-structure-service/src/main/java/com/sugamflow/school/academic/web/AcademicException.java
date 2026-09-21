package com.sugamflow.school.academic.web;

public class AcademicException extends RuntimeException {

  private final String code;

  public AcademicException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }

  public static AcademicException notFound(String what) {
    return new AcademicException("NOT_FOUND", what + " not found");
  }

  public static AcademicException badRequest(String message) {
    return new AcademicException("BAD_REQUEST", message);
  }
}
