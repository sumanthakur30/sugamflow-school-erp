package com.sugamflow.school.settings.portal;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.integration.SubscriptionClient;
import com.sugamflow.school.settings.model.ModuleSettings;
import com.sugamflow.school.settings.service.SettingsConfigService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortalBootstrapService {

  private final SettingsConfigService settings;
  private final SubscriptionClient subscription;

  public PortalBootstrapService(SettingsConfigService settings, SubscriptionClient subscription) {
    this.settings = settings;
    this.subscription = subscription;
  }

  @Transactional
  public Map<String, Object> bootstrap(String portalKey) {
    if (!PortalCatalog.isKnown(portalKey)) {
      throw new IllegalArgumentException("Unknown portal: " + portalKey);
    }
    TenantScope scope = TenantContext.require();
    String flag = PortalCatalog.featureFlag(portalKey);
    boolean enabled = subscription.isFeatureEnabled(scope, flag);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("portalKey", portalKey.toLowerCase());
    out.put("featureEnabled", enabled);
    out.put("requiredFeatureFlag", flag);
    if (!enabled) {
      return out;
    }

    String moduleKey = PortalCatalog.moduleKey(portalKey);
    ModuleSettings module = settings.getModule(moduleKey, scope.organizationId(), scope.branchId());
    Map<String, Object> cfg =
        module.getSettings() != null ? new LinkedHashMap<>(module.getSettings()) : new LinkedHashMap<>();

    out.put("moduleKey", moduleKey);
    out.put("title", cfg.getOrDefault("title", portalKey));
    out.put("subtitle", cfg.getOrDefault("subtitle", ""));
    out.put("roleCode", cfg.getOrDefault("roleCode", portalKey.toUpperCase()));
    out.put("profile", cfg.getOrDefault("profile", Map.of()));
    out.put("summary", cfg.getOrDefault("summary", Map.of()));
    out.put("notices", cfg.getOrDefault("notices", List.of()));
    out.put("nav", filterNav(asListOfMaps(cfg.get("nav")), scope));
    out.put("widgets", sortWidgets(asListOfMaps(cfg.get("widgets"))));
    out.put("sections", cfg.getOrDefault("sections", Map.of()));
    out.put("module", module);
    return out;
  }

  private List<Map<String, Object>> filterNav(List<Map<String, Object>> nav, TenantScope scope) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> item : nav) {
      Object flag = item.get("requiredFeatureFlag");
      if (flag != null && !String.valueOf(flag).isBlank()) {
        if (!subscription.isFeatureEnabled(scope, String.valueOf(flag))) {
          continue;
        }
      }
      out.add(item);
    }
    return out;
  }

  private List<Map<String, Object>> sortWidgets(List<Map<String, Object>> widgets) {
    return widgets.stream()
        .filter(w -> !Boolean.FALSE.equals(w.get("enabled")))
        .sorted(Comparator.comparingInt(w -> intOr(w.get("order"), 99)))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> asListOfMaps(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        out.add((Map<String, Object>) m);
      }
    }
    return out;
  }

  private static int intOr(Object v, int d) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return v != null ? Integer.parseInt(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }
}
