package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class StudentExceptionHandler {

  @ExceptionHandler(StudentException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handle(StudentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(false, Map.of("code", ex.getCode()), ex.getMessage()));
  }
}
