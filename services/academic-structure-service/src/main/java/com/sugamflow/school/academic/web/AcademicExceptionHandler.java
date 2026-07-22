package com.sugamflow.school.academic.web;

import com.sugamflow.school.common.api.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AcademicExceptionHandler {

  @ExceptionHandler(AcademicException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handle(AcademicException ex) {
    HttpStatus status =
        "NOT_FOUND".equals(ex.getCode()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(false, Map.of("code", ex.getCode()), ex.getMessage()));
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handleSecurity(SecurityException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(new ApiResponse<>(false, Map.of("code", "FORBIDDEN"), ex.getMessage()));
  }
}
