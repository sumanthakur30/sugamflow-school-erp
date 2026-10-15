package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.persistence.entity.AppUserEntity;
import com.sugamflow.school.student.persistence.repo.AppUserRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserService {

  private static final Set<String> ROLES = Set.of("STUDENT", "PARENT", "TEACHER");

  private final AppUserRepository repository;

  public AppUserService(AppUserRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list() {
    TenantScope scope = TenantContext.require();
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> save(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String username = text(body.get("username"), null);
    if (username == null) {
      throw new StudentException("VALIDATION", "username is required");
    }
    String role = text(body.get("roleCode"), "STUDENT").toUpperCase(Locale.ROOT);
    if (!ROLES.contains(role)) {
      throw new StudentException("VALIDATION", "roleCode must be STUDENT, PARENT, or TEACHER");
    }
    AppUserEntity entity =
        repository
            .findByOrganizationIdAndUsernameIgnoreCase(scope.organizationId(), username)
            .orElseGet(AppUserEntity::new);
    Instant now = Instant.now();
    if (entity.getId() == null) {
      entity.setId(java.util.UUID.randomUUID());
      entity.setOrganizationId(scope.organizationId());
      entity.setCreatedAt(now);
    }
    entity.setRoleCode(role);
    entity.setUsername(username);
    entity.setSubjectRef(text(body.get("subjectRef"), entity.getSubjectRef()));
    entity.setDisplayName(text(body.get("displayName"), entity.getDisplayName()));
    if (body.get("installed") != null) {
      entity.setInstalled(Boolean.parseBoolean(String.valueOf(body.get("installed"))));
    }
    if (entity.isInstalled() && entity.getLastSeenAt() == null) {
      entity.setLastSeenAt(now);
    }
    entity.setUpdatedAt(now);
    return toDto(repository.save(entity));
  }

  /**
   * Records who should receive an auth password reset. The UI calls auth-service
   * POST /api/v1/auth/password-reset/admin with the returned username.
   */
  @Transactional
  public Map<String, Object> reset(Map<String, Object> body) {
    Map<String, Object> saved = save(body);
    Map<String, Object> out = new LinkedHashMap<>(saved);
    out.put("resetRequested", true);
    out.put("message", "Password reset requested. Send it through auth for this username.");
    return out;
  }

  private Map<String, Object> toDto(AppUserEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId().toString());
    row.put("roleCode", entity.getRoleCode());
    row.put("subjectRef", entity.getSubjectRef());
    row.put("displayName", entity.getDisplayName());
    row.put("username", entity.getUsername());
    row.put("installed", entity.isInstalled());
    row.put("lastSeenAt", entity.getLastSeenAt() == null ? null : entity.getLastSeenAt().toString());
    return row;
  }

  private static String text(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? fallback : text;
  }
}
