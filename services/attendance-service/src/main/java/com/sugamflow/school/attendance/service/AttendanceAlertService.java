package com.sugamflow.school.attendance.service;

import com.sugamflow.school.attendance.integration.ConfigEngineClient;
import com.sugamflow.school.attendance.integration.NotificationDeliveryClient;
import com.sugamflow.school.attendance.integration.StudentRecordClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceAlertOutboxEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceAlertOutboxRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceMarkRepository;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Parent alerts for roster attendance, backed by a durable per-channel outbox.
 *
 * <p>When a session is submitted, one outbox row is created per (mark, status, channel, recipient)
 * — the unique constraint makes creation idempotent across re-submits and crashes. Each row is
 * dispatched to the shared notification pipeline with an idempotency key derived from the row id,
 * so a crash between send and commit cannot double-deliver. FAILED/PENDING rows are retried on the
 * next submit and by the scheduled retry job; a channel that failed is retried even when another
 * channel already delivered (per-channel, not mark-wide, idempotency).
 */
@Service
public class AttendanceAlertService {

  private static final Logger log = LoggerFactory.getLogger(AttendanceAlertService.class);
  private static final Set<String> DEFAULT_ALERT_STATUSES = Set.of("ABSENT", "LATE");
  private static final List<String> DEFAULT_CHANNELS = List.of("SMS", "EMAIL", "IN_APP");
  private static final String MODULE_ATTENDANCE = "attendance";
  private static final String EVENT = "ATTENDANCE";
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration RETRY_WINDOW = Duration.ofDays(7);
  private static final int RETRY_BATCH = 100;

  private final AttendanceMarkRepository marks;
  private final AttendanceAlertOutboxRepository outbox;
  private final ConfigEngineClient engines;
  private final NotificationDeliveryClient delivery;
  private final StudentRecordClient studentRecords;

  public AttendanceAlertService(
      AttendanceMarkRepository marks,
      AttendanceAlertOutboxRepository outbox,
      ConfigEngineClient engines,
      NotificationDeliveryClient delivery,
      StudentRecordClient studentRecords) {
    this.marks = marks;
    this.outbox = outbox;
    this.engines = engines;
    this.delivery = delivery;
    this.studentRecords = studentRecords;
  }

  /**
   * Called after a roster session transitions to SUBMITTED. Never throws — alert failures must not
   * roll back the attendance submit.
   */
  public Map<String, Object> onSessionSubmitted(
      TenantScope scope, AttendanceSessionEntity session, String sectionLabel) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("notified", 0);
    summary.put("skipped", 0);
    try {
      Map<String, Object> settings = moduleSettings(engines.getModuleSettings(scope, MODULE_ATTENDANCE));
      if (Boolean.FALSE.equals(settings.get("notifyOnRosterSubmit"))) {
        summary.put("disabled", true);
        return summary;
      }
      Set<String> alertStatuses = alertStatuses(settings);
      List<String> channels = channels(settings);

      int notified = 0;
      int skipped = 0;
      List<Map<String, Object>> alerts = new ArrayList<>();
      for (AttendanceMarkEntity mark : marks.findBySessionIdOrderByStudentNameAsc(session.getId())) {
        String status = mark.getStatus() == null ? "" : mark.getStatus().toUpperCase(Locale.ROOT);
        if (!alertStatuses.contains(status)) {
          continue;
        }
        Map<String, Object> result = processMark(scope, session, sectionLabel, mark, settings, channels);
        if (Boolean.TRUE.equals(result.get("alreadyNotified"))) {
          skipped++;
          continue;
        }
        alerts.add(result);
        if (Boolean.TRUE.equals(result.get("delivered"))) {
          mark.setLastNotifiedStatus(status);
          mark.setLastNotifiedAt(Instant.now());
          mark.setUpdatedAt(Instant.now());
          marks.save(mark);
          notified++;
        } else {
          skipped++;
        }
      }
      summary.put("notified", notified);
      summary.put("skipped", skipped);
      summary.put("alerts", alerts);
    } catch (Exception ex) {
      log.warn("Attendance parent alerts failed for session {}: {}", session.getId(), ex.getMessage());
      summary.put("error", ex.getMessage());
    }
    return summary;
  }

  /**
   * Ensure outbox rows exist for every reachable channel of the mark's guardian, then dispatch the
   * rows that are not yet SENT. Returns the per-mark alert result for the submit summary.
   */
  private Map<String, Object> processMark(
      TenantScope scope,
      AttendanceSessionEntity session,
      String sectionLabel,
      AttendanceMarkEntity mark,
      Map<String, Object> settings,
      List<String> channels) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("studentName", mark.getStudentName());
    out.put("admissionNo", mark.getAdmissionNo());
    out.put("status", mark.getStatus());
    out.put("delivered", false);

    String status = mark.getStatus().toUpperCase(Locale.ROOT);
    String intent = "ATTENDANCE_" + status;

    List<AttendanceAlertOutboxEntity> existing = outbox.findByMarkIdAndMarkStatus(mark.getId(), status);
    Map<String, Object> loadedStudent =
        mark.getStudentId() == null
            ? Map.of()
            : studentRecords.getStudent(scope, mark.getStudentId().toString());
    Map<String, Object> student = loadedStudent == null ? Map.of() : loadedStudent;
    Map<String, Object> guardian = primaryGuardian(student);
    Set<String> linkedParents = linkedParentRecipients(student);
    if (fullyDelivered(existing, channels, guardian, linkedParents)) {
      out.put("alreadyNotified", true);
      return out;
    }
    if (guardian.isEmpty() && linkedParents.isEmpty()) {
      if (existing.isEmpty()) {
        out.put("skippedReason", "NO_GUARDIAN_CONTACT");
        return out;
      }
      // Guardian lookup failed now, but durable rows exist from an earlier submit: retry those.
      List<Map<String, Object>> deliveries = new ArrayList<>();
      boolean anyDelivered = false;
      for (AttendanceAlertOutboxEntity entry : existing) {
        if (!AttendanceAlertOutboxEntity.STATUS_SENT.equals(entry.getStatus())) {
          entry = dispatch(entry);
        }
        if (AttendanceAlertOutboxEntity.STATUS_SENT.equals(entry.getStatus())) {
          anyDelivered = true;
        }
        deliveries.add(deliveryRow(entry));
      }
      out.put("intent", intent);
      out.put("delivery", deliveries);
      out.put("delivered", anyDelivered);
      return out;
    }

    String[] content = composeMessage(scope, session, sectionLabel, mark, settings, status, intent);
    String subject = content[0];
    String body = content[1];

    List<Map<String, Object>> deliveries = new ArrayList<>();
    boolean anyDelivered = false;
    for (String channel : channels) {
      String upper = channel.toUpperCase(Locale.ROOT);
      List<String> recipients =
          "IN_APP".equals(upper)
              ? new ArrayList<>(linkedParents)
              : List.of(recipientFor(guardian, upper) == null ? "" : recipientFor(guardian, upper));
      if (recipients.isEmpty() || recipients.stream().allMatch(String::isBlank)) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("channel", upper);
        row.put("status", "SKIPPED");
        row.put(
            "error",
            "IN_APP".equals(upper)
                ? "No linked guardian account"
                : "No guardian contact for channel " + upper);
        deliveries.add(row);
        continue;
      }
      for (String recipient : recipients) {
        if (recipient.isBlank()) {
          continue;
        }
        AttendanceAlertOutboxEntity entry =
            outbox
                .findByMarkIdAndMarkStatusAndChannelAndRecipient(mark.getId(), status, upper, recipient)
                .orElseGet(
                    () ->
                        newOutboxRow(
                            scope, session, mark, status, intent, upper, recipient,
                            str(guardian.get("fullName")), subject, body));
        if (!AttendanceAlertOutboxEntity.STATUS_SENT.equals(entry.getStatus())) {
          entry = dispatch(entry);
        }
        if (AttendanceAlertOutboxEntity.STATUS_SENT.equals(entry.getStatus())) {
          anyDelivered = true;
        }
        deliveries.add(deliveryRow(entry));
      }
    }
    out.put("intent", intent);
    out.put("guardianName", guardian.get("fullName"));
    out.put("delivery", deliveries);
    out.put("delivered", anyDelivered);
    return out;
  }

  private boolean fullyDelivered(
      List<AttendanceAlertOutboxEntity> existing,
      List<String> channels,
      Map<String, Object> guardian,
      Set<String> linkedParents) {
    if (existing.isEmpty()) {
      return false;
    }
    Set<String> expected = new LinkedHashSet<>();
    for (String channel : channels) {
      String upper = channel.toUpperCase(Locale.ROOT);
      if ("IN_APP".equals(upper)) {
        for (String recipient : linkedParents) {
          expected.add(upper + "|" + recipient);
        }
      } else {
        String recipient = recipientFor(guardian, upper);
        if (recipient != null && !recipient.isBlank()) {
          expected.add(upper + "|" + recipient);
        }
      }
    }
    if (expected.isEmpty()) {
      return existing.stream()
          .allMatch(row -> AttendanceAlertOutboxEntity.STATUS_SENT.equals(row.getStatus()));
    }
    Set<String> sent =
        existing.stream()
            .filter(row -> AttendanceAlertOutboxEntity.STATUS_SENT.equals(row.getStatus()))
            .map(row -> row.getChannel() + "|" + row.getRecipient())
            .collect(java.util.stream.Collectors.toSet());
    return sent.containsAll(expected);
  }

  private AttendanceAlertOutboxEntity newOutboxRow(
      TenantScope scope,
      AttendanceSessionEntity session,
      AttendanceMarkEntity mark,
      String status,
      String intent,
      String channel,
      String recipient,
      String guardianName,
      String subject,
      String body) {
    AttendanceAlertOutboxEntity entry = new AttendanceAlertOutboxEntity();
    entry.setId(UUID.randomUUID());
    entry.setOrganizationId(scope.organizationId());
    entry.setBranchId(scope.branchId());
    entry.setAcademicSessionId(scope.academicSessionId());
    entry.setSessionId(session.getId());
    entry.setMarkId(mark.getId());
    entry.setAdmissionNo(mark.getAdmissionNo());
    entry.setStudentName(mark.getStudentName());
    entry.setMarkStatus(status);
    entry.setIntent(intent);
    entry.setChannel(channel);
    entry.setRecipient(recipient);
    entry.setGuardianName(guardianName);
    entry.setSubject(subject);
    entry.setBody(body);
    entry.setStatus(AttendanceAlertOutboxEntity.STATUS_PENDING);
    entry.setCreatedAt(Instant.now());
    entry.setUpdatedAt(Instant.now());
    // Persist as PENDING before any network call so a crash mid-dispatch leaves a retryable row.
    return outbox.save(entry);
  }

  /**
   * Send one outbox row through the notification pipeline. The idempotency key is derived from the
   * stable row id, so retries after a crash or timeout cannot double-deliver.
   */
  private AttendanceAlertOutboxEntity dispatch(AttendanceAlertOutboxEntity entry) {
    entry.setAttempts(entry.getAttempts() + 1);
    entry.setUpdatedAt(Instant.now());
    Map<String, Object> response =
        delivery.queue(
            entry.getOrganizationId(),
            entry.getChannel(),
            entry.getRecipient(),
            entry.getSubject(),
            entry.getBody(),
            "att-alert-" + entry.getId());
    String deliveryStatus = String.valueOf(response.getOrDefault("status", "UNKNOWN"));
    if ("FAILED".equalsIgnoreCase(deliveryStatus) || "UNKNOWN".equalsIgnoreCase(deliveryStatus)) {
      entry.setStatus(AttendanceAlertOutboxEntity.STATUS_FAILED);
      entry.setLastError(truncate(str(response.get("error")), 512));
    } else {
      entry.setStatus(AttendanceAlertOutboxEntity.STATUS_SENT);
      entry.setSentAt(Instant.now());
      entry.setLastError(null);
    }
    if (response.get("id") != null) {
      entry.setNotificationId(String.valueOf(response.get("id")));
    }
    return outbox.save(entry);
  }

  /** Retry PENDING/FAILED rows (recent, under the attempt cap). Invoked by the scheduled job. */
  public int retryPending() {
    List<AttendanceAlertOutboxEntity> retryable =
        outbox.findRetryable(MAX_ATTEMPTS, Instant.now().minus(RETRY_WINDOW), PageRequest.of(0, RETRY_BATCH));
    int sent = 0;
    for (AttendanceAlertOutboxEntity entry : retryable) {
      try {
        if (AttendanceAlertOutboxEntity.STATUS_SENT.equals(dispatch(entry).getStatus())) {
          sent++;
        }
      } catch (Exception ex) {
        log.warn("Attendance alert retry failed for outbox {}: {}", entry.getId(), ex.getMessage());
      }
    }
    if (!retryable.isEmpty()) {
      log.info("Attendance alert retry: {} row(s) processed, {} sent", retryable.size(), sent);
    }
    return sent;
  }

  /** Delivery history for one session, newest attempt state per channel/recipient. */
  public List<Map<String, Object>> deliveryHistory(UUID sessionId) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (AttendanceAlertOutboxEntity entry : outbox.findBySessionIdOrderByCreatedAtAsc(sessionId)) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("id", entry.getId().toString());
      row.put("markId", entry.getMarkId().toString());
      row.put("studentName", entry.getStudentName());
      row.put("admissionNo", entry.getAdmissionNo());
      row.put("markStatus", entry.getMarkStatus());
      row.put("channel", entry.getChannel());
      row.put("recipient", entry.getRecipient());
      row.put("guardianName", entry.getGuardianName());
      row.put("status", entry.getStatus());
      row.put("attempts", entry.getAttempts());
      row.put("error", entry.getLastError());
      row.put("notificationId", entry.getNotificationId());
      row.put("sentAt", entry.getSentAt() == null ? null : entry.getSentAt().toString());
      row.put("updatedAt", entry.getUpdatedAt() == null ? null : entry.getUpdatedAt().toString());
      out.add(row);
    }
    return out;
  }

  private Map<String, Object> deliveryRow(AttendanceAlertOutboxEntity entry) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("channel", entry.getChannel());
    row.put("recipient", entry.getRecipient());
    row.put("status", entry.getStatus());
    row.put("attempts", entry.getAttempts());
    if (entry.getNotificationId() != null) {
      row.put("notificationId", entry.getNotificationId());
    }
    if (entry.getLastError() != null) {
      row.put("error", entry.getLastError());
    }
    return row;
  }

  private String[] composeMessage(
      TenantScope scope,
      AttendanceSessionEntity session,
      String sectionLabel,
      AttendanceMarkEntity mark,
      Map<String, Object> settings,
      String status,
      String intent) {
    Map<String, Object> attendance = new LinkedHashMap<>();
    attendance.put("studentName", mark.getStudentName());
    attendance.put("admissionNo", mark.getAdmissionNo());
    attendance.put("status", status);
    attendance.put("date", session.getAttendanceDate().toString());
    attendance.put("classSection", sectionLabel);
    attendance.put("remark", mark.getRemark() == null ? "" : mark.getRemark());

    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("attendance", attendance);
    Map<String, Object> context = new LinkedHashMap<>();
    context.put("organizationId", scope.organizationId());
    context.put("branchId", scope.branchId());
    context.put("sessionId", session.getId().toString());
    context.put("markId", mark.getId().toString());
    variables.put("context", context);

    String templateId = stringOr(settings.get("rosterNotificationTemplateId"), null);
    Map<String, Object> resolved = Map.of();
    if (templateId != null) {
      resolved = engines.resolveNotification(scope, EVENT, templateId, variables);
    }
    if (resolved.isEmpty() || !Boolean.TRUE.equals(resolved.get("resolved"))) {
      resolved = engines.resolveNotification(scope, EVENT, intent, variables);
    }
    String subject =
        String.valueOf(
            resolved.getOrDefault(
                "subject", "Attendance alert — " + nz(mark.getStudentName(), "student")));
    String body =
        String.valueOf(
            resolved.getOrDefault(
                "body",
                nz(mark.getStudentName(), "Your child")
                    + " was marked "
                    + status
                    + " for "
                    + nz(sectionLabel, "class")
                    + " on "
                    + session.getAttendanceDate()
                    + "."));
    return new String[] {truncate(subject, 255), body};
  }

  /**
   * Build a channel-capable guardian contact map. Prefer the primary guardian, then fill missing
   * email/mobile from other guardians so SMS and EMAIL can both deliver when contacts are split.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> primaryGuardian(Map<String, Object> student) {
    Object raw = student.get("guardians");
    if (!(raw instanceof List<?> list) || list.isEmpty()) {
      // Fall back to student-level contact if guardians are missing.
      Object answers = student.get("answers");
      if (answers instanceof Map<?, ?> a) {
        Map<String, Object> fallback = new LinkedHashMap<>();
        Object mobile = a.get("guardianMobile") != null ? a.get("guardianMobile") : a.get("mobile");
        Object email = a.get("email");
        if (mobile != null || email != null) {
          fallback.put("fullName", nz(str(a.get("guardianFullName")), "Guardian"));
          fallback.put("mobile", str(mobile));
          fallback.put("email", str(email));
          return fallback;
        }
      }
      return Map.of();
    }

    Map<String, Object> primary = null;
    Map<String, Object> firstWithContact = null;
    String mobile = null;
    String email = null;
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> g)) {
        continue;
      }
      Map<String, Object> guardian = (Map<String, Object>) g;
      boolean hasContact = hasText(guardian.get("mobile")) || hasText(guardian.get("email"));
      if (!hasContact) {
        continue;
      }
      if (mobile == null && hasText(guardian.get("mobile"))) {
        mobile = str(guardian.get("mobile"));
      }
      if (email == null && hasText(guardian.get("email"))) {
        email = str(guardian.get("email"));
      }
      if (Boolean.TRUE.equals(guardian.get("isPrimary"))
          || "true".equalsIgnoreCase(str(guardian.get("isPrimary")))) {
        primary = guardian;
      }
      if (firstWithContact == null) {
        firstWithContact = guardian;
      }
    }
    Map<String, Object> chosen = primary != null ? primary : firstWithContact;
    if (chosen == null) {
      return Map.of();
    }
    Map<String, Object> merged = new LinkedHashMap<>(chosen);
    if (!hasText(merged.get("mobile")) && mobile != null) {
      merged.put("mobile", mobile);
    }
    if (!hasText(merged.get("email")) && email != null) {
      merged.put("email", email);
    }
    return merged;
  }

  /** Return one canonical login identity per linked guardian, preserving guardian order. */
  private Set<String> linkedParentRecipients(Map<String, Object> student) {
    Set<String> recipients = new LinkedHashSet<>();
    Object raw = student.get("guardians");
    if (!(raw instanceof List<?> list)) {
      return recipients;
    }
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> guardian)) {
        continue;
      }
      String identity =
          firstNonBlank(
              str(guardian.get("authUsername")),
              str(guardian.get("username")),
              str(guardian.get("userId")));
      if (identity != null) {
        recipients.add(identity);
      }
    }
    return recipients;
  }

  private static String recipientFor(Map<String, Object> guardian, String channel) {
    if ("EMAIL".equals(channel)) {
      return str(guardian.get("email"));
    }
    if ("SMS".equals(channel) || "WHATSAPP".equals(channel)) {
      return str(guardian.get("mobile"));
    }
    return null;
  }

  private static Set<String> alertStatuses(Map<String, Object> settings) {
    Object configured = settings.get("rosterAlertStatuses");
    if (configured instanceof List<?> list && !list.isEmpty()) {
      Set<String> out = new java.util.LinkedHashSet<>();
      for (Object item : list) {
        out.add(String.valueOf(item).toUpperCase(Locale.ROOT));
      }
      return out;
    }
    return DEFAULT_ALERT_STATUSES;
  }

  private static List<String> channels(Map<String, Object> settings) {
    Object configured = settings.get("rosterNotificationChannels");
    if (configured instanceof List<?> list && !list.isEmpty()) {
      return list.stream().map(String::valueOf).toList();
    }
    return DEFAULT_CHANNELS;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> moduleSettings(Map<String, Object> module) {
    if (module == null) {
      return Map.of();
    }
    Object settings = module.get("settings");
    if (settings instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return module;
  }

  private static boolean hasText(Object v) {
    return v != null && !String.valueOf(v).trim().isEmpty();
  }

  private static String str(Object v) {
    if (v == null) {
      return null;
    }
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String nz(String v, String fallback) {
    return v == null || v.isBlank() ? fallback : v;
  }

  private static String stringOr(Object v, String fallback) {
    String s = str(v);
    return s != null ? s : fallback;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return null;
  }

  private static String truncate(String v, int max) {
    if (v == null) {
      return null;
    }
    return v.length() <= max ? v : v.substring(0, max);
  }
}
