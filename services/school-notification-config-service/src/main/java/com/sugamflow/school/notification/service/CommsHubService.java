package com.sugamflow.school.notification.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.notification.persistence.entity.CommsAnnouncementEntity;
import com.sugamflow.school.notification.persistence.repo.CommsAnnouncementRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Comms hub — announcements with durable guardian fan-out. */
@Service
public class CommsHubService {

  private static final Set<String> GUARDIAN_AUDIENCES =
      Set.of("ALL_ACTIVE", "PARENTS", "ALL", "GUARDIANS");

  private final CommsAnnouncementRepository repo;
  private final CommsFanOutService fanOut;

  public CommsHubService(CommsAnnouncementRepository repo, CommsFanOutService fanOut) {
    this.repo = repo;
    this.fanOut = fanOut;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("channels", List.of("IN_APP", "EMAIL", "SMS", "WHATSAPP"));
    out.put("audiences", List.of("PARENTS", "ALL_ACTIVE"));
    out.put("announcements", list(scope.organizationId()));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list() {
    TenantScope scope = TenantContext.require();
    return list(scope.organizationId());
  }

  @Transactional
  public Map<String, Object> create(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String title = str(body.get("title"));
    String bodyText = str(body.get("body"));
    if (title == null || bodyText == null) {
      throw new IllegalArgumentException("title and body are required");
    }
    String audience = strOr(body.get("audience"), "PARENTS").toUpperCase(Locale.ROOT);
    if ("CLASS_SECTION".equals(audience)) {
      throw new IllegalArgumentException("CLASS_SECTION audience is not implemented yet");
    }
    if (!GUARDIAN_AUDIENCES.contains(audience)) {
      throw new IllegalArgumentException("Unsupported audience: " + audience);
    }

    CommsAnnouncementEntity e = new CommsAnnouncementEntity();
    e.setId(UUID.randomUUID());
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setTitle(title);
    e.setBody(bodyText);
    e.setChannel(strOr(body.get("channel"), "IN_APP").toUpperCase(Locale.ROOT));
    e.setAudience(audience);
    e.setStatus("QUEUED");
    e.setCreatedBy(scope.userId());
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    List<Map<String, Object>> delivery = new ArrayList<>();
    Map<String, Object> queued = new LinkedHashMap<>();
    queued.put("status", "QUEUED");
    queued.put("at", Instant.now().toString());
    delivery.add(queued);
    e.setDeliveryJson(delivery);
    e = repo.save(e);

    Map<String, Object> summary = fanOut.fanOut(scope, e);
    Map<String, Object> dto = toDto(e);
    dto.put("fanOut", summary);
    return dto;
  }

  private List<Map<String, Object>> list(String org) {
    return repo.findByOrganizationIdOrderByCreatedAtDesc(org).stream()
        .limit(50)
        .map(this::toDto)
        .toList();
  }

  private Map<String, Object> toDto(CommsAnnouncementEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId().toString());
    m.put("title", e.getTitle());
    m.put("body", e.getBody());
    m.put("channel", e.getChannel());
    m.put("audience", e.getAudience());
    m.put("status", e.getStatus());
    m.put("delivery", e.getDeliveryJson());
    m.put("createdBy", e.getCreatedBy());
    m.put("createdAt", e.getCreatedAt().toString());
    return m;
  }

  private static String str(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    return s.isEmpty() ? null : s;
  }

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null ? fallback : s;
  }
}
