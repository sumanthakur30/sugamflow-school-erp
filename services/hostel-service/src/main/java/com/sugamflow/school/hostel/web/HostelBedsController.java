package com.sugamflow.school.hostel.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.hostel.service.HostelBedsService;
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
@RequestMapping("/api/hostel/beds")
public class HostelBedsController {

  private final HostelBedsService service;

  public HostelBedsController(HostelBedsService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.listBeds());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> upsert(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.upsertBed(body));
  }

  @GetMapping("/occupancies")
  public ApiResponse<?> active(
      @RequestParam(value = "admissionNo", required = false) String admissionNo) {
    if (admissionNo != null && !admissionNo.isBlank()) {
      return ApiResponse.ok(service.activeOccupancyForAdmission(admissionNo));
    }
    return ApiResponse.ok(service.activeOccupancies());
  }

  @PostMapping("/allocate")
  public ApiResponse<Map<String, Object>> allocate(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.allocate(body));
  }

  @PostMapping("/occupancies/{id}/release")
  public ApiResponse<Map<String, Object>> release(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.release(id));
  }
}
