package com.sugamflow.school.notification.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.notification.service.NotificationTemplateService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/school/notification-config")
public class NotificationController {
  public static final List<String> EVENTS =
      List.of(
          "ADMISSION",
          "ATTENDANCE",
          "FEES",
          "EXAM",
          "SALARY",
          "LEAVE",
          "TRANSPORT",
          "BIRTHDAY",
          "HOLIDAY",
          "EMERGENCY",
          "LIBRARY",
          "HOSTEL",
          "PAYROLL");
  public static final List<String> CHANNELS =
      List.of("SMS", "WHATSAPP", "EMAIL", "PUSH", "IN_APP", "VOICE", "TELEGRAM");

  private final NotificationTemplateService service;

  public NotificationController(NotificationTemplateService service) {
    this.service = service;
  }

  @GetMapping("/events")
  public ApiResponse<List<String>> events() {
    return ApiResponse.ok(EVENTS);
  }

  @GetMapping("/channels")
  public ApiResponse<List<String>> channels() {
    return ApiResponse.ok(CHANNELS);
  }

  @GetMapping("/templates")
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.list(TenantContext.require().organizationId()));
  }

  @PostMapping("/templates")
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    String id = String.valueOf(body.getOrDefault("id", UUID.randomUUID()));
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), id, body));
  }

  @PutMapping("/templates/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), id, body));
  }

  @GetMapping("/templates/resolve")
  public ApiResponse<Map<String, Object>> resolveGet(
      @RequestParam("event") String event, @RequestParam("intent") String intent) {
    return ApiResponse.ok(
        service.resolve(TenantContext.require().organizationId(), event, intent, Map.of()));
  }

  @PostMapping("/templates/resolve")
  public ApiResponse<Map<String, Object>> resolvePost(@RequestBody Map<String, Object> body) {
    String event = String.valueOf(body.getOrDefault("event", "ADMISSION"));
    String intent = String.valueOf(body.getOrDefault("intent", "ADMISSION_APPROVED"));
    @SuppressWarnings("unchecked")
    Map<String, Object> variables =
        body.get("variables") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : new LinkedHashMap<>();
    return ApiResponse.ok(
        service.resolve(TenantContext.require().organizationId(), event, intent, variables));
  }

  @PostMapping("/preview")
  public ApiResponse<Map<String, Object>> preview(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.preview(body, TenantContext.require().organizationId()));
  }
}
