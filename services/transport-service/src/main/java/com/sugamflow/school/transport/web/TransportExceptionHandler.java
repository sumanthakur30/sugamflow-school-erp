package com.sugamflow.school.transport.web;

import com.sugamflow.school.common.api.ApiResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class TransportExceptionHandler {

  @ExceptionHandler(TransportException.class)
  public ResponseEntity<ApiResponse<Map<String, String>>> handle(TransportException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(false, Map.of("code", ex.getCode()), ex.getMessage()));
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<ApiResponse<Void>> handleIllegal(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ApiResponse<>(false, null, ex.getMessage()));
  }
}
