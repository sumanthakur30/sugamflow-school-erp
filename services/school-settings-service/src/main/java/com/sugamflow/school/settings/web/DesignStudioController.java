package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.model.DesignTheme;
import com.sugamflow.school.settings.service.SettingsConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/design-studio")
public class DesignStudioController {

  private final SettingsConfigService store;
  private final AuditClient auditClient;

  public DesignStudioController(SettingsConfigService store, AuditClient auditClient) {
    this.store = store;
    this.auditClient = auditClient;
  }

  /**
   * White-label theme for login (no JWT). Uses {@code organizationId} query or
   * {@code X-Tenant-Id}. Returns the org's configured theme (latest save).
   */
  @GetMapping("/theme/published")
  public ApiResponse<DesignTheme> publishedTheme(
      @RequestParam(value = "organizationId", required = false) String organizationId,
      @RequestParam(value = "branchId", required = false) String branchId) {
    String org =
        organizationId != null && !organizationId.isBlank()
            ? organizationId.trim()
            : TenantContext.get().map(TenantScope::organizationId).orElse(null);
    if (org == null || org.isBlank()) {
      throw new IllegalStateException("organizationId or X-Tenant-Id required");
    }
    String branch =
        branchId != null && !branchId.isBlank()
            ? branchId.trim()
            : TenantContext.get().map(TenantScope::branchId).orElse("main");
    if (branch == null || branch.isBlank()) {
      branch = "main";
    }
    return ApiResponse.ok(store.getOrCreateTheme(org, branch));
  }

  @GetMapping("/theme")
  public ApiResponse<DesignTheme> getTheme() {
    TenantScope t = TenantContext.require();
    return ApiResponse.ok(store.getOrCreateTheme(t.organizationId(), t.branchId()));
  }

  @PutMapping("/theme")
  public ApiResponse<DesignTheme> saveTheme(@RequestBody DesignTheme theme) {
    TenantScope t = TenantContext.require();
    DesignTheme before = store.getOrCreateTheme(t.organizationId(), t.branchId());
    theme.setOrganizationId(t.organizationId());
    if (theme.getBranchId() == null) {
      theme.setBranchId(t.branchId());
    }
    theme.setStatus("DRAFT");
    DesignTheme saved = store.saveTheme(theme);
    auditClient.recordChange(
        "DESIGN_THEME", "theme", before, saved, "Design Studio theme saved (draft)");
    return ApiResponse.ok(saved);
  }

  @PutMapping("/theme/publish")
  public ApiResponse<DesignTheme> publish() {
    TenantScope t = TenantContext.require();
    DesignTheme before = store.getOrCreateTheme(t.organizationId(), t.branchId());
    DesignTheme theme = store.getOrCreateTheme(t.organizationId(), t.branchId());
    theme.setStatus("PUBLISHED");
    int next = Integer.parseInt(theme.getVersion() == null ? "0" : theme.getVersion()) + 1;
    theme.setVersion(String.valueOf(next));
    DesignTheme saved = store.saveTheme(theme);
    auditClient.recordChange(
        "DESIGN_THEME", "theme", before, saved, "Design Studio theme published v" + saved.getVersion());
    return ApiResponse.ok(saved);
  }
}
