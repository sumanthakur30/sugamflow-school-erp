package com.sugamflow.school.attendance.web;

import com.sugamflow.school.attendance.biometric.BiometricDeviceRejected;
import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEntity;
import com.sugamflow.school.attendance.service.BiometricAttendanceService;
import com.sugamflow.school.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance/biometric")
public class BiometricAdminController {

  private final BiometricAttendanceService biometric;

  public BiometricAdminController(BiometricAttendanceService biometric) {
    this.biometric = biometric;
  }

  @PostMapping("/push")
  public ResponseEntity<ApiResponse<Map<String, Object>>> push(
      @RequestHeader(value = "X-Device-Serial", required = false) String serial,
      @RequestHeader(value = "X-Device-Key", required = false) String key,
      @RequestBody Map<String, Object> body) {
    try {
      String sn = serial != null && !serial.isBlank() ? serial : String.valueOf(body.getOrDefault("serialNumber", ""));
      AttendanceDeviceEntity device = biometric.authenticate(sn, key);
      return ResponseEntity.ok(ApiResponse.ok(biometric.ingestNormalized(device, body)));
    } catch (BiometricDeviceRejected ex) {
      return ResponseEntity.status(406)
          .body(new ApiResponse<>(false, Map.of("code", ex.getCode()), ex.getMessage()));
    }
  }

  @GetMapping("/devices")
  public ApiResponse<List<Map<String, Object>>> devices(
      @RequestParam(value = "branchId", required = false) String branchId,
      @RequestParam(value = "deviceType", required = false) String deviceType,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "location", required = false) String location) {
    return ApiResponse.ok(biometric.listDevices(branchId, deviceType, status, location));
  }

  @PostMapping("/devices")
  public ApiResponse<Map<String, Object>> create(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return ApiResponse.ok(biometric.registerDevice(body, ZkTecoIclockController.clientIp(request)));
  }

  @PutMapping("/devices/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return ApiResponse.ok(biometric.updateDevice(id, body, ZkTecoIclockController.clientIp(request)));
  }

  @DeleteMapping("/devices/{id}")
  public ApiResponse<Map<String, Object>> deactivate(
      @PathVariable("id") String id, HttpServletRequest request) {
    return ApiResponse.ok(biometric.deactivate(id, ZkTecoIclockController.clientIp(request)));
  }

  @PostMapping("/devices/{id}/key")
  public ApiResponse<Map<String, Object>> regenerate(
      @PathVariable("id") String id, HttpServletRequest request) {
    return ApiResponse.ok(biometric.regenerateKey(id, ZkTecoIclockController.clientIp(request)));
  }

  @DeleteMapping("/devices/{id}/key")
  public ApiResponse<Map<String, Object>> revoke(
      @PathVariable("id") String id, HttpServletRequest request) {
    return ApiResponse.ok(biometric.revokeKey(id, ZkTecoIclockController.clientIp(request)));
  }

  @GetMapping("/health")
  public ApiResponse<Map<String, Object>> health() {
    return ApiResponse.ok(biometric.health());
  }

  @GetMapping("/live")
  public ApiResponse<List<Map<String, Object>>> live(
      @RequestParam(value = "limit", defaultValue = "30") int limit) {
    return ApiResponse.ok(biometric.live(limit));
  }

  @GetMapping("/enrollments")
  public ApiResponse<List<Map<String, Object>>> enrollments(
      @RequestParam(value = "q", required = false) String query) {
    return ApiResponse.ok(biometric.listEnrollments(query));
  }

  @PostMapping("/enrollments")
  public ApiResponse<Map<String, Object>> saveEnrollment(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(biometric.saveEnrollment(body));
  }

  @PostMapping("/enrollments/{id}/disable")
  public ApiResponse<Map<String, Object>> disableEnrollment(@PathVariable("id") UUID id) {
    return ApiResponse.ok(biometric.disableEnrollment(id));
  }

  @GetMapping("/events")
  public ApiResponse<List<Map<String, Object>>> events(
      @RequestParam(value = "status", required = false) String status) {
    return ApiResponse.ok(biometric.listEvents(status));
  }

  @PostMapping("/events/{id}/retry")
  public ApiResponse<Map<String, Object>> retry(@PathVariable("id") UUID id) {
    return ApiResponse.ok(biometric.retry(id));
  }

  @PostMapping("/events/{id}/ignore")
  public ApiResponse<Map<String, Object>> ignore(@PathVariable("id") UUID id) {
    return ApiResponse.ok(biometric.ignoreEvent(id));
  }

  @GetMapping("/rules")
  public ApiResponse<Map<String, Object>> rules() {
    return ApiResponse.ok(biometric.getRule());
  }

  @PutMapping("/rules")
  public ApiResponse<Map<String, Object>> saveRules(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(biometric.saveRule(body));
  }

  @PostMapping("/devices/{id}/commands")
  public ApiResponse<Map<String, Object>> command(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return ApiResponse.ok(biometric.queueCommand(id, body, ZkTecoIclockController.clientIp(request)));
  }

  @PostMapping("/days/{id}/corrections")
  public ApiResponse<Map<String, Object>> correct(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return ApiResponse.ok(biometric.correct(id, body, ZkTecoIclockController.clientIp(request)));
  }

  @GetMapping("/summary")
  public ApiResponse<Map<String, Object>> summary(
      @RequestParam(value = "date", required = false) String date) {
    LocalDate day = date == null || date.isBlank() ? LocalDate.now() : LocalDate.parse(date);
    return ApiResponse.ok(biometric.summary(day));
  }

  @GetMapping(value = "/reports/daily", produces = "text/csv")
  public ResponseEntity<String> daily(@RequestParam("date") String date) {
    return csv("biometric-daily-" + date + ".csv", biometric.dailyCsv(LocalDate.parse(date)));
  }

  @GetMapping(value = "/reports/people", produces = "text/csv")
  public ResponseEntity<String> people(
      @RequestParam("from") String from,
      @RequestParam("to") String to,
      @RequestParam(value = "personType", defaultValue = "STUDENT") String personType) {
    return csv(
        "biometric-" + personType.toLowerCase() + ".csv",
        biometric.rangeCsv(LocalDate.parse(from), LocalDate.parse(to), personType));
  }

  private static ResponseEntity<String> csv(String filename, String body) {
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
        .contentType(MediaType.parseMediaType("text/csv"))
        .body(body);
  }
}
