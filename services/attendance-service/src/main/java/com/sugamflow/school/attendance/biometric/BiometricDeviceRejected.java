package com.sugamflow.school.attendance.biometric;

/** Device protocol failure. Controllers return plain text; this must not become a JSON API error. */
public class BiometricDeviceRejected extends RuntimeException {

  private final String code;

  public BiometricDeviceRejected(String code, String message) {
    super(message);
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
