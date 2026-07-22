package com.sugamflow.school.notification.service;

import com.sugamflow.school.notification.persistence.entity.NotificationTemplateEntity;
import com.sugamflow.school.notification.persistence.repo.NotificationTemplateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTemplateService {

  public static final String PLATFORM_ORG = "__platform__";
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)\\}\\}");

  private final NotificationTemplateRepository repo;

  public NotificationTemplateService(NotificationTemplateRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public List<Map<String, Object>> list(String org) {
    ensureDefaults();
    List<Map<String, Object>> out = new ArrayList<>();
    repo.findByOrganizationIdOrderByUpdatedAtDesc(PLATFORM_ORG)
        .forEach(e -> out.add(e.getPayload()));
    repo.findByOrganizationIdOrderByUpdatedAtDesc(org).forEach(e -> out.add(e.getPayload()));
    return out;
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
