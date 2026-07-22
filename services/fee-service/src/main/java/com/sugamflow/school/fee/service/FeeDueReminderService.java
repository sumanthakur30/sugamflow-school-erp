package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.NotificationDeliveryClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import com.sugamflow.school.fee.persistence.entity.FeeDueReminderOutboxEntity;
import com.sugamflow.school.fee.persistence.repo.FeeCollectionRepository;
import com.sugamflow.school.fee.persistence.repo.FeeDueReminderOutboxRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fee due reminders with durable per-channel outbox. Open collections (IN_PROGRESS /
 * INFO_REQUESTED) with positive amount are reminded once per day per guardian/channel.
 */
@Service
public class FeeDueReminderService {

  private static final Logger log = LoggerFactory.getLogger(FeeDueReminderService.class);
  private static final Set<String> OPEN = Set.of("IN_PROGRESS", "INFO_REQUESTED");
  private static final List<String> DEFAULT_CHANNELS = List.of("IN_APP", "EMAIL", "SMS");
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration RETRY_WINDOW = Duration.ofDays(7);

  private final FeeCollectionRepository collections;
  private final FeeDueReminderOutboxRepository outbox;
  private final StudentProfileClient students;
  private final NotificationDeliveryClient delivery;
  private final ConfigEngineClient engines;

  public FeeDueReminderService(
      FeeCollectionRepository collections,
      FeeDueReminderOutboxRepository outbox,
      StudentProfileClient students,
      NotificationDeliveryClient delivery,
      ConfigEngineClient engines) {
    this.collections = collections;
    this.outbox = outbox;
    this.students = students;
    this.delivery = delivery;
    this.engines = engines;
  }

  @Transactional
  public Map<String, Object> run(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    Map<String, Object> settings = moduleSettings(engines.getModuleSettings(scope, "fee"));
    if (Boolean.FALSE.equals(settings.get("notifyOnDue"))) {
      return Map.of("disabled", true, "reminded", 0);
    }
    LocalDate asOf = parseDate(body == null ? null : body.get("asOf"), LocalDate.now());
    String onlyAdmission = body == null ? null : str(body.get("admissionNo"));
    int minPendingDays = intOr(settings.get("dueReminderMinPendingDays"), 1);
    List<String> channels = channels(settings);

    int reminded = 0;
    int skipped = 0;
    int sent = 0;
    List<Map<String, Object>> details = new ArrayList<>();
    for (FeeCollectionEntity collection : loadOpen(scope)) {
      if (!OPEN.contains(String.valueOf(collection.getStatus()).toUpperCase(Locale.ROOT))) {
        continue;
      }
      Map<String, Object> answers =
          collection.getAnswers() == null ? Map.of() : collection.getAnswers();
      String admissionNo = str(answers.get("admissionNo"));
      if (admissionNo == null) {
        skipped++;
        continue;
      }
      if (onlyAdmission != null && !onlyAdmission.equalsIgnoreCase(admissionNo)) {
        continue;
      }
      BigDecimal amount = toAmount(answers.get("amount"));
      if (amount.compareTo(BigDecimal.ZERO) <= 0) {
        skipped++;
        continue;
      }
      int pendingDays = intOr(answers.get("pendingDays"), 0);
      LocalDate dueDate = parseDate(answers.get("dueDate"), null);
      boolean due =
          (dueDate != null && !dueDate.isAfter(asOf))
              || (dueDate == null && pendingDays >= minPendingDays);
      if (!due) {
        skipped++;
        continue;
      }

      String feeHead = strOr(answers.get("feeHead"), "FEE");
      String periodKey =
          strOr(answers.get("feePeriodKey"), feeHead + ":" + asOf);
      String studentKey = admissionNo.toUpperCase(Locale.ROOT);
      Map<String, Object> student = students.byAdmissionNo(scope, admissionNo);
      List<Map<String, Object>> guardians = guardiansOf(student);
      if (guardians.isEmpty()) {
        skipped++;
        details.add(Map.of("admissionNo", admissionNo, "skippedReason", "NO_GUARDIAN"));
        continue;
      }

      String subject = "Fee due reminder — " + admissionNo;
      String bodyText =
          "Pending fee for admission "
              + admissionNo
              + " ("
              + feeHead
              + ") is ₹"
              + amount
              + ". Please clear dues at the earliest.";

      List<Map<String, Object>> deliveries = new ArrayList<>();
      boolean anySent = false;
      for (Map<String, Object> guardian : guardians) {
        for (String channel : channels) {
          String recipient = recipientFor(guardian, channel);
          if (recipient == null) {
            continue;
          }
          FeeDueReminderOutboxEntity row =
              outbox
                  .findByOrganizationIdAndStudentKeyAndPeriodKeyAndChannelAndRecipient(
                      scope.organizationId(), studentKey, periodKey, channel, recipient)
                  .orElseGet(
                      () ->
                          newRow(
                              scope,
                              studentKey,
                              admissionNo,
                              collection.getId(),
                              periodKey,
                              amount,
                              channel,
                              recipient,
                              str(guardian.get("fullName")),
                              subject,
                              bodyText));
          if (!FeeDueReminderOutboxEntity.STATUS_SENT.equals(row.getStatus())) {
            row = dispatch(row);
          }
          if (FeeDueReminderOutboxEntity.STATUS_SENT.equals(row.getStatus())) {
            anySent = true;
            sent++;
          }
          deliveries.add(
              Map.of(
                  "channel", row.getChannel(),
                  "recipient", row.getRecipient(),
                  "status", row.getStatus(),
                  "attempts", row.getAttempts()));
        }
      }
      if (anySent) {
        reminded++;
      } else {
        skipped++;
      }
      details.add(
          Map.of(
              "admissionNo", admissionNo,
              "periodKey", periodKey,
              "amount", amount,
              "delivery", deliveries));
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("asOf", asOf.toString());
    out.put("reminded", reminded);
    out.put("skipped", skipped);
    out.put("sent", sent);
    out.put("details", details);
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> history(String admissionNo) {
    TenantScope scope = TenantContext.require();
    List<FeeDueReminderOutboxEntity> rows =
        admissionNo == null || admissionNo.isBlank()
            ? outbox.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId())
            : outbox.findByOrganizationIdAndAdmissionNoOrderByCreatedAtDesc(
                scope.organizationId(), admissionNo.trim());
    List<Map<String, Object>> out = new ArrayList<>();
    for (FeeDueReminderOutboxEntity row : rows.stream().limit(100).toList()) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("id", row.getId().toString());
      m.put("admissionNo", row.getAdmissionNo());
      m.put("periodKey", row.getPeriodKey());
      m.put("channel", row.getChannel());
      m.put("recipient", row.getRecipient());
      m.put("status", row.getStatus());
      m.put("attempts", row.getAttempts());
      m.put("notificationId", row.getNotificationId());
      m.put("sentAt", row.getSentAt() == null ? null : row.getSentAt().toString());
      out.add(m);
    }
    return out;
  }

  public int retryPending() {
    List<FeeDueReminderOutboxEntity> retryable =
        outbox.findRetryable(MAX_ATTEMPTS, Instant.now().minus(RETRY_WINDOW), PageRequest.of(0, 100));
    int sent = 0;
    for (FeeDueReminderOutboxEntity row : retryable) {
      try {
        if (FeeDueReminderOutboxEntity.STATUS_SENT.equals(dispatch(row).getStatus())) {
          sent++;
        }
      } catch (Exception ex) {
        log.warn("Fee due reminder retry failed for {}: {}", row.getId(), ex.getMessage());
      }
    }
    return sent;
  }

  private FeeDueReminderOutboxEntity newRow(
      TenantScope scope,
      String studentKey,
      String admissionNo,
      UUID collectionId,
      String periodKey,
      BigDecimal amount,
      String channel,
      String recipient,
      String guardianName,
      String subject,
      String body) {
    FeeDueReminderOutboxEntity row = new FeeDueReminderOutboxEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(scope.organizationId());
    row.setBranchId(scope.branchId());
    row.setAcademicSessionId(scope.academicSessionId());
    row.setStudentKey(studentKey);
    row.setAdmissionNo(admissionNo);
    row.setCollectionId(collectionId);
    row.setPeriodKey(periodKey);
    row.setAmount(amount);
    row.setChannel(channel);
    row.setRecipient(recipient);
    row.setGuardianName(guardianName);
    row.setSubject(truncate(subject, 255));
    row.setBody(body);
    row.setStatus(FeeDueReminderOutboxEntity.STATUS_PENDING);
    row.setCreatedAt(Instant.now());
    row.setUpdatedAt(Instant.now());
    return outbox.save(row);
  }

  private FeeDueReminderOutboxEntity dispatch(FeeDueReminderOutboxEntity entry) {
    entry.setAttempts(entry.getAttempts() + 1);
    entry.setUpdatedAt(Instant.now());
    Map<String, Object> response =
        delivery.queue(
            entry.getOrganizationId(),
            entry.getChannel(),
            entry.getRecipient(),
            entry.getSubject(),
            entry.getBody(),
            "fee-due-" + entry.getId());
    String status = String.valueOf(response.getOrDefault("status", "UNKNOWN"));
    if ("FAILED".equalsIgnoreCase(status) || "UNKNOWN".equalsIgnoreCase(status)) {
      entry.setStatus(FeeDueReminderOutboxEntity.STATUS_FAILED);
      entry.setLastError(truncate(str(response.get("error")), 512));
    } else {
      entry.setStatus(FeeDueReminderOutboxEntity.STATUS_SENT);
      entry.setSentAt(Instant.now());
      entry.setLastError(null);
    }
    if (response.get("id") != null) {
      entry.setNotificationId(String.valueOf(response.get("id")));
    }
    return outbox.save(entry);
  }

  private List<FeeCollectionEntity> loadOpen(TenantScope scope) {
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      return collections.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return collections.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> guardiansOf(Map<String, Object> student) {
    List<?> list = null;
    Object raw = student.get("guardians");
    if (raw instanceof List<?> top && !top.isEmpty()) {
      list = top;
    } else {
      Object answers = student.get("answers");
      if (answers instanceof Map<?, ?> a) {
        Object nested = a.get("guardians");
        if (nested instanceof List<?> nestedList && !nestedList.isEmpty()) {
          list = nestedList;
        }
      }
    }
    if (list == null || list.isEmpty()) {
      return List.of();
    }
    Set<String> seen = new LinkedHashSet<>();
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> g)) {
        continue;
      }
      Map<String, Object> guardian = (Map<String, Object>) g;
      String identity =
          firstNonBlank(
              str(guardian.get("authUsername")),
              str(guardian.get("username")),
              str(guardian.get("userId")));
      String email = str(guardian.get("email"));
      String mobile = str(guardian.get("mobile"));
      if (identity == null && email == null && mobile == null) {
        continue;
      }
      String key =
          identity != null
              ? "id:" + identity.toLowerCase(Locale.ROOT)
              : "c:" + (email == null ? "" : email) + "|" + (mobile == null ? "" : mobile);
      if (!seen.add(key)) {
        continue;
      }
      Map<String, Object> row = new LinkedHashMap<>(guardian);
      if (identity != null) {
        row.put("identity", identity);
      }
      out.add(row);
    }
    return out;
  }

  private static String recipientFor(Map<String, Object> guardian, String channel) {
    if ("IN_APP".equals(channel)) {
      return firstNonBlank(
          str(guardian.get("identity")),
          str(guardian.get("authUsername")),
          str(guardian.get("username")),
          str(guardian.get("userId")));
    }
    if ("EMAIL".equals(channel)) {
      return str(guardian.get("email"));
    }
    if ("SMS".equals(channel) || "WHATSAPP".equals(channel)) {
      return str(guardian.get("mobile"));
    }
    return null;
  }

  private static List<String> channels(Map<String, Object> settings) {
    Object configured = settings.get("dueReminderChannels");
    if (configured instanceof List<?> list && !list.isEmpty()) {
      return list.stream().map(String::valueOf).map(s -> s.toUpperCase(Locale.ROOT)).toList();
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

  private static LocalDate parseDate(Object raw, LocalDate fallback) {
    String s = str(raw);
    if (s == null) {
      return fallback;
    }
    try {
      return LocalDate.parse(s);
    } catch (Exception ex) {
      return fallback;
    }
  }

  private static BigDecimal toAmount(Object raw) {
    if (raw instanceof Number n) {
      return BigDecimal.valueOf(n.doubleValue());
    }
    String s = str(raw);
    if (s == null) {
      return BigDecimal.ZERO;
    }
    try {
      return new BigDecimal(s);
    } catch (Exception ex) {
      return BigDecimal.ZERO;
    }
  }

  private static int intOr(Object raw, int fallback) {
    if (raw instanceof Number n) {
      return n.intValue();
    }
    String s = str(raw);
    if (s == null) {
      return fallback;
    }
    try {
      return Integer.parseInt(s);
    } catch (Exception ex) {
      return fallback;
    }
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
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

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null ? fallback : s;
  }

  private static String truncate(String v, int max) {
    if (v == null) {
      return null;
    }
    return v.length() <= max ? v : v.substring(0, max);
  }
}
