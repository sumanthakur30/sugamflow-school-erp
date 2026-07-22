package com.sugamflow.school.library.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.library.service.LibraryCirculationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library/circulation")
public class LibraryCirculationController {

  private final LibraryCirculationService service;

  public LibraryCirculationController(LibraryCirculationService service) {
    this.service = service;
  }

  @GetMapping("/books")
  public ApiResponse<List<Map<String, Object>>> books() {
    return ApiResponse.ok(service.listBooks());
  }

  @PostMapping("/books")
  public ApiResponse<Map<String, Object>> upsertBook(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.upsertBook(body));
  }

  @GetMapping("/issues")
  public ApiResponse<List<Map<String, Object>>> openIssues(
      @RequestParam(value = "admissionNo", required = false) String admissionNo) {
    if (admissionNo != null && !admissionNo.isBlank()) {
      return ApiResponse.ok(service.openIssuesForAdmission(admissionNo));
    }
    return ApiResponse.ok(service.openIssues());
  }

  @PostMapping("/issue")
  public ApiResponse<Map<String, Object>> issue(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.issue(body));
  }

  @PostMapping("/issues/{id}/return")
  public ApiResponse<Map<String, Object>> returnBook(
      @PathVariable("id") UUID id, @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(service.returnBook(id, body == null ? Map.of() : body));
  }
}
