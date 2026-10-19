package com.sugamflow.school.student.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.CampusDeskItemEntity;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.repo.CampusDeskItemRepository;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.web.StudentException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CampusDeskService {

  private static final Set<String> KINDS =
      Set.of(
          "LEAVE",
          "LEAVE_TYPE",
          "GATE_PASS",
          "VISIT_PURPOSE",
          "EXIT_GATE",
          "INVENTORY",
          "PTM",
          "TASK",
          "TEACHER_DIARY",
          "DESK_SLIP",
          "ACTIVITY",
          "LECTURE");

  private static final Set<String> ELEVATED_ROLES =
      Set.of("SHOP_OWNER", "SUPER_ADMIN", "ADMIN", "PRINCIPAL");

  private static final DateTimeFormatter PASS_DAY = DateTimeFormatter.BASIC_ISO_DATE;

  private static final Set<String> CLOSED_STUDENT_STATUSES =
      Set.of("DELETED", "TRANSFERRED", "TC_ISSUED", "ALUMNI", "PASSED_OUT");

  private final CampusDeskItemRepository repository;
  private final StudentRecordRepository students;
  private final ConfigEngineClient engines;

  public CampusDeskService(
      CampusDeskItemRepository repository,
      StudentRecordRepository students,
      ConfigEngineClient engines) {
    this.repository = repository;
    this.students = students;
    this.engines = engines;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String kind) {
    return list(kind, Map.of());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> list(String kind, Map<String, String> query) {
    TenantScope scope = TenantContext.require();
    String normalized = normalizeKind(kind);
    List<Map<String, Object>> rows =
        repository
            .findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), normalized)
            .stream()
            .filter(item -> sameCampus(scope, item))
            .map(this::toDto)
            .toList();
    if (!"GATE_PASS".equals(normalized) || query == null || query.isEmpty()) {
      return rows;
    }
    return rows.stream().filter(row -> GatePassRules.matches(row, query)).toList();
  }

  @Transactional
  public Map<String, Object> create(String kind, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String normalized = normalizeKind(kind);
    CampusDeskItemEntity entity = new CampusDeskItemEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(blankToNull(scope.branchId()));
    entity.setAcademicSessionId(blankToNull(scope.academicSessionId()));
    entity.setKind(normalized);
    entity.setStatus(text(body.get("status"), defaultStatus(normalized)));
    entity.setSubjectType(text(body.get("subjectType"), "STUDENT"));
    entity.setSubjectRef(text(body.get("subjectRef"), null));
    entity.setSubjectName(text(body.get("subjectName"), null));
    entity.setTitle(text(body.get("title"), normalized.replace('_', ' ')));
    entity.setNote(text(body.get("note"), null));
    Map<String, Object> payload = payloadOf(body.get("payload"));
    if ("LEAVE".equals(normalized)) {
      stampLeaveStep(scope, payload);
    }
    if ("GATE_PASS".equals(normalized)) {
      CampusDeskItemEntity replay = prepareGatePass(scope, entity, body, payload);
      if (replay != null) {
        return toDto(replay);
      }
    }
    entity.setPayload(payload);
    entity.setCreatedBy(scope.userId());
    Instant now = Instant.now();
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return toDto(repository.save(entity));
  }

  @Transactional
  public Map<String, Object> updateStatus(UUID id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    CampusDeskItemEntity entity =
        repository
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> new StudentException("NOT_FOUND", "Desk item not found"));
    if ("GATE_PASS".equals(entity.getKind())) {
      if (!sameCampus(scope, entity)) {
        throw new StudentException("NOT_FOUND", "Desk item not found");
      }
      applyGateAction(scope, entity, body);
      entity.setUpdatedAt(Instant.now());
      return toDto(repository.save(entity));
    }
    String status = text(body.get("status"), null);
    if (status == null) {
      throw new StudentException("VALIDATION", "status is required");
    }
    entity.setStatus(status.toUpperCase(Locale.ROOT));
    String note = text(body.get("note"), null);
    if (note != null) {
      entity.setNote(note);
    }
    if ("LEAVE".equals(entity.getKind()) && "APPROVED".equals(entity.getStatus())) {
      advanceLeave(scope, entity);
    }
    entity.setUpdatedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  /** @return an existing pass when {@code clientRequestId} was already saved */
  private CampusDeskItemEntity prepareGatePass(
      TenantScope scope,
      CampusDeskItemEntity entity,
      Map<String, Object> body,
      Map<String, Object> payload) {
    List<CampusDeskItemEntity> existing = gatePasses(scope);
    String requestId = text(body.get("clientRequestId"), text(payload.get("clientRequestId"), null));
    if (requestId != null) {
      for (CampusDeskItemEntity item : existing) {
        if (requestId.equals(text(payloadValue(item, "clientRequestId"), null))) {
          return item;
        }
      }
      payload.put("clientRequestId", requestId);
    }
    String passType = gatePassType(body, payload);
    boolean approval = truthy(body.get("requestApproval")) || truthy(payload.get("requestApproval"));
    String status =
        GatePassRules.createStatus(passType, approval, GatePassRules.elevated(scope.roleCode()));
    entity.setSubjectType(passType);
    entity.setStatus(status);
    if ("STUDENT".equals(passType)) {
      bindStudent(scope, entity, body, payload, existing);
    } else {
      bindVisitor(scope, entity, payload, existing);
    }
    String passNumber = nextPassNumber(existing, Instant.now());
    payload.put("passType", passType);
    payload.put("passNumber", passNumber);
    payload.put("passDate", GatePassRules.today(Instant.now()).toString());
    payload.put("issuedAt", Instant.now().toString());
    rememberAudit(payload, scope.userId(), "CREATE", status);
    entity.setTitle(passNumber);
    if (entity.getNote() == null) {
      entity.setNote(text(payload.get("reason"), text(payload.get("purpose"), null)));
    }
    return null;
  }

  private void applyGateAction(TenantScope scope, CampusDeskItemEntity entity, Map<String, Object> body) {
    Map<String, Object> payload =
        entity.getPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getPayload());
    String action = text(body.get("action"), null);
    if (action == null) {
      throw new StudentException("VALIDATION", "action is required");
    }
    if ("EDIT".equalsIgnoreCase(action)) {
      editGatePass(entity, payload, payloadOf(body.get("payload")));
      entity.setPayload(payload);
      return;
    }
    String passType = GatePassRules.passType(payload, entity.getSubjectType());
    String next =
        GatePassRules.nextStatus(
            passType, entity.getStatus(), action, GatePassRules.elevated(scope.roleCode()));
    Instant now = Instant.now();
    String clock = now.atZone(GatePassRules.ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));
    if ("EXIT".equalsIgnoreCase(action)) {
      payload.put("actualExit", clock);
      payload.put("actualExitAt", now.toString());
    } else if ("RETURN".equalsIgnoreCase(action)) {
      if (text(payload.get("actualExitAt"), null) == null && text(payload.get("actualExit"), null) == null) {
        throw new StudentException("VALIDATION", "Record return only after the student has exited");
      }
      payload.put("actualReturn", clock);
      payload.put("actualReturnAt", now.toString());
    } else if ("CHECK_OUT".equalsIgnoreCase(action)) {
      if (text(payload.get("checkInAt"), null) == null && text(payload.get("checkIn"), null) == null) {
        throw new StudentException("VALIDATION", "Check-out is only for a visitor who is inside");
      }
      payload.put("checkOut", clock);
      payload.put("checkOutAt", now.toString());
    }
    entity.setStatus(next);
    entity.setSubjectType(passType);
    rememberAudit(payload, scope.userId(), action.toUpperCase(Locale.ROOT), next);
    entity.setPayload(payload);
  }

  private void editGatePass(
      CampusDeskItemEntity entity, Map<String, Object> payload, Map<String, Object> incoming) {
    String status = entity.getStatus() == null ? "" : entity.getStatus().toUpperCase(Locale.ROOT);
    if (Set.of("RETURNED", "COMPLETED", "CANCELLED", "REJECTED").contains(status)) {
      throw new StudentException("VALIDATION", "A closed gate pass cannot be edited");
    }
    for (String key :
        List.of(
            "purpose",
            "reason",
            "notes",
            "destination",
            "expectedReturn",
            "guardianName",
            "relationship",
            "contact",
            "pickupPerson",
            "organization",
            "personToMeet",
            "department",
            "vehicleNo",
            "expectedDeparture",
            "category")) {
      if (incoming.containsKey(key)) {
        payload.put(key, text(incoming.get(key), ""));
      }
    }
    if (incoming.containsKey("accompanying")) {
      payload.put("accompanying", accompanying(incoming.get("accompanying")));
    }
    if (incoming.containsKey("category")) {
      payload.put("category", visitorCategory(text(payload.get("category"), "GUEST")));
    }
    String purpose = text(payload.get("purpose"), null);
    if (purpose == null) {
      throw new StudentException("VALIDATION", "Purpose is required");
    }
    entity.setNote(text(payload.get("reason"), purpose));
  }

  private void bindStudent(
      TenantScope scope,
      CampusDeskItemEntity entity,
      Map<String, Object> body,
      Map<String, Object> payload,
      List<CampusDeskItemEntity> existing) {
    String ref = text(body.get("subjectRef"), text(payload.get("studentId"), null));
    UUID studentId;
    try {
      studentId = ref == null ? null : UUID.fromString(ref);
    } catch (IllegalArgumentException ex) {
      studentId = null;
    }
    if (studentId == null) {
      throw new StudentException("VALIDATION", "Select a student");
    }
    StudentRecordEntity student =
        students
            .findByIdAndOrganizationId(studentId, scope.organizationId())
            .orElseThrow(() -> new StudentException("VALIDATION", "Student not found in this school"));
    if (student.getDeletedAt() != null || CLOSED_STUDENT_STATUSES.contains(upper(student.getStatus()))) {
      throw new StudentException("VALIDATION", "This student is not enrolled");
    }
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && student.getBranchId() != null
        && !student.getBranchId().isBlank()
        && !scope.branchId().equals(student.getBranchId())) {
      throw new StudentException("VALIDATION", "Student is not on this campus");
    }
    if (scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()
        && student.getAcademicSessionId() != null
        && !student.getAcademicSessionId().isBlank()
        && !scope.academicSessionId().equals(student.getAcademicSessionId())) {
      throw new StudentException("VALIDATION", "Student is not in the current academic session");
    }
    for (CampusDeskItemEntity item : existing) {
      if (ref.equals(item.getSubjectRef()) && GatePassRules.openStudent(item.getStatus())) {
        throw new StudentException("VALIDATION", "This student already has an open gate pass");
      }
    }
    Map<String, Object> answers = student.getAnswers() == null ? Map.of() : student.getAnswers();
    String name =
        firstText(answers, "fullName", "studentName", "name");
    if (name.isBlank()) {
      name = "Student";
    }
    String classSection = firstText(answers, "classSection", "classApplied");
    String[] parts = splitClass(classSection);
    entity.setSubjectRef(student.getId().toString());
    entity.setSubjectName(name);
    payload.put("studentId", student.getId().toString());
    payload.put("admissionNo", text(student.getAdmissionNo(), ""));
    payload.put("studentName", name);
    payload.put("className", parts[0]);
    payload.put("section", parts[1]);
    payload.put("rollNo", firstText(answers, "rollNo", "rollNumber"));
    payload.put("photoUrl", photoUrl(answers));
    putIfBlank(payload, "guardianName", firstText(answers, "guardianName", "fatherName", "parentName"));
    putIfBlank(payload, "contact", firstText(answers, "mobile", "guardianMobile", "fatherMobile"));
    requireText(payload.get("purpose"), "Purpose is required");
    payload.put("reason", text(payload.get("reason"), ""));
    payload.put("destination", text(payload.get("destination"), ""));
    payload.put("expectedReturn", text(payload.get("expectedReturn"), ""));
    payload.put("notes", text(payload.get("notes"), text(payload.get("reason"), "")));
    payload.put("relationship", text(payload.get("relationship"), ""));
    payload.put("pickupPerson", text(payload.get("pickupPerson"), ""));
    copyFromBody(body, payload, "guardianName", "relationship", "contact", "pickupPerson", "reason", "destination", "expectedReturn", "notes");
  }

  private void bindVisitor(
      TenantScope scope,
      CampusDeskItemEntity entity,
      Map<String, Object> payload,
      List<CampusDeskItemEntity> existing) {
    String name = requireText(payload.get("visitorName"), "Visitor name is required");
    String mobile = digits(text(payload.get("mobile"), ""));
    if (mobile.length() < 10) {
      throw new StudentException("VALIDATION", "A valid mobile number is required");
    }
    String category = visitorCategory(text(payload.get("category"), "GUEST"));
    for (CampusDeskItemEntity item : existing) {
      if (!"INSIDE".equalsIgnoreCase(item.getStatus())) {
        continue;
      }
      if (mobile.equals(digits(text(payloadValue(item, "mobile"), "")))) {
        throw new StudentException("VALIDATION", "This visitor is already checked in");
      }
    }
    String meetId = text(payload.get("meetStudentId"), null);
    if (meetId != null) {
      try {
        StudentRecordEntity student =
            students
                .findByIdAndOrganizationId(UUID.fromString(meetId), scope.organizationId())
                .orElseThrow(() -> new StudentException("VALIDATION", "Student to meet was not found"));
        Map<String, Object> answers = student.getAnswers() == null ? Map.of() : student.getAnswers();
        payload.put("meetStudentName", firstText(answers, "fullName", "studentName"));
        String[] parts = splitClass(firstText(answers, "classSection", "classApplied"));
        payload.put("meetClassName", parts[0]);
        payload.put("meetSection", parts[1]);
      } catch (IllegalArgumentException ex) {
        throw new StudentException("VALIDATION", "Student to meet was not found");
      }
    }
    entity.setSubjectRef(null);
    entity.setSubjectName(name);
    payload.put("visitorName", name);
    payload.put("mobile", mobile);
    payload.put("category", category);
    payload.put("organization", text(payload.get("organization"), ""));
    payload.put("idType", text(payload.get("idType"), ""));
    payload.put("idRefLast4", last4(text(payload.get("idRef"), "")));
    payload.remove("idRef");
    requireText(payload.get("purpose"), "Purpose is required");
    payload.put("personToMeet", text(payload.get("personToMeet"), ""));
    payload.put("department", text(payload.get("department"), ""));
    payload.put("vehicleNo", text(payload.get("vehicleNo"), ""));
    payload.put("accompanying", accompanying(payload.get("accompanying")));
    payload.put("notes", text(payload.get("notes"), ""));
    payload.put("expectedDeparture", text(payload.get("expectedDeparture"), ""));
    Instant now = Instant.now();
    payload.put("checkIn", now.atZone(GatePassRules.ZONE).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")));
    payload.put("checkInAt", now.toString());
  }

  private List<CampusDeskItemEntity> gatePasses(TenantScope scope) {
    return repository.findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), "GATE_PASS")
        .stream()
        .filter(item -> sameCampus(scope, item))
        .toList();
  }

  private static String nextPassNumber(List<CampusDeskItemEntity> existing, Instant now) {
    String day = GatePassRules.today(now).format(PASS_DAY);
    java.util.HashSet<String> used = new java.util.HashSet<>();
    for (CampusDeskItemEntity item : existing) {
      used.add(text(item.getTitle(), ""));
      used.add(text(payloadValue(item, "passNumber"), ""));
    }
    for (int n = 1; n <= 9999; n++) {
      String candidate = "GP-" + day + "-" + String.format(Locale.ROOT, "%04d", n);
      if (!used.contains(candidate)) {
        return candidate;
      }
    }
    throw new StudentException("VALIDATION", "Could not allocate a pass number");
  }

  private static void rememberAudit(
      Map<String, Object> payload, String userId, String action, String status) {
    List<Map<String, Object>> audit = new ArrayList<>();
    Object raw = payload.get("audit");
    if (raw instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> map) {
          Map<String, Object> copy = new LinkedHashMap<>();
          map.forEach((key, value) -> copy.put(String.valueOf(key), value));
          audit.add(copy);
        }
      }
    }
    Map<String, Object> step = new LinkedHashMap<>();
    step.put("at", Instant.now().toString());
    step.put("by", userId);
    step.put("action", action);
    step.put("status", status);
    audit.add(step);
    payload.put("audit", audit);
  }

  private static String gatePassType(Map<String, Object> body, Map<String, Object> payload) {
    String raw = text(payload.get("passType"), text(body.get("subjectType"), "STUDENT"));
    return "VISITOR".equalsIgnoreCase(raw) ? "VISITOR" : "STUDENT";
  }

  private static void copyFromBody(
      Map<String, Object> body, Map<String, Object> payload, String... keys) {
    for (String key : keys) {
      if (body.containsKey(key) && text(body.get(key), null) != null) {
        payload.put(key, text(body.get(key), ""));
      }
    }
  }

  private static void putIfBlank(Map<String, Object> payload, String key, String value) {
    if (text(payload.get(key), null) == null && value != null && !value.isBlank()) {
      payload.put(key, value);
    }
  }

  private static String requireText(Object value, String message) {
    String text = text(value, null);
    if (text == null) {
      throw new StudentException("VALIDATION", message);
    }
    return text;
  }

  private static String visitorCategory(String raw) {
    String category = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    if (!GatePassRules.VISITOR_CATEGORIES.contains(category)) {
      throw new StudentException("VALIDATION", "Choose a visitor category");
    }
    return category;
  }

  private static int accompanying(Object raw) {
    int count = 0;
    if (raw instanceof Number number) {
      count = number.intValue();
    } else if (raw != null && !String.valueOf(raw).isBlank()) {
      try {
        count = Integer.parseInt(String.valueOf(raw).trim());
      } catch (NumberFormatException ex) {
        throw new StudentException("VALIDATION", "Accompanying visitors must be a number");
      }
    }
    if (count < 0 || count > 20) {
      throw new StudentException("VALIDATION", "Accompanying visitors must be between 0 and 20");
    }
    return count;
  }

  private static String last4(String value) {
    String compact = value == null ? "" : value.replaceAll("\\s+", "");
    if (compact.length() <= 4) {
      return compact;
    }
    return compact.substring(compact.length() - 4);
  }

  private static String digits(String value) {
    return value == null ? "" : value.replaceAll("\\D", "");
  }

  private static String photoUrl(Map<String, Object> answers) {
    for (String key : List.of("photoUrl", "photo")) {
      Object value = answers.get(key);
      if (value instanceof String text) {
        String url = text.trim();
        if (url.startsWith("http://")
            || url.startsWith("https://")
            || url.startsWith("/")
            || url.startsWith("data:image/")) {
          return url;
        }
      }
    }
    return "";
  }

  private static String firstText(Map<String, Object> answers, String... keys) {
    for (String key : keys) {
      String value = text(answers.get(key), null);
      if (value != null) {
        return value;
      }
    }
    return "";
  }

  private static String[] splitClass(String classSection) {
    if (classSection == null || classSection.isBlank()) {
      return new String[] {"", ""};
    }
    String value = classSection.trim();
    int split = value.lastIndexOf('-');
    if (split <= 0 || split == value.length() - 1) {
      return new String[] {value, ""};
    }
    return new String[] {value.substring(0, split).trim(), value.substring(split + 1).trim()};
  }

  private static boolean truthy(Object value) {
    if (value instanceof Boolean flag) {
      return flag;
    }
    return value != null && "true".equalsIgnoreCase(String.valueOf(value).trim());
  }

  private static String upper(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private static String payloadValue(CampusDeskItemEntity item, String key) {
    if (item.getPayload() == null) {
      return null;
    }
    return text(item.getPayload().get(key), null);
  }

  private static boolean sameCampus(TenantScope scope, CampusDeskItemEntity item) {
    if (scope.branchId() == null || scope.branchId().isBlank()) {
      return true;
    }
    return scope.branchId().equals(item.getBranchId()) || item.getBranchId() == null;
  }

  private static String normalizeKind(String kind) {
    String normalized = kind == null ? "" : kind.trim().toUpperCase(Locale.ROOT);
    if (!KINDS.contains(normalized)) {
      throw new StudentException("VALIDATION", "Unsupported desk kind");
    }
    return normalized;
  }

  private static String defaultStatus(String kind) {
    return switch (kind) {
      case "LEAVE", "PTM", "TASK" -> "PENDING";
      case "GATE_PASS", "DESK_SLIP" -> "OPEN";
      case "LECTURE" -> "PRESENT";
      default -> "ACTIVE";
    };
  }

  private static Map<String, Object> payloadOf(Object raw) {
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      map.forEach((key, value) -> out.put(String.valueOf(key), value));
      return out;
    }
    return new LinkedHashMap<>();
  }

  private static String text(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? fallback : text;
  }

  private void stampLeaveStep(TenantScope scope, Map<String, Object> payload) {
    Map<String, Object> workflow = engines.getWorkflow(scope, "leave");
    Map<String, Object> first = firstHumanStep(stepsOf(workflow));
    if (first == null) {
      return;
    }
    payload.put("workflowKey", "leave");
    payload.put("stepSequence", intOf(first.get("sequence")));
    payload.put("stepName", text(first.get("name"), "Review"));
    payload.put("assignRole", text(first.get("assignRole"), ""));
  }

  /** Walk the saved leave workflow. Balance is applied only on the last human step. */
  private void advanceLeave(TenantScope scope, CampusDeskItemEntity entity) {
    Map<String, Object> payload =
        entity.getPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getPayload());
    if (payload.get("stepSequence") == null) {
      applyLeaveBalance(scope, entity);
      entity.setStatus("APPROVED");
      return;
    }
    List<Map<String, Object>> steps = stepsOf(engines.getWorkflow(scope, "leave"));
    if (steps.isEmpty()) {
      applyLeaveBalance(scope, entity);
      entity.setStatus("APPROVED");
      return;
    }
    int current = intOf(payload.get("stepSequence"));
    Map<String, Object> step =
        steps.stream().filter(item -> intOf(item.get("sequence")) == current).findFirst().orElse(null);
    if (step == null) {
      step = firstHumanStep(steps);
    }
    if (step != null) {
      assertLeaveRole(scope, step);
    }
    Map<String, Object> next = nextHumanStep(steps, step);
    if (next == null) {
      applyLeaveBalance(scope, entity);
      entity.setStatus("APPROVED");
      payload.remove("assignRole");
      entity.setPayload(payload);
      return;
    }
    payload.put("stepSequence", intOf(next.get("sequence")));
    payload.put("stepName", text(next.get("name"), "Review"));
    payload.put("assignRole", text(next.get("assignRole"), ""));
    entity.setPayload(payload);
    entity.setStatus("PENDING");
  }

  private void assertLeaveRole(TenantScope scope, Map<String, Object> step) {
    String required = text(step.get("assignRole"), "");
    if (required.isBlank() || "SYSTEM".equalsIgnoreCase(required)) {
      return;
    }
    String role = scope.roleCode() == null ? "" : scope.roleCode().trim().toUpperCase(Locale.ROOT);
    if (ELEVATED_ROLES.contains(role) || required.equalsIgnoreCase(role)) {
      return;
    }
    throw new StudentException("FORBIDDEN", "This leave step is assigned to " + required);
  }

  private static Map<String, Object> firstHumanStep(List<Map<String, Object>> steps) {
    for (Map<String, Object> step : steps) {
      if (!"SYSTEM".equalsIgnoreCase(text(step.get("assignRole"), ""))) {
        return step;
      }
    }
    return null;
  }

  private static Map<String, Object> nextHumanStep(
      List<Map<String, Object>> steps, Map<String, Object> current) {
    boolean after = current == null;
    int currentSequence = current == null ? -1 : intOf(current.get("sequence"));
    for (Map<String, Object> step : steps) {
      if (!after) {
        if (intOf(step.get("sequence")) == currentSequence) {
          after = true;
        }
        continue;
      }
      if (!"SYSTEM".equalsIgnoreCase(text(step.get("assignRole"), ""))) {
        return step;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> stepsOf(Map<String, Object> workflow) {
    if (workflow == null || !(workflow.get("steps") instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> steps = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        Map<String, Object> step = new LinkedHashMap<>();
        map.forEach((key, value) -> step.put(String.valueOf(key), value));
        steps.add(step);
      }
    }
    steps.sort(Comparator.comparingInt(step -> intOf(step.get("sequence"))));
    return steps;
  }

  private static int intOf(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private void applyLeaveBalance(TenantScope scope, CampusDeskItemEntity entity) {
    int requested = requestedDays(entity);
    int remaining = remainingDays(scope, entity, true);
    if (requested > remaining) {
      throw new StudentException(
          "LEAVE_BALANCE",
          "Only " + remaining + " day(s) remain for this leave type");
    }
    Map<String, Object> payload =
        entity.getPayload() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entity.getPayload());
    payload.put("days", requested);
    payload.put("remainingDays", remaining - requested);
    entity.setPayload(payload);
  }

  private int remainingDays(TenantScope scope, CampusDeskItemEntity entity, boolean excludeSelf) {
    return Math.max(0, quotaFor(scope, entity) - usedDays(scope, entity, excludeSelf));
  }

  private int quotaFor(TenantScope scope, CampusDeskItemEntity leave) {
    String type = leaveType(leave);
    return repository.findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), "LEAVE_TYPE")
        .stream()
        .filter(item -> sameCampus(scope, item))
        .filter(item -> type.equalsIgnoreCase(item.getTitle()))
        .map(CampusDeskService::yearlyQuota)
        .findFirst()
        .orElse(12);
  }

  private int usedDays(TenantScope scope, CampusDeskItemEntity leave, boolean excludeSelf) {
    String type = leaveType(leave);
    String person = personKey(leave);
    int year = leave.getCreatedAt() == null
        ? Instant.now().atZone(ZoneId.of("Asia/Kolkata")).getYear()
        : leave.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).getYear();
    int used = 0;
    for (CampusDeskItemEntity item :
        repository.findByOrganizationIdAndKindOrderByCreatedAtDesc(scope.organizationId(), "LEAVE")) {
      if (!sameCampus(scope, item) || !"APPROVED".equalsIgnoreCase(item.getStatus())) {
        continue;
      }
      if (excludeSelf && item.getId().equals(leave.getId())) {
        continue;
      }
      if (!type.equalsIgnoreCase(leaveType(item)) || !person.equalsIgnoreCase(personKey(item))) {
        continue;
      }
      int itemYear = item.getCreatedAt().atZone(ZoneId.of("Asia/Kolkata")).getYear();
      if (itemYear == year) {
        used += requestedDays(item);
      }
    }
    return used;
  }

  private static int yearlyQuota(CampusDeskItemEntity type) {
    Object raw = type.getPayload() == null ? null : type.getPayload().get("yearlyQuota");
    if (raw instanceof Number number) {
      return Math.max(0, number.intValue());
    }
    if (raw != null) {
      try {
        return Math.max(0, Integer.parseInt(String.valueOf(raw).trim()));
      } catch (NumberFormatException ignored) {
        return 12;
      }
    }
    return 12;
  }

  private static int requestedDays(CampusDeskItemEntity leave) {
    Object raw = leave.getPayload() == null ? null : leave.getPayload().get("days");
    if (raw instanceof Number number && number.intValue() > 0) {
      return number.intValue();
    }
    if (raw != null) {
      try {
        int parsed = Integer.parseInt(String.valueOf(raw).trim());
        if (parsed > 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        return 1;
      }
    }
    return 1;
  }

  private static String leaveType(CampusDeskItemEntity leave) {
    Object key = leave.getPayload() == null ? null : leave.getPayload().get("catalogKey");
    String fromPayload = text(key, null);
    return fromPayload != null ? fromPayload : text(leave.getTitle(), "LEAVE");
  }

  private static String personKey(CampusDeskItemEntity leave) {
    String ref = text(leave.getSubjectRef(), null);
    return ref != null ? ref : text(leave.getSubjectName(), "");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private Map<String, Object> toDto(CampusDeskItemEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId().toString());
    row.put("kind", entity.getKind());
    row.put("status", entity.getStatus());
    row.put("subjectType", entity.getSubjectType());
    row.put("subjectRef", entity.getSubjectRef());
    row.put("subjectName", entity.getSubjectName());
    row.put("title", entity.getTitle());
    row.put("note", entity.getNote());
    row.put("payload", entity.getPayload());
    if ("LEAVE".equals(entity.getKind())) {
      row.put("requestedDays", requestedDays(entity));
      row.put("remainingDays", remainingDays(TenantContext.require(), entity, false));
      row.put("stepName", entity.getPayload() == null ? null : entity.getPayload().get("stepName"));
      row.put("assignRole", entity.getPayload() == null ? null : entity.getPayload().get("assignRole"));
    }
    if ("LEAVE_TYPE".equals(entity.getKind())) {
      row.put("yearlyQuota", yearlyQuota(entity));
    }
    row.put("createdBy", entity.getCreatedBy());
    row.put("createdAt", entity.getCreatedAt().toString());
    row.put("updatedAt", entity.getUpdatedAt().toString());
    return row;
  }
}
