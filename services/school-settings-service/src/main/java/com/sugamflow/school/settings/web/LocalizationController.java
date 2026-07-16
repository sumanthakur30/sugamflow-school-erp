package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.model.LocalizationSettings;
import com.sugamflow.school.settings.service.SettingsConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/localization")
public class LocalizationController {

  private final SettingsConfigService store;
  private final AuditClient auditClient;

  public LocalizationController(SettingsConfigService store, AuditClient auditClient) {
    this.store = store;
    this.auditClient = auditClient;
  }

  @GetMapping
  public ApiResponse<LocalizationSettings> get() {
    TenantScope t = TenantContext.require();
    return ApiResponse.ok(store.getLocale(t.organizationId(), t.branchId()));
  }

  @PutMapping
  public ApiResponse<LocalizationSettings> save(@RequestBody LocalizationSettings body) {
    TenantScope t = TenantContext.require();
    LocalizationSettings before = store.getLocale(t.organizationId(), t.branchId());
    LocalizationSettings saved = store.saveLocale(t.organizationId(), t.branchId(), body);
    auditClient.recordChange(
        "LOCALIZATION", "localization", before, saved, "Localization settings saved");
    return ApiResponse.ok(saved);
  }
}
