package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.settings.integration.AuditClient;
import com.sugamflow.school.settings.model.MenuNode;
import com.sugamflow.school.settings.service.SettingsConfigService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/menus")
public class MenuController {

  private final SettingsConfigService store;
  private final AuditClient auditClient;

  public MenuController(SettingsConfigService store, AuditClient auditClient) {
    this.store = store;
    this.auditClient = auditClient;
  }

  @GetMapping
  public ApiResponse<List<MenuNode>> get() {
    return ApiResponse.ok(store.getMenus(TenantContext.require().organizationId()));
  }

  /** Role + feature-flag filtered menu for the current caller. */
  @GetMapping("/effective")
  public ApiResponse<List<MenuNode>> effective() {
    return ApiResponse.ok(store.getEffectiveMenus(TenantContext.require()));
  }

  @PutMapping
  public ApiResponse<List<MenuNode>> save(@RequestBody List<MenuNode> menus) {
    String org = TenantContext.require().organizationId();
    List<MenuNode> before = store.getMenus(org);
    List<MenuNode> saved = store.saveMenus(org, menus);
    auditClient.recordChange("MENU_CONFIG", "menu", before, saved, "Menu configuration saved");
    return ApiResponse.ok(saved);
  }
}
