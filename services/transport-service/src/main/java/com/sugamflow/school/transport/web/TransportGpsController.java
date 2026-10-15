package com.sugamflow.school.transport.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.transport.service.TransportGpsService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transport/gps")
public class TransportGpsController {

  private final TransportGpsService service;

  public TransportGpsController(TransportGpsService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> latest() {
    return ApiResponse.ok(service.latest());
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> ping(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.ping(body == null ? Map.of() : body));
  }
}
