package com.sugamflow.school.website.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.website.persistence.entity.WebsiteAnalyticsEvent;
import com.sugamflow.school.website.persistence.repo.WebsiteAnalyticsEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WebsiteAnalyticsService {

  private static final Logger log = LoggerFactory.getLogger(WebsiteAnalyticsService.class);

  private final WebsiteAnalyticsEventRepository repository;
  private final WebsiteResolveService resolveService;
  private final ObjectMapper objectMapper;
  private final MeterRegistry meterRegistry;
  private final int retentionDays;

  public WebsiteAnalyticsService(
      WebsiteAnalyticsEventRepository repository,
      WebsiteResolveService resolveService,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${website.analytics.retention-days:90}") int retentionDays) {
    this.repository = repository;
    this.resolveService = resolveService;
    this.objectMapper = objectMapper;
    this.meterRegistry = meterRegistry;
    this.retentionDays = Math.max(7, retentionDays);
  }

  @Transactional
  public Map<String, Object> track(Map<String, Object> body, String userAgent) {
    String host = stringOr(body.get("host"), "");
    String organizationId = stringOr(body.get("organizationId"), "");
    if (!host.isBlank()) {
      organizationId = resolveService.resolveByHost(host).organizationId();
    }
    if (organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "host or organizationId required");
    }
    String eventType = stringOr(body.get("eventType"), "page_view").trim().toLowerCase(Locale.ROOT);
    if (eventType.length() > 64) {
      eventType = eventType.substring(0, 64);
    }

    WebsiteAnalyticsEvent event = new WebsiteAnalyticsEvent();
    event.setId(UUID.randomUUID());
    event.setOrganizationId(organizationId);
    event.setHost(blankToNull(host));
    event.setEventType(eventType);
    event.setPath(truncate(stringOr(body.get("path"), "/"), 512));
    event.setReferrer(truncate(stringOr(body.get("referrer"), ""), 1024));
    event.setUserAgent(truncate(userAgent == null ? "" : userAgent, 512));
    Object meta = body.get("meta");
    try {
      event.setMetaJson(objectMapper.writeValueAsString(meta == null ? Map.of() : meta));
    } catch (Exception ex) {
      event.setMetaJson("{}");
    }
    event.setCreatedAt(Instant.now());
    repository.save(event);

    meterRegistry
        .counter("website.analytics.events", "type", eventType, "org", organizationId)
        .increment();

    return Map.of(
        "id", event.getId().toString(),
        "organizationId", organizationId,
        "eventType", eventType,
        "accepted", true);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary(String organizationId, int days) {
    int window = Math.max(1, Math.min(days, 90));
    Instant since = Instant.now().minus(window, ChronoUnit.DAYS);
    List<Object[]> counts = repository.countByTypeSince(organizationId, since);
    Map<String, Long> byType = new LinkedHashMap<>();
    long total = 0;
    for (Object[] row : counts) {
      String type = String.valueOf(row[0]);
      long count = row[1] instanceof Number n ? n.longValue() : 0L;
      byType.put(type, count);
      total += count;
    }
    List<Map<String, Object>> recent = new ArrayList<>();
    for (WebsiteAnalyticsEvent e :
        repository.findTop100ByOrganizationIdOrderByCreatedAtDesc(organizationId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", e.getId().toString());
      row.put("eventType", e.getEventType());
      row.put("path", e.getPath());
      row.put("host", e.getHost());
      row.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
      recent.add(row);
    }
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("organizationId", organizationId);
    payload.put("days", window);
    payload.put("totalEvents", total);
    payload.put("byType", byType);
    payload.put("recent", recent);
    return payload;
  }

  @Scheduled(cron = "0 20 3 * * *")
  @Transactional
  public void purgeExpired() {
    Instant before = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
    int deleted = repository.deleteOlderThan(before);
    if (deleted > 0) {
      log.info("Purged {} website analytics events older than {} days", deleted, retentionDays);
    }
  }

  private static String stringOr(Object value, String fallback) {
    return value == null ? fallback : String.valueOf(value).trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String truncate(String value, int max) {
    if (value == null) return null;
    return value.length() <= max ? value : value.substring(0, max);
  }
}
