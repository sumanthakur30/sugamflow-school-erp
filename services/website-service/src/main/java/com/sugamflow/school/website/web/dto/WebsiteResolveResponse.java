package com.sugamflow.school.website.web.dto;

import java.util.List;
import java.util.Map;

/** Public shell payload returned after Host → tenant resolution. */
public record WebsiteResolveResponse(
    String organizationId,
    String host,
    String status,
    String templateCode,
    String displayName,
    String erpLoginUrl,
    /** Optional CDN base for media/static assets; empty means same-origin. */
    String cdnBaseUrl,
    Map<String, Object> theme,
    List<Map<String, Object>> homepage,
    List<Map<String, Object>> navigation,
    Map<String, Object> seo) {}
