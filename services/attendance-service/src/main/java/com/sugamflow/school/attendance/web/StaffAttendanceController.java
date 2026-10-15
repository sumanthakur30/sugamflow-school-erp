package com.sugamflow.school.attendance.web;

import com.sugamflow.school.attendance.service.StaffAttendanceService;
import com.sugamflow.school.common.api.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance/staff/months")
public class StaffAttendanceController {

  private final StaffAttendanceService service;

  public StaffAttendanceController(StaffAttendanceService service) {
    this.service = service;
  }

  @GetMapping("/{yearMonth}")
  public ApiResponse<Map<String, Object>> sheet(@PathVariable("yearMonth") String yearMonth) {
    return ApiResponse.ok(service.sheet(yearMonth));
  }

  @PutMapping("/{yearMonth}")
  public ApiResponse<Map<String, Object>> saveDay(
      @PathVariable("yearMonth") String yearMonth, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.saveDay(yearMonth, body));
  }

  @PostMapping("/{yearMonth}/submit")
  public ApiResponse<Map<String, Object>> submit(@PathVariable("yearMonth") String yearMonth) {
    return ApiResponse.ok(service.submit(yearMonth));
  }
}
