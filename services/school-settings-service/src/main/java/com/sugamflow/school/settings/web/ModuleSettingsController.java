package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.model.ModuleSettings;
import com.sugamflow.school.settings.service.SettingsConfigService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/modules")
public class ModuleSettingsController {

  private final SettingsConfigService store;
  private final AuditClient auditClient;

  public ModuleSettingsController(SettingsConfigService store, AuditClient auditClient) {
    this.store = store;
    this.auditClient = auditClient;
  }

  @GetMapping
  public ApiResponse<List<String>> listModules() {
    return ApiResponse.ok(SettingsConfigService.MODULE_KEYS);
  }

  @GetMapping("/{moduleKey}")
  public ApiResponse<ModuleSettings> get(@PathVariable("moduleKey") String moduleKey) {
    TenantScope t = TenantContext.require();
    return ApiResponse.ok(store.getModule(moduleKey, t.organizationId(), t.branchId()));
  }

  @PutMapping("/{moduleKey}")
  public ApiResponse<ModuleSettings> save(
      @PathVariable("moduleKey") String moduleKey, @RequestBody ModuleSettings body) {
    TenantScope t = TenantContext.require();
    ModuleSettings before = store.getModule(moduleKey, t.organizationId(), t.branchId());
    body.setModuleKey(moduleKey);
    body.setOrganizationId(t.organizationId());
    body.setBranchId(t.branchId());
    body.setAcademicSessionId(t.academicSessionId());
    ModuleSettings saved = store.saveModule(body);
    auditClient.recordChange(
        "MODULE_SETTINGS",
        moduleKey,
        before,
        saved,
        "Module settings saved: " + moduleKey);
    return ApiResponse.ok(saved);
  }

  @GetMapping("/catalog/schema")
  public ApiResponse<Map<String, Object>> schemaCatalog() {
    Map<String, Object> catalog = new LinkedHashMap<>();
    for (String key : SettingsConfigService.MODULE_KEYS) {
      catalog.put(
          key,
          Map.of(
              "title",
              key.substring(0, 1).toUpperCase() + key.substring(1) + " Settings",
              "configurable",
              true));
    }
    return ApiResponse.ok(catalog);
  }
}
