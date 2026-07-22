package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.settings.portal.PortalBootstrapService;
import com.sugamflow.school.settings.portal.PortalCatalog;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/config/portals")
public class PortalController {

  private final PortalBootstrapService portals;

  public PortalController(PortalBootstrapService portals) {
    this.portals = portals;
  }

  @GetMapping
  public ApiResponse<List<String>> list() {
    return ApiResponse.ok(List.of(PortalCatalog.PARENT, PortalCatalog.TEACHER));
  }

  @GetMapping("/{portalKey}/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap(@PathVariable("portalKey") String portalKey) {
    try {
      return ApiResponse.ok(portals.bootstrap(portalKey));
    } catch (IllegalArgumentException ex) {
      String message = ex.getMessage() == null ? "Invalid portal request" : ex.getMessage();
      HttpStatus status =
          message.toLowerCase().contains("requires") ? HttpStatus.FORBIDDEN : HttpStatus.BAD_REQUEST;
      throw new ResponseStatusException(status, message);
    }
  }
}
