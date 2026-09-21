package com.sugamflow.school.attendance.web;

import com.sugamflow.school.attendance.service.AttendanceRosterService;
import com.sugamflow.school.common.api.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceRosterController {

  private final AttendanceRosterService service;

  public AttendanceRosterController(AttendanceRosterService service) {
    this.service = service;
  }

  @GetMapping("/roster")
  public ApiResponse<Map<String, Object>> roster(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(name = "periodId", required = false) UUID periodId) {
    return ApiResponse.ok(service.roster(sectionId, date, periodId));
  }

  @GetMapping("/roster/register")
  public ApiResponse<Map<String, Object>> register(
      @RequestParam("sectionId") UUID sectionId,
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(name = "periodId", required = false) UUID periodId,
      @RequestParam(name = "format", defaultValue = "PDF") String format) {
    return ApiResponse.ok(service.registerExport(sectionId, date, periodId, format));
  }

  @PutMapping("/sessions/bulk")
  public ApiResponse<Map<String, Object>> bulkMark(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.bulkMark(body));
  }

  @PostMapping("/sessions/{id}/submit")
  public ApiResponse<Map<String, Object>> submit(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.submitSession(id));
  }

  @PostMapping("/sessions/{id}/lock")
  public ApiResponse<Map<String, Object>> lock(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.lockSession(id));
  }

  @GetMapping("/sessions/{id}/alerts")
  public ApiResponse<List<Map<String, Object>>> alerts(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.sessionAlerts(id));
  }

  @GetMapping("/marks/mine")
  public ApiResponse<List<Map<String, Object>>> myMarks() {
    return ApiResponse.ok(service.myMarks());
  }
}
