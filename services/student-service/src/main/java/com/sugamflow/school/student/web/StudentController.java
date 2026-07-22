package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.student.directory.StudentDirectoryService;
import com.sugamflow.school.student.service.Student360Service;
import com.sugamflow.school.student.service.StudentRecordService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
public class StudentController {

  private final StudentRecordService service;
  private final Student360Service student360;
  private final StudentDirectoryService directory;

  public StudentController(
      StudentRecordService service,
      Student360Service student360,
      StudentDirectoryService directory) {
    this.service = service;
    this.student360 = student360;
    this.directory = directory;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping("/access-scope")
  public ApiResponse<Map<String, Object>> accessScope() {
    return ApiResponse.ok(service.accessScope().toMap());
  }

  @GetMapping("/guardians/delivery-targets")
  public ApiResponse<List<Map<String, Object>>> guardianDeliveryTargets() {
    return ApiResponse.ok(service.guardianDeliveryTargets());
  }

  @GetMapping("/students")
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(service.list(page, size));
  }

  /**
   * Reusable enterprise student lookup — same contract as {@code /api/student/directory/students}.
   * Prefer this path from cross-module selectors (fee, attendance, exams, etc.).
   */
  @GetMapping("/students/search")
  public ApiResponse<PageResult<Map<String, Object>>> search(
      @RequestParam Map<String, String> params) {
    return ApiResponse.ok(directory.search(params != null ? params : Map.of()));
  }

  @GetMapping("/students/by-admission/{admissionNo}")
  public ApiResponse<Map<String, Object>> getByAdmission(
      @PathVariable("admissionNo") String admissionNo) {
    return ApiResponse.ok(service.getByAdmissionNo(admissionNo));
  }

  @GetMapping("/students/by-admission/{admissionNo}/identity")
  public ApiResponse<Map<String, Object>> identityByAdmission(
      @PathVariable("admissionNo") String admissionNo) {
    return ApiResponse.ok(service.identitySummaryByAdmissionNo(admissionNo));
  }

  @GetMapping("/students/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @GetMapping("/students/{id}/360")
  public ApiResponse<Map<String, Object>> student360(@PathVariable("id") UUID id) {
    return ApiResponse.ok(student360.profile(id));
  }

  @GetMapping("/students/{id}/audit")
  public ApiResponse<PageResult<Map<String, Object>>> audit(
      @PathVariable("id") UUID id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(service.fieldAudit(id, page, size));
  }

  @GetMapping("/students/{id}/timeline")
  public ApiResponse<Map<String, Object>> timeline(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.timeline(id));
  }

  @PostMapping("/enroll-from-admission")
  public ApiResponse<Map<String, Object>> enrollFromAdmission(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.enrollFromAdmission(body));
  }

  @PutMapping("/students/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") UUID id,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return ApiResponse.ok(service.update(id, withClientMeta(body, request)));
  }

  @PutMapping("/students/{id}/guardians")
  public ApiResponse<Map<String, Object>> replaceGuardians(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.replaceGuardians(id, body));
  }

  @PostMapping("/students/{id}/soft-delete")
  public ApiResponse<Map<String, Object>> softDelete(
      @PathVariable("id") UUID id,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return ApiResponse.ok(service.softDelete(id, withClientMeta(body, request)));
  }

  @PostMapping("/students/{id}/restore")
  public ApiResponse<Map<String, Object>> restore(
      @PathVariable("id") UUID id,
      @RequestBody(required = false) Map<String, Object> body,
      HttpServletRequest request) {
    Map<String, Object> payload = body != null ? body : new LinkedHashMap<>();
    return ApiResponse.ok(service.restore(id, withClientMeta(payload, request)));
  }

  @PostMapping("/students/{id}/status")
  public ApiResponse<Map<String, Object>> changeStatus(
      @PathVariable("id") UUID id,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return ApiResponse.ok(service.changeStatus(id, withClientMeta(body, request)));
  }

  @PostMapping("/students/bulk-soft-delete")
  public ApiResponse<Map<String, Object>> bulkSoftDelete(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return ApiResponse.ok(service.bulkSoftDelete(withClientMeta(body, request)));
  }

  @PostMapping("/students/bulk-restore")
  public ApiResponse<Map<String, Object>> bulkRestore(
      @RequestBody Map<String, Object> body, HttpServletRequest request) {
    return ApiResponse.ok(service.bulkRestore(withClientMeta(body, request)));
  }

  @DeleteMapping("/students/{id}/permanent")
  public ApiResponse<Map<String, Object>> hardDelete(
      @PathVariable("id") UUID id,
      @RequestBody Map<String, Object> body,
      HttpServletRequest request) {
    return ApiResponse.ok(service.hardDelete(id, withClientMeta(body, request)));
  }

  private static Map<String, Object> withClientMeta(
      Map<String, Object> body, HttpServletRequest request) {
    Map<String, Object> out = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
    if (!out.containsKey("ipAddress") || out.get("ipAddress") == null) {
      out.put("ipAddress", clientIp(request));
    }
    if (!out.containsKey("userAgent") || out.get("userAgent") == null) {
      out.put("userAgent", request.getHeader("User-Agent"));
    }
    return out;
  }

  private static String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
