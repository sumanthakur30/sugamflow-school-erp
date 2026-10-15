package com.sugamflow.school.cms.service;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public/admin visibility for CMS rows.
 *
 * <ul>
 *   <li>No site id: only legacy rows ({@code site_id} null). Holly Cross stays on this path.
 *   <li>Site id + include legacy: that site plus null rows (organization default site).
 *   <li>Site id without legacy: that campus only.
 * </ul>
 */
public record CmsSiteScope(UUID siteId, boolean includeLegacy) {

  public static CmsSiteScope legacy() {
    return new CmsSiteScope(null, false);
  }

  public static CmsSiteScope parse(String siteId, Boolean includeLegacy) {
    if (siteId == null || siteId.isBlank()) {
      return legacy();
    }
    UUID id;
    try {
      id = UUID.fromString(siteId.trim());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "siteId must be a UUID");
    }
    return new CmsSiteScope(id, Boolean.TRUE.equals(includeLegacy));
  }

  public boolean matches(UUID rowSiteId) {
    if (siteId == null) {
      return rowSiteId == null;
    }
    if (includeLegacy) {
      return rowSiteId == null || siteId.equals(rowSiteId);
    }
    return siteId.equals(rowSiteId);
  }

  /** Slug uniqueness is per site. Null site ids share one bucket. */
  public boolean sameBucket(UUID rowSiteId) {
    if (siteId == null) {
      return rowSiteId == null;
    }
    return siteId.equals(rowSiteId);
  }
}
