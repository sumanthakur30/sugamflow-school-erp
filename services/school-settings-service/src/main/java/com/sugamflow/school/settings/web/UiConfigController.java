package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.model.UiScreenConfig;
import com.sugamflow.school.settings.service.SettingsConfigService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/config/ui")
public class UiConfigController {

  private final SettingsConfigService store;

  public UiConfigController(SettingsConfigService store) {
    this.store = store;
  }

  @GetMapping("/screens/{screenKey}")
  public ApiResponse<UiScreenConfig> getScreen(@PathVariable("screenKey") String screenKey) {
    return ApiResponse.ok(store.getScreen(screenKey, TenantContext.require().organizationId()));
  }

  @PutMapping("/screens/{screenKey}")
  public ApiResponse<UiScreenConfig> saveScreen(
      @PathVariable("screenKey") String screenKey, @RequestBody UiScreenConfig body) {
    body.setScreenKey(screenKey);
    return ApiResponse.ok(store.saveScreen(TenantContext.require().organizationId(), body));
  }

  @GetMapping("/roles/{roleCode}/dashboard")
  public ApiResponse<Map<String, Object>> roleDashboard(@PathVariable("roleCode") String roleCode) {
    TenantScope scope = TenantContext.require();
    String requested = PersonaRoles.normalize(roleCode);
    String current = PersonaRoles.normalize(scope.roleCode());
    if (!PersonaRoles.isElevated(current) && !requested.equals(current)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Dashboard access is limited to the signed-in role");
    }
    return ApiResponse.ok(store.getRoleDashboard(scope.organizationId(), requested));
  }

  @PutMapping("/roles/{roleCode}/dashboard")
  public ApiResponse<Map<String, Object>> saveRoleDashboard(
      @PathVariable("roleCode") String roleCode, @RequestBody Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    if (!PersonaRoles.isElevated(scope.roleCode())) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "Dashboard configuration requires an elevated role");
    }
    return ApiResponse.ok(
        store.saveRoleDashboard(
            scope.organizationId(), PersonaRoles.normalize(roleCode), body));
  }

  @GetMapping("/ai")
  public ApiResponse<Map<String, Object>> ai() {
    return ApiResponse.ok(store.getAi(TenantContext.require().organizationId()));
  }

  @PutMapping("/ai")
  public ApiResponse<Map<String, Object>> saveAi(@RequestBody Map<String, Object> body) {
    TenantScope t = TenantContext.require();
    return ApiResponse.ok(store.saveAi(t.organizationId(), body));
  }
}
