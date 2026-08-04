package com.sugamflow.school.notification.service;

import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.notification.integration.NotificationDeliveryClient;
import com.sugamflow.school.notification.integration.StudentGuardianClient;
import com.sugamflow.school.notification.persistence.entity.CommsAlertOutboxEntity;
import com.sugamflow.school.notification.persistence.entity.CommsAnnouncementEntity;
import com.sugamflow.school.notification.persistence.repo.CommsAlertOutboxRepository;
import com.sugamflow.school.notification.persistence.repo.CommsAnnouncementRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Expands QUEUED announcements into durable per-recipient outbox rows and dispatches them through
 * the shared notification pipeline (IN_APP + optional EMAIL/SMS).
 */
@Service
public class CommsFanOutService {

  private static final Logger log = LoggerFactory.getLogger(CommsFanOutService.class);
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration RETRY_WINDOW = Duration.ofDays(7);
  private static final int RETRY_BATCH = 100;

  private final CommsAnnouncementRepository announcements;
  private final CommsAlertOutboxRepository outbox;
  private final StudentGuardianClient guardians;
  private final NotificationDeliveryClient delivery;

  public CommsFanOutService(
      CommsAnnouncementRepository announcements,
      CommsAlertOutboxRepository outbox,
      StudentGuardianClient guardians,
      NotificationDeliveryClient delivery) {
    this.announcements = announcements;
    this.outbox = outbox;
    this.guardians = guardians;
    this.delivery = delivery;
  }

  @Transactional
  public Map<String, Object> fanOut(TenantScope scope, CommsAnnouncementEntity announcement) {
    announcement.setStatus("DISPATCHING");
    announcement.setUpdatedAt(Instant.now());
    announcements.save(announcement);

    List<Map<String, Object>> targets = guardians.deliveryTargets(scope);
    List<String> channels = channelsFor(announcement.getChannel());
    int created = 0;
    for (Map<String, Object> target : targets) {
      for (String channel : channels) {
        String recipient = recipientFor(target, channel);
        if (recipient == null || recipient.isBlank()) {
          continue;
        }
        if (outbox
            .findByAnnouncementIdAndChannelAndRecipient(announcement.getId(), channel, recipient)
            .isPresent()) {
          continue;
        }
        CommsAlertOutboxEntity row = new CommsAlertOutboxEntity();
        row.setId(UUID.randomUUID());
        row.setOrganizationId(announcement.getOrganizationId());
        row.setBranchId(announcement.getBranchId());
        row.setAnnouncementId(announcement.getId());
        row.setChannel(channel);
        row.setRecipient(recipient);
        row.setGuardianName(str(target.get("fullName")));
        row.setSubject(truncate(announcement.getTitle(), 255));
        row.setBody(announcement.getBody());
        row.setStatus(CommsAlertOutboxEntity.STATUS_PENDING);
        row.setCreatedAt(Instant.now());
        row.setUpdatedAt(Instant.now());
        outbox.save(row);
        created++;
      }
    }

    int sent = 0;
    int failed = 0;
    for (CommsAlertOutboxEntity row :
        outbox.findByAnnouncementIdOrderByCreatedAtAsc(announcement.getId())) {
      if (!CommsAlertOutboxEntity.STATUS_SENT.equals(row.getStatus())) {
        row = dispatch(row);
      }
      if (CommsAlertOutboxEntity.STATUS_SENT.equals(row.getStatus())) {
        sent++;
      } else if (CommsAlertOutboxEntity.STATUS_FAILED.equals(row.getStatus())) {
        failed++;
      }
    }

    Map<String, Object> summary = summarize(announcement.getId(), targets.size(), created, sent, failed);
    announcement.setDeliveryJson(List.of(summary));
    if (sent > 0 && failed == 0) {
      announcement.setStatus("SENT");
    } else if (sent > 0) {
      announcement.setStatus("PARTIAL_FAILED");
    } else if (targets.isEmpty()) {
      announcement.setStatus("FAILED");
      summary.put("error", "No guardian delivery targets found");
      announcement.setDeliveryJson(List.of(summary));
    } else if (created == 0) {
      announcement.setStatus("FAILED");
      summary.put(
          "error",
          "Guardians found but none have a usable recipient for "
              + String.join("/", channels)
              + " (need portal login for IN_APP, email for EMAIL, mobile for SMS/WHATSAPP)");
      announcement.setDeliveryJson(List.of(summary));
    } else {
      announcement.setStatus(failed > 0 ? "FAILED" : "SENT");
    }
    announcement.setUpdatedAt(Instant.now());
    announcements.save(announcement);
    return summary;
  }

  public int retryPending() {
    for (CommsAnnouncementEntity announcement :
        announcements.findDispatchable(PageRequest.of(0, 20))) {
      try {
        TenantScope scope =
            new TenantScope(
                announcement.getOrganizationId(),
                announcement.getBranchId(),
                null,
                announcement.getCreatedBy(),
                "ADMIN");
        fanOut(scope, announcement);
      } catch (Exception ex) {
        log.warn("Comms fan-out failed for {}: {}", announcement.getId(), ex.getMessage());
      }
    }

    List<CommsAlertOutboxEntity> retryable =
        outbox.findRetryable(MAX_ATTEMPTS, Instant.now().minus(RETRY_WINDOW), PageRequest.of(0, RETRY_BATCH));
    int sent = 0;
    for (CommsAlertOutboxEntity row : retryable) {
      try {
        if (CommsAlertOutboxEntity.STATUS_SENT.equals(dispatch(row).getStatus())) {
          sent++;
        }
      } catch (Exception ex) {
        log.warn("Comms outbox retry failed for {}: {}", row.getId(), ex.getMessage());
      }
    }
    if (!retryable.isEmpty()) {
      log.info("Comms alert retry: {} row(s) processed, {} sent", retryable.size(), sent);
    }
    return sent;
  }

  private CommsAlertOutboxEntity dispatch(CommsAlertOutboxEntity entry) {
    entry.setAttempts(entry.getAttempts() + 1);
    entry.setUpdatedAt(Instant.now());
    Map<String, Object> response =
        delivery.queue(
            entry.getOrganizationId(),
            entry.getChannel(),
            entry.getRecipient(),
            entry.getSubject(),
            entry.getBody(),
            "comms-ann-" + entry.getId());
    String deliveryStatus = String.valueOf(response.getOrDefault("status", "UNKNOWN"));
    if ("FAILED".equalsIgnoreCase(deliveryStatus) || "UNKNOWN".equalsIgnoreCase(deliveryStatus)) {
      entry.setStatus(CommsAlertOutboxEntity.STATUS_FAILED);
      entry.setLastError(truncate(str(response.get("error")), 512));
    } else {
      entry.setStatus(CommsAlertOutboxEntity.STATUS_SENT);
      entry.setSentAt(Instant.now());
      entry.setLastError(null);
    }
    if (response.get("id") != null) {
      entry.setNotificationId(String.valueOf(response.get("id")));
    }
    return outbox.save(entry);
  }

  private Map<String, Object> summarize(
      UUID announcementId, int guardians, int created, int sent, int failed) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("guardians", guardians);
    summary.put("outboxCreated", created);
    summary.put("sent", sent);
    summary.put("failed", failed);
    summary.put("pending", Math.max(0, outbox.findByAnnouncementIdOrderByCreatedAtAsc(announcementId).size() - sent - failed));
    summary.put("at", Instant.now().toString());
    return summary;
  }

  private static List<String> channelsFor(String selected) {
    String upper = selected == null ? "IN_APP" : selected.toUpperCase(Locale.ROOT);
    // Always include IN_APP for linked portal accounts. Contact channels use email/mobile when present
    // (many schools publish "In-app" while guardians only have phone/email on file).
    if ("IN_APP".equals(upper)) {
      return List.of("IN_APP", "EMAIL", "SMS", "WHATSAPP");
    }
    if ("EMAIL".equals(upper) || "SMS".equals(upper) || "WHATSAPP".equals(upper)) {
      return List.of("IN_APP", upper);
    }
    return List.of("IN_APP", "EMAIL", "SMS", "WHATSAPP");
  }

  private static String recipientFor(Map<String, Object> target, String channel) {
    if ("IN_APP".equals(channel)) {
      return str(target.get("identity"));
    }
    if ("EMAIL".equals(channel)) {
      return str(target.get("email"));
    }
    if ("SMS".equals(channel) || "WHATSAPP".equals(channel)) {
      return str(target.get("mobile"));
    }
    return null;
  }

  private static String str(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String truncate(String v, int max) {
    if (v == null) {
      return null;
    }
    return v.length() <= max ? v : v.substring(0, max);
  }
}
