package com.sugamflow.school.notification.service;

import com.sugamflow.school.notification.persistence.entity.NotificationTemplateEntity;
import com.sugamflow.school.notification.persistence.repo.NotificationTemplateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTemplateService {

  public static final String PLATFORM_ORG = "__platform__";
  public static final List<String> EVENTS =
      List.of(
          "ADMISSION",
          "ATTENDANCE",
          "FEES",
          "EXAM",
          "SALARY",
          "LEAVE",
          "TRANSPORT",
          "BIRTHDAY",
          "HOLIDAY",
          "EMERGENCY",
          "LIBRARY",
          "HOSTEL",
          "PAYROLL");
  public static final List<String> CHANNELS =
      List.of("SMS", "WHATSAPP", "EMAIL", "PUSH", "IN_APP", "VOICE", "TELEGRAM");
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)\\}\\}");

  private final NotificationTemplateRepository repo;
  private final java.util.concurrent.atomic.AtomicBoolean defaultsReady =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  public NotificationTemplateService(NotificationTemplateRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public List<Map<String, Object>> list(String org) {
    ensureDefaultsOnce();
    List<Map<String, Object>> out = new ArrayList<>();
    repo.findByOrganizationIdOrderByUpdatedAtDesc(PLATFORM_ORG)
        .forEach(e -> out.add(e.getPayload()));
    repo.findByOrganizationIdOrderByUpdatedAtDesc(org).forEach(e -> out.add(e.getPayload()));
    return out;
  }

  /** Effective event → channel routing for the school (org overrides win over platform defaults). */
  @Transactional
  public List<Map<String, Object>> listRouting(String org) {
    ensureDefaultsOnce();
    List<Map<String, Object>> orgTemplates = payloadsForOrg(org);
    List<Map<String, Object>> platformTemplates = payloadsForOrg(PLATFORM_ORG);
    List<Map<String, Object>> out = new ArrayList<>();
    for (String event : EVENTS) {
      out.add(routingForEvent(event, orgTemplates, platformTemplates));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> saveRouting(String org, String event, Map<String, Object> body) {
    String normalizedEvent = event == null ? "" : event.trim().toUpperCase(Locale.ROOT);
    if (!EVENTS.contains(normalizedEvent)) {
      throw new IllegalArgumentException("Unknown notification event: " + event);
    }
    ensureDefaultsOnce();

    List<String> channels = normalizeChannels(body.get("channels"));
    boolean enabled = !Boolean.FALSE.equals(body.get("enabled"));

    List<Map<String, Object>> platformTemplates =
        filterByEvent(payloadsForOrg(PLATFORM_ORG), normalizedEvent);
    if (platformTemplates.isEmpty()) {
      String id = normalizedEvent.toLowerCase(Locale.ROOT) + "_notify";
      Map<String, Object> tpl = new LinkedHashMap<>();
      tpl.put("id", id);
      tpl.put("name", normalizedEvent + " notifications");
      tpl.put("event", normalizedEvent);
      tpl.put("intent", normalizedEvent + "_NOTIFY");
      tpl.put("channels", channels);
      tpl.put("enabled", enabled);
      tpl.put("subject", normalizedEvent + " update");
      tpl.put("body", "Notification for {{context.organizationId}} — " + normalizedEvent);
      save(org, id, tpl);
    } else {
      for (Map<String, Object> platform : platformTemplates) {
        String id = String.valueOf(platform.getOrDefault("id", normalizedEvent.toLowerCase(Locale.ROOT)));
        Map<String, Object> copy = new LinkedHashMap<>(platform);
        copy.put("id", id);
        copy.put("event", normalizedEvent);
        copy.put("channels", channels);
        copy.put("enabled", enabled);
        copy.put("organizationId", org);
        save(org, id, copy);
      }
    }
    return routingForEvent(
        normalizedEvent, payloadsForOrg(org), payloadsForOrg(PLATFORM_ORG));
  }

  private Map<String, Object> routingForEvent(
      String event,
      List<Map<String, Object>> orgTemplates,
      List<Map<String, Object>> platformTemplates) {
    List<Map<String, Object>> orgForEvent = filterByEvent(orgTemplates, event);
    List<Map<String, Object>> platformForEvent = filterByEvent(platformTemplates, event);
    List<Map<String, Object>> source = !orgForEvent.isEmpty() ? orgForEvent : platformForEvent;

    Set<String> channels = new LinkedHashSet<>();
    boolean enabled = true;
    int templateCount = source.size();
    String sourceLabel =
        !orgForEvent.isEmpty() ? "SCHOOL" : (!platformForEvent.isEmpty() ? "PLATFORM" : "DEFAULT");

    if (source.isEmpty()) {
      channels.add("IN_APP");
      enabled = true;
    } else {
      enabled = source.stream().anyMatch(t -> !Boolean.FALSE.equals(t.get("enabled")));
      for (Map<String, Object> tpl : source) {
        Object raw = tpl.get("channels");
        if (raw instanceof List<?> list) {
          for (Object item : list) {
            if (item != null) {
              String channel = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
              if (CHANNELS.contains(channel)) {
                channels.add(channel);
              }
            }
          }
        }
      }
      if (channels.isEmpty()) {
        channels.add("IN_APP");
      }
    }

    Map<String, Object> row = new LinkedHashMap<>();
    row.put("event", event);
    row.put("enabled", enabled);
    row.put("channels", new ArrayList<>(channels));
    row.put("templateCount", templateCount);
    row.put("source", sourceLabel);
    return row;
  }

  private List<Map<String, Object>> payloadsForOrg(String org) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (NotificationTemplateEntity e : repo.findByOrganizationIdOrderByUpdatedAtDesc(org)) {
      Map<String, Object> payload = e.getPayload();
      if (payload != null) {
        out.add(payload);
      }
    }
    return out;
  }

  private static List<Map<String, Object>> filterByEvent(
      List<Map<String, Object>> templates, String event) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> payload : templates) {
      if (event.equalsIgnoreCase(String.valueOf(payload.get("event")))) {
        out.add(payload);
      }
    }
    return out;
  }

  private static List<String> normalizeChannels(Object raw) {
    Set<String> channels = new LinkedHashSet<>();
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item == null) {
          continue;
        }
        String channel = String.valueOf(item).trim().toUpperCase(Locale.ROOT);
        if (CHANNELS.contains(channel)) {
          channels.add(channel);
        }
      }
    }
    if (channels.isEmpty()) {
      channels.add("IN_APP");
    }
    // Portal alerts always keep an in-app path when any channel is enabled.
    if (!channels.contains("IN_APP")) {
      channels.add("IN_APP");
    }
    return new ArrayList<>(channels);
  }

  @Transactional
  public Map<String, Object> save(String org, String id, Map<String, Object> body) {
    body.put("id", id);
    body.put("organizationId", org);
    NotificationTemplateEntity e =
        repo.findByOrganizationIdAndTemplateId(org, id).orElseGet(NotificationTemplateEntity::new);
    e.setOrganizationId(org);
    e.setTemplateId(id);
    e.setPayload(body);
    e.setUpdatedAt(Instant.now());
    return repo.save(e).getPayload();
  }

  /**
   * Resolve template by event + intent (org override wins over platform). Substitutes {{paths}} from
   * variables map.
   */
  @Transactional
  public Map<String, Object> resolve(
      String org, String event, String intent, Map<String, Object> variables) {
    ensureDefaults();
    Map<String, Object> template = findByEventIntent(org, event, intent);
    if (template == null) {
      template = findByEventIntent(PLATFORM_ORG, event, intent);
    }
    if (template == null) {
      Map<String, Object> fallback = new LinkedHashMap<>();
      fallback.put("event", event);
      fallback.put("intent", intent);
      fallback.put("subject", intent);
      fallback.put("body", intent);
      fallback.put("channels", List.of("IN_APP"));
      fallback.put("resolved", false);
      return fallback;
    }
    Map<String, Object> vars = variables != null ? variables : Map.of();
    Map<String, Object> out = new LinkedHashMap<>(template);
    out.put("subject", substitute(String.valueOf(template.getOrDefault("subject", intent)), vars));
    out.put("body", substitute(String.valueOf(template.getOrDefault("body", "")), vars));
    out.put("resolved", true);
    return out;
  }

  @Transactional
  public Map<String, Object> preview(Map<String, Object> body, String org) {
    String event = String.valueOf(body.getOrDefault("event", "ADMISSION"));
    String intent = String.valueOf(body.getOrDefault("intent", body.getOrDefault("template", "")));
    @SuppressWarnings("unchecked")
    Map<String, Object> variables =
        body.get("variables") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : new LinkedHashMap<>();
    if (!(body.get("variables") instanceof Map) && body.get("template") != null) {
      // backward compat: treat template string as body fragment when no intent resolve
      variables.putIfAbsent("message", body.get("template"));
    }
    Map<String, Object> resolved = resolve(org, event, intent.isBlank() ? event : intent, variables);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("event", event);
    result.put("intent", intent);
    result.put("channels", body.getOrDefault("channels", resolved.getOrDefault("channels", List.of("IN_APP"))));
    result.put("subject", resolved.get("subject"));
    result.put("rendered", resolved.get("body"));
    result.put("resolved", resolved.get("resolved"));
    return result;
  }

  @Transactional
  public void ensureDefaults() {
    ensureDefaultsOnce();
  }

  private void ensureDefaultsOnce() {
    if (defaultsReady.get()) {
      return;
    }
    synchronized (defaultsReady) {
      if (defaultsReady.get()) {
        return;
      }
      seedPlatformDefaults();
      defaultsReady.set(true);
    }
  }

  private void seedPlatformDefaults() {
    ensurePlatformTemplate(
        "admission_approved",
        "Admission approved",
        "ADMISSION",
        "ADMISSION_APPROVED",
        "Admission offer — {{application.fullName}}",
        "Dear {{application.fullName}},\n\n"
            + "Your admission to class {{application.classApplied}} has been approved.\n"
            + "Download your offer letter: {{context.offerLetterUrl}}\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "fee_approved",
        "Fee approved",
        "FEES",
        "FEE_APPROVED",
        "Fee receipt — {{payment.studentName}}",
        "Dear {{payment.studentName}},\n\n"
            + "Your fee payment for {{payment.feeHead}} (amount {{payment.amount}}) has been approved.\n"
            + "Download your receipt: {{context.feeReceiptUrl}}\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "attendance_approved",
        "Attendance approved",
        "ATTENDANCE",
        "ATTENDANCE_APPROVED",
        "Attendance approved — {{attendance.studentName}}",
        "Dear {{attendance.studentName}},\n\n"
            + "Your attendance for {{attendance.classSection}} on {{attendance.attendanceDate}} "
            + "({{attendance.status}}, {{attendance.attendancePercent}}%) has been approved.\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "attendance_absent",
        "Attendance absent alert (parent)",
        "ATTENDANCE",
        "ATTENDANCE_ABSENT",
        "Absence alert — {{attendance.studentName}}",
        "Dear {{guardian.fullName}},\n\n"
            + "{{attendance.studentName}} ({{attendance.admissionNo}}) was marked ABSENT for "
            + "{{attendance.classSection}} on {{attendance.date}}.\n"
            + "Remark: {{attendance.remark}}\n\n"
            + "If this is unexpected, please contact the school office.\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "attendance_late",
        "Attendance late alert (parent)",
        "ATTENDANCE",
        "ATTENDANCE_LATE",
        "Late arrival — {{attendance.studentName}}",
        "Dear {{guardian.fullName}},\n\n"
            + "{{attendance.studentName}} ({{attendance.admissionNo}}) was marked LATE for "
            + "{{attendance.classSection}} on {{attendance.date}}.\n"
            + "Remark: {{attendance.remark}}\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "exam_approved",
        "Exam approved",
        "EXAM",
        "EXAM_APPROVED",
        "Exam marks approved — {{exam.studentName}}",
        "Dear {{exam.studentName}},\n\n"
            + "Your {{exam.examName}} marks for {{exam.subject}} ({{exam.marksObtained}}/{{exam.maxMarks}}) "
            + "in class {{exam.classSection}} have been approved.\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "library_approved",
        "Library issue approved",
        "LIBRARY",
        "LIBRARY_APPROVED",
        "Library issue approved — {{library.studentName}}",
        "Dear {{library.studentName}},\n\n"
            + "Your library issue for \"{{library.bookTitle}}\" (due {{library.dueDate}}) has been approved.\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "hostel_approved",
        "Hostel allocation approved",
        "HOSTEL",
        "HOSTEL_APPROVED",
        "Hostel allocation approved — {{hostel.studentName}}",
        "Dear {{hostel.studentName}},\n\n"
            + "Your hostel allocation in block {{hostel.hostelBlock}}, room {{hostel.roomNo}} "
            + "bed {{hostel.bedNo}} has been approved.\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "transport_approved",
        "Transport route approved",
        "TRANSPORT",
        "TRANSPORT_APPROVED",
        "Transport route approved — {{transport.studentName}}",
        "Dear {{transport.studentName}},\n\n"
            + "Your transport route {{transport.routeName}} (stop {{transport.stopName}}, "
            + "pickup {{transport.pickupTime}}) has been approved.\n\n"
            + "Organization: {{context.organizationId}}");
    ensurePlatformTemplate(
        "payroll_approved",
        "Payroll run approved",
        "PAYROLL",
        "PAYROLL_APPROVED",
        "Payroll approved — {{payroll.employeeName}}",
        "Dear {{payroll.employeeName}},\n\n"
            + "Your payroll run for {{payroll.month}} (net pay {{payroll.netPay}}) has been approved.\n\n"
            + "Organization: {{context.organizationId}}");
  }

  private void ensurePlatformTemplate(
      String id, String name, String event, String intent, String subject, String body) {
    if (repo.findByOrganizationIdAndTemplateId(PLATFORM_ORG, id).isPresent()) {
      return;
    }
    Map<String, Object> tpl = new LinkedHashMap<>();
    tpl.put("id", id);
    tpl.put("name", name);
    tpl.put("event", event);
    tpl.put("intent", intent);
    tpl.put("channels", List.of("EMAIL", "IN_APP"));
    tpl.put("enabled", true);
    tpl.put("subject", subject);
    tpl.put("body", body);
    save(PLATFORM_ORG, id, tpl);
  }

  private Map<String, Object> findByEventIntent(String org, String event, String intent) {
    for (NotificationTemplateEntity e : repo.findByOrganizationIdOrderByUpdatedAtDesc(org)) {
      Map<String, Object> p = e.getPayload();
      if (p == null) {
        continue;
      }
      if (event.equals(String.valueOf(p.get("event")))
          && intent.equals(String.valueOf(p.get("intent")))) {
        Object enabled = p.get("enabled");
        if (enabled != null && Boolean.FALSE.equals(enabled)) {
          continue;
        }
        return p;
      }
      if (intent.equals(String.valueOf(p.get("id"))) || intent.equals(e.getTemplateId())) {
        return p;
      }
    }
    return null;
  }

  private static String substitute(String template, Map<String, Object> data) {
    if (template == null) {
      return "";
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String path = matcher.group(1).trim();
      String value = bindValue(data, path);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static String bindValue(Map<String, Object> data, String path) {
    Object cur = data;
    for (String part : path.split("\\.")) {
      if (!(cur instanceof Map<?, ?> map)) {
        return "";
      }
      cur = map.get(part);
      if (cur == null) {
        return "";
      }
    }
    return String.valueOf(cur);
  }
}
