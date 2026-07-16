package com.sugamflow.school.attendance.web;

import com.sugamflow.school.attendance.service.DeviceAdapterService;
import com.sugamflow.school.common.api.ApiResponse;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/attendance/devices")
public class DeviceAdapterController {

  private final DeviceAdapterService service;

  public DeviceAdapterController(DeviceAdapterService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.listDevices());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.registerDevice(body));
  }

  @PutMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateDevice(id, body));
  }

  @GetMapping("/events")
  public ApiResponse<List<Map<String, Object>>> events(
      @RequestParam(value = "deviceId", required = false) String deviceId) {
    return ApiResponse.ok(service.listEvents(deviceId));
  }

  @PostMapping("/{id}/events")
  public ApiResponse<Map<String, Object>> ingest(
      @PathVariable("id") String id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.ingest(id, body));
  }

  @PostMapping("/{id}/simulate")
  public ApiResponse<Map<String, Object>> simulate(@PathVariable("id") String id) {
    return ApiResponse.ok(service.simulate(id));
  }
}
