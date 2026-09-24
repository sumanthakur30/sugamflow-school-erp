package com.sugamflow.school.attendance.web;

import com.sugamflow.school.common.api.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AttendanceExceptionHandler {

  @ExceptionHandler(AttendanceException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handle(AttendanceException ex) {
    HttpStatus status =
        "NOT_FOUND".equals(ex.getCode())
            ? HttpStatus.NOT_FOUND
            : "LOCKED".equals(ex.getCode()) ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(false, Map.of("code", ex.getCode()), ex.getMessage()));
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleSecurity(SecurityException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiResponse<>(false, Map.of("code", "FORBIDDEN"), ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegal(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(false, null, ex.getMessage()));
  }
}
