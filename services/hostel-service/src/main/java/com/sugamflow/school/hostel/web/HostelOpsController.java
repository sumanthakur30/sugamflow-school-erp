package com.sugamflow.school.hostel.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.hostel.ops.HostelOpsCatalog;
import com.sugamflow.school.hostel.service.HostelOpsService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/hostel/ops")
public class HostelOpsController {

  private final HostelOpsService service;

  public HostelOpsController(HostelOpsService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(wrap(service::bootstrap));
  }

  @GetMapping("/room-types")
  public ApiResponse<List<Map<String, Object>>> roomTypes() {
    return ApiResponse.ok(wrap(() -> service.listDefinitions(HostelOpsCatalog.TYPE_ROOM_TYPE)));
  }

  @PutMapping("/room-types/{key}")
  public ApiResponse<Map<String, Object>> saveRoomType(
      @PathVariable("key") String key, @RequestBody Map<String, Object> body) {
    body.put("definitionKey", key);
    return ApiResponse.ok(wrap(() -> service.saveDefinition(HostelOpsCatalog.TYPE_ROOM_TYPE, body)));
  }

  @PostMapping("/allocations/preview")
  public ApiResponse<Map<String, Object>> previewAllocation(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(wrap(() -> service.previewAllocation(body)));
  }

  private <T> T wrap(java.util.concurrent.Callable<T> call) {
    try {
      return call.call();
    } catch (HostelException ex) {
      HttpStatus status =
          "NOT_FOUND".equals(ex.getCode())
              ? HttpStatus.NOT_FOUND
              : "FEATURE_OFF".equals(ex.getCode())
                  ? HttpStatus.FORBIDDEN
                  : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, ex.getMessage());
    } catch (ResponseStatusException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage());
    }
  }
}
