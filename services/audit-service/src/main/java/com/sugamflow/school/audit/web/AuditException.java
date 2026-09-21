package com.sugamflow.school.audit.web;

public class AuditException extends RuntimeException {

  private final String code;

  public AuditException(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
