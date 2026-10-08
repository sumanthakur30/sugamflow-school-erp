package com.sugamflow.school.attendance.service;

import com.sugamflow.school.attendance.biometric.BiometricDeviceRejected;
import com.sugamflow.school.attendance.biometric.BiometricRules;
import com.sugamflow.school.attendance.biometric.DeviceSecrets;
import com.sugamflow.school.attendance.biometric.ZkAttlogParser;
import com.sugamflow.school.attendance.integration.NotificationDeliveryClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceCorrectionEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricAuditEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricCommandEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricDayEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricEnrollmentEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricEventEntity;
import com.sugamflow.school.attendance.persistence.entity.BiometricRuleEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceCorrectionRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceDeviceRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceMarkRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceSessionRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricAuditRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricCommandRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricDayRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricEnrollmentRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricEventRepository;
import com.sugamflow.school.attendance.persistence.repo.BiometricRuleRepository;
import com.sugamflow.school.attendance.web.AttendanceException;
import com.sugamflow.school.common.security.PersonaRoles;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BiometricAttendanceService {

  private static final Logger log = LoggerFactory.getLogger(BiometricAttendanceService.class);
  private static final Set<String> MODES =
      Set.of("FIRST_LAST", "DEVICE_STATUS", "GATE", "TIME_SPLIT");
  private static final Set<String> STATUSES =
      Set.of(
          "PRESENT",
          "ABSENT",
          "LATE",
          "HALF_DAY",
          "EARLY_DEPARTURE",
          "LEAVE",
          "HOLIDAY",
          "WEEKEND",
          "EXCUSED",
          "UNKNOWN");
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("h:mm a");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");

  private final AttendanceDeviceRepository devices;
  private final BiometricRuleRepository rules;
  private final BiometricEnrollmentRepository enrollments;
  private final BiometricEventRepository events;
  private final BiometricDayRepository days;
  private final BiometricCommandRepository commands;
  private final BiometricAuditRepository audits;
  private final AttendanceCorrectionRepository corrections;
  private final AttendanceSessionRepository sessions;
  private final AttendanceMarkRepository marks;
  private final StaffAttendanceService staffAttendance;
  private final NotificationDeliveryClient notifications;

  public BiometricAttendanceService(
      AttendanceDeviceRepository devices,
      BiometricRuleRepository rules,
      BiometricEnrollmentRepository enrollments,
      BiometricEventRepository events,
      BiometricDayRepository days,
      BiometricCommandRepository commands,
      BiometricAuditRepository audits,
      AttendanceCorrectionRepository corrections,
      AttendanceSessionRepository sessions,
      AttendanceMarkRepository marks,
      StaffAttendanceService staffAttendance,
      NotificationDeliveryClient notifications) {
    this.devices = devices;
    this.rules = rules;
    this.enrollments = enrollments;
    this.events = events;
    this.days = days;
    this.commands = commands;
    this.audits = audits;
    this.corrections = corrections;
    this.sessions = sessions;
    this.marks = marks;
    this.staffAttendance = staffAttendance;
    this.notifications = notifications;
  }

  @Transactional
  public AttendanceDeviceEntity authenticate(String serial, String key) {
    String sn = serial == null ? "" : serial.trim();
    if (sn.isEmpty()) {
      throw new BiometricDeviceRejected("UNKNOWN_DEVICE", "Missing serial number");
    }
    AttendanceDeviceEntity device = devices.findBySerialNumber(sn).orElse(null);
    if (device == null) {
      log.warn("Rejected biometric request from unknown serial {}", sn);
      throw new BiometricDeviceRejected("UNKNOWN_DEVICE", "Unknown device");
    }
    if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
      device.setErrorCount(device.getErrorCount() + 1);
      devices.save(device);
      audit(device.getOrganizationId(), "device", "DEVICE_REJECTED", device.getId(), null, "disabled", null);
      throw new BiometricDeviceRejected("DISABLED", "Device is disabled");
    }
    if (!DeviceSecrets.matches(key, device.getCredentialHash())) {
      device.setErrorCount(device.getErrorCount() + 1);
      devices.save(device);
      audit(device.getOrganizationId(), "device", "DEVICE_REJECTED", device.getId(), null, "bad key", null);
      log.warn("Rejected biometric request for serial {} — credential mismatch", sn);
      throw new BiometricDeviceRejected("BAD_KEY", "Invalid device credential");
    }
    return device;
  }

  @Transactional
  public void heartbeat(AttendanceDeviceEntity device, Instant deviceTime) {
    Instant now = Instant.now();
    device.setLastHeartbeatAt(now);
    if (deviceTime != null) {
      device.setLastDeviceTime(deviceTime);
    }
    device.setUpdatedAt(now);
    devices.save(device);
  }

  @Transactional
  public int ingestAttlog(AttendanceDeviceEntity device, String body) {
    int accepted = 0;
    for (ZkAttlogParser.Punch punch : ZkAttlogParser.parse(body)) {
      Instant when = ZkAttlogParser.toInstant(punch.deviceTime(), device.getTimeZone());
      ingest(
          device,
          punch.pin(),
          when,
          null,
          ZkAttlogParser.verification(punch.verifyCode()),
          "BIOMETRIC",
          punch.statusCode());
      accepted++;
    }
    return accepted;
  }

  @Transactional
  public Map<String, Object> ingestNormalized(AttendanceDeviceEntity device, Map<String, Object> body) {
    String person = text(body.get("personCode"));
    Instant when = parseInstant(text(body.get("eventTime")));
    if (person == null || when == null) {
      throw new BiometricDeviceRejected("VALIDATION", "personCode and eventTime are required");
    }
    String eventType = text(body.get("eventType"));
    if (eventType != null) {
      eventType = eventType.toUpperCase(Locale.ROOT);
    }
    BiometricEventEntity saved =
        ingest(
            device,
            person,
            when,
            eventType,
            upper(text(body.get("verificationType")), "FINGERPRINT"),
            upper(text(body.get("source")), "BIOMETRIC"),
            0);
    return eventMap(saved);
  }

  @Transactional
  public String pollCommands(AttendanceDeviceEntity device) {
    heartbeat(device, null);
    List<BiometricCommandEntity> pending =
        commands.findByDeviceIdAndStatusOrderByRequestedAtAsc(device.getId(), "QUEUED");
    if (pending.isEmpty()) {
      return "OK";
    }
    StringBuilder out = new StringBuilder();
    for (BiometricCommandEntity command : pending) {
      String token = command.getId().toString().replace("-", "");
      out.append("C:").append(token).append(':').append(command.getCommandText()).append('\n');
      command.setStatus("SENT");
      commands.save(command);
    }
    return out.toString();
  }

  @Transactional
  public void completeCommands(AttendanceDeviceEntity device, String body) {
    String response = body == null ? "" : body.trim();
    for (BiometricCommandEntity command :
        commands.findByDeviceIdAndStatusOrderByRequestedAtAsc(device.getId(), "SENT")) {
      command.setStatus("COMPLETED");
      command.setResponse(response.length() > 1000 ? response.substring(0, 1000) : response);
      command.setCompletedAt(Instant.now());
      commands.save(command);
    }
  }

  private BiometricEventEntity ingest(
      AttendanceDeviceEntity device,
      String personCode,
      Instant eventTime,
      String forcedType,
      String verification,
      String source,
      int deviceStatusCode) {
    BiometricRuleEntity rule = ruleFor(device);
    ZoneId zone = zone(rule.getTimeZone());
    String mode = rule.getPunchMode();
    String eventType =
        forcedType != null
            ? forcedType
            : BiometricRules.eventType(
                mode, device.getDirection(), deviceStatusCode, eventTime, rule.getSplitTime(), zone);
    if (eventType == null) {
      BiometricDayEntity existing =
          days.findByOrganizationIdAndPersonCodeAndAttendanceDate(
                  device.getOrganizationId(), personCode, eventTime.atZone(zone).toLocalDate())
              .orElse(null);
      eventType =
          existing == null || existing.getFirstIn() == null || !eventTime.isAfter(existing.getFirstIn())
              ? "CHECK_IN"
              : "CHECK_OUT";
    }
    String hash =
        DeviceSecrets.eventHash(device.getId(), personCode, eventTime.toString(), eventType);
    BiometricEventEntity prior = events.findByEventHash(hash).orElse(null);
    if (prior != null) {
      prior.setDuplicateReplay(true);
      return prior;
    }
    BiometricEventEntity event = new BiometricEventEntity();
    event.setId(UUID.randomUUID());
    event.setOrganizationId(device.getOrganizationId());
    event.setBranchId(device.getBranchId());
    event.setDeviceId(device.getId());
    event.setSerialNumber(device.getSerialNumber());
    event.setPersonCode(personCode);
    event.setEventTime(eventTime);
    event.setEventType(eventType);
    event.setVerificationType(verification);
    event.setSource(source);
    event.setEventHash(hash);
    event.setReceivedAt(Instant.now());
    BiometricEnrollmentEntity enrollment =
        enrollments
            .findByOrganizationIdAndEnrollmentCode(device.getOrganizationId(), personCode)
            .filter(row -> "ACTIVE".equalsIgnoreCase(row.getStatus()))
            .orElse(null);
    if (enrollment == null) {
      event.setStatus("UNKNOWN_PERSON");
      event.setErrorMessage("No active enrollment for " + personCode);
      event.setProcessedAt(Instant.now());
      events.save(event);
      device.setLastEventAt(eventTime);
      device.setLastDeviceTime(eventTime);
      devices.save(device);
      return event;
    }
    try {
      apply(device, rule, enrollment, event, deviceStatusCode);
      event.setStatus("PROCESSED");
      event.setProcessedAt(Instant.now());
    } catch (RuntimeException ex) {
      event.setStatus("FAILED");
      event.setErrorMessage(trim(ex.getMessage()));
      device.setErrorCount(device.getErrorCount() + 1);
      log.warn("Biometric punch failed for {} on {}: {}", personCode, device.getSerialNumber(), ex.getMessage());
    }
    device.setLastEventAt(eventTime);
    device.setLastDeviceTime(eventTime);
    device.setUpdatedAt(Instant.now());
    devices.save(device);
    return events.save(event);
  }

  private void apply(
      AttendanceDeviceEntity device,
      BiometricRuleEntity rule,
      BiometricEnrollmentEntity enrollment,
      BiometricEventEntity event,
      int deviceStatusCode) {
    ZoneId zone = zone(rule.getTimeZone());
    LocalDate date = event.getEventTime().atZone(zone).toLocalDate();
    BiometricDayEntity day =
        days.findByOrganizationIdAndPersonCodeAndAttendanceDate(
                device.getOrganizationId(), enrollment.getEnrollmentCode(), date)
            .orElseGet(() -> newDay(device, enrollment, date));
    boolean corrected = day.isCorrected();
    BiometricRules.Decision decision =
        BiometricRules.decide(
            rule.getPunchMode(),
            device.getDirection(),
            deviceStatusCode,
            event.getEventTime(),
            day.getFirstIn(),
            day.getLastOut(),
            rule.getSchoolStart(),
            rule.getGraceMinutes(),
            rule.getSchoolEnd(),
            rule.getSplitTime(),
            rule.getHalfDayMinutes(),
            zone,
            event.getEventType());
    event.setEventType(decision.eventType());
    day.setFirstIn(decision.firstIn());
    day.setLastOut(decision.lastOut());
    if (!corrected) {
      day.setStatus(decision.status());
      day.setLateMinutes(decision.lateMinutes());
      day.setEarlyMinutes(decision.earlyMinutes());
      day.setWorkingMinutes(decision.workingMinutes());
    }
    day.setUpdatedAt(Instant.now());
    if ("STUDENT".equalsIgnoreCase(enrollment.getPersonType()) && enrollment.getSectionId() != null) {
      UUID markId = upsertRoster(device, enrollment, day, date);
      day.setAttendanceMarkId(markId);
    } else if ("STAFF".equalsIgnoreCase(enrollment.getPersonType())) {
      String staffId =
          enrollment.getPersonId() != null && !enrollment.getPersonId().isBlank()
              ? enrollment.getPersonId()
              : enrollment.getPersonCode();
      staffAttendance.mergeBiometric(
          device.getOrganizationId(),
          device.getBranchId(),
          date,
          staffId,
          enrollment.getDisplayName(),
          day.getStatus());
    }
    days.save(day);
    boolean firstCheckIn =
        "CHECK_IN".equals(decision.eventType())
            && decision.firstIn() != null
            && decision.firstIn().equals(event.getEventTime())
            && "STUDENT".equalsIgnoreCase(enrollment.getPersonType());
    if (firstCheckIn && rule.isNotifyOnCheckIn() && !event.isNotified()) {
      event.setNotified(notifyParent(device, enrollment, event, zone));
    }
  }

  private boolean notifyParent(
      AttendanceDeviceEntity device,
      BiometricEnrollmentEntity enrollment,
      BiometricEventEntity event,
      ZoneId zone) {
    ZonedDateTime local = event.getEventTime().atZone(zone);
    String gate = device.getGateName() == null || device.getGateName().isBlank() ? device.getLocation() : device.getGateName();
    String body =
        "Dear Parent,\n\nYour child "
            + enrollment.getDisplayName()
            + " entered the school at "
            + CLOCK.format(local)
            + ".\n\nDate: "
            + DAY.format(local)
            + "\nTime: "
            + CLOCK.format(local)
            + "\nGate: "
            + (gate == null ? "" : gate);
    BiometricRuleEntity rule = ruleFor(device);
    boolean any = false;
    for (String channel : rule.getNotifyChannels().split(",")) {
      String name = channel.trim().toUpperCase(Locale.ROOT);
      if (name.isEmpty()) {
        continue;
      }
      String recipient =
          switch (name) {
            case "SMS", "WHATSAPP" -> enrollment.getNotifyMobile();
            case "EMAIL" -> enrollment.getNotifyEmail();
            default -> enrollment.getPersonCode();
          };
      if (recipient == null || recipient.isBlank()) {
        continue;
      }
      Map<String, Object> result =
          notifications.queue(
              device.getOrganizationId(),
              name,
              recipient,
              "School arrival",
              body,
              event.getId().toString() + ":" + name);
      any = any || !"FAILED".equals(String.valueOf(result.get("status")));
    }
    return any;
  }

  private UUID upsertRoster(
      AttendanceDeviceEntity device,
      BiometricEnrollmentEntity enrollment,
      BiometricDayEntity day,
      LocalDate date) {
    AttendanceSessionEntity session =
        sessions
            .findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(
                device.getOrganizationId(), enrollment.getSectionId(), date)
            .orElse(null);
    if (session != null && "LOCKED".equals(session.getStatus())) {
      return day.getAttendanceMarkId();
    }
    if (session == null) {
      session = new AttendanceSessionEntity();
      session.setId(UUID.randomUUID());
      session.setOrganizationId(device.getOrganizationId());
      session.setBranchId(device.getBranchId());
      session.setSectionId(enrollment.getSectionId());
      session.setAttendanceDate(date);
      session.setStatus("DRAFT");
      session.setMarkedBy("biometric");
      session.setCreatedAt(Instant.now());
    }
    session.setUpdatedAt(Instant.now());
    session = sessions.save(session);
    UUID studentId = parseUuid(enrollment.getPersonId());
    AttendanceMarkEntity mark = null;
    if (studentId != null) {
      mark = marks.findBySessionIdAndStudentId(session.getId(), studentId).orElse(null);
    }
    if (mark == null && enrollment.getPersonCode() != null) {
      mark = marks.findBySessionIdAndAdmissionNo(session.getId(), enrollment.getPersonCode()).orElse(null);
    }
    if (mark == null) {
      mark = new AttendanceMarkEntity();
      mark.setId(UUID.randomUUID());
      mark.setOrganizationId(device.getOrganizationId());
      mark.setSessionId(session.getId());
      mark.setCreatedAt(Instant.now());
    }
    mark.setStudentId(studentId);
    mark.setAdmissionNo(enrollment.getPersonCode());
    mark.setStudentName(enrollment.getDisplayName());
    mark.setStatus(BiometricRules.rosterStatus(day.getStatus()));
    String remark = "Biometric " + device.getName();
    if ("EARLY_DEPARTURE".equals(day.getStatus())) {
      remark = remark + " · early departure " + day.getEarlyMinutes() + " min";
    } else if (day.getLateMinutes() > 0) {
      remark = remark + " · late " + day.getLateMinutes() + " min";
    }
    mark.setRemark(remark);
    mark.setMarkedBy("biometric");
    mark.setMarkedAt(day.getFirstIn() == null ? Instant.now() : day.getFirstIn());
    mark.setUpdatedAt(Instant.now());
    return marks.save(mark).getId();
  }

  @Transactional
  public Map<String, Object> retry(UUID eventId) {
    TenantScope scope = requireWrite();
    BiometricEventEntity event =
        events
            .findByIdAndOrganizationId(eventId, scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Event not found"));
    if ("PROCESSED".equals(event.getStatus()) && event.isNotified()) {
      return eventMap(event);
    }
    if ("PROCESSED".equals(event.getStatus()) && !event.isNotified()) {
      AttendanceDeviceEntity device = devices.findById(event.getDeviceId()).orElse(null);
      BiometricEnrollmentEntity enrollment =
          enrollments
              .findByOrganizationIdAndEnrollmentCode(scope.organizationId(), event.getPersonCode())
              .orElse(null);
      if (device != null && enrollment != null && "CHECK_IN".equals(event.getEventType())) {
        event.setNotified(notifyParent(device, enrollment, event, zone(device.getTimeZone())));
        events.save(event);
      }
      return eventMap(event);
    }
    AttendanceDeviceEntity device =
        devices
            .findByIdAndOrganizationId(event.getDeviceId(), scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Device not found"));
    events.delete(event);
    BiometricEventEntity again =
        ingest(
            device,
            event.getPersonCode(),
            event.getEventTime(),
            "DEVICE_STATUS".equals(ruleFor(device).getPunchMode()) ? event.getEventType() : null,
            event.getVerificationType(),
            event.getSource(),
            0);
    again.setRetryCount(event.getRetryCount() + 1);
    audit(scope.organizationId(), scope.userId(), "EVENT_REPROCESSED", again.getId().toString(), event.getStatus(), again.getStatus(), null);
    return eventMap(events.save(again));
  }

  @Transactional
  public Map<String, Object> registerDevice(Map<String, Object> body, String ip) {
    TenantScope scope = requireWrite();
    String serial = required(body, "serialNumber");
    if (devices.findBySerialNumber(serial).isPresent()) {
      throw new AttendanceException("VALIDATION", "Serial number is already registered");
    }
    String key = DeviceSecrets.newKey();
    AttendanceDeviceEntity device = new AttendanceDeviceEntity();
    device.setId("dev-" + UUID.randomUUID().toString().substring(0, 8));
    device.setOrganizationId(scope.organizationId());
    device.setBranchId(text(body.get("branchId")) == null ? scope.branchId() : text(body.get("branchId")));
    device.setDeviceKey(serial);
    device.setName(required(body, "name"));
    device.setAdapterType("BIOMETRIC");
    device.setStatus("ACTIVE");
    fillDevice(device, body);
    device.setSerialNumber(serial);
    device.setCredentialHash(DeviceSecrets.hash(key));
    device.setCredentialPrefix(DeviceSecrets.prefix(key));
    device.setCreatedAt(Instant.now());
    device.setUpdatedAt(Instant.now());
    devices.save(device);
    audit(scope.organizationId(), scope.userId(), "DEVICE_CREATED", device.getId(), null, device.getName(), ip);
    Map<String, Object> out = deviceMap(device, Instant.now());
    out.put("apiKey", key);
    out.put("uploadUrl", "/iclock/cdata?SN=" + serial + "&table=ATTLOG");
    out.put("pollUrl", "/iclock/getrequest?SN=" + serial);
    return out;
  }

  @Transactional
  public Map<String, Object> updateDevice(String id, Map<String, Object> body, String ip) {
    TenantScope scope = requireWrite();
    AttendanceDeviceEntity device = requireDevice(scope, id);
    String before = device.getStatus();
    fillDevice(device, body);
    if (text(body.get("status")) != null) {
      device.setStatus(text(body.get("status")).toUpperCase(Locale.ROOT));
    }
    device.setUpdatedAt(Instant.now());
    devices.save(device);
    audit(scope.organizationId(), scope.userId(), "DEVICE_UPDATED", device.getId(), before, device.getStatus(), ip);
    return deviceMap(device, Instant.now());
  }

  @Transactional
  public Map<String, Object> deactivate(String id, String ip) {
    TenantScope scope = requireWrite();
    AttendanceDeviceEntity device = requireDevice(scope, id);
    device.setStatus("DISABLED");
    device.setUpdatedAt(Instant.now());
    devices.save(device);
    audit(scope.organizationId(), scope.userId(), "DEVICE_DISABLED", device.getId(), "ACTIVE", "DISABLED", ip);
    return deviceMap(device, Instant.now());
  }

  @Transactional
  public Map<String, Object> regenerateKey(String id, String ip) {
    TenantScope scope = requireWrite();
    AttendanceDeviceEntity device = requireDevice(scope, id);
    String key = DeviceSecrets.newKey();
    device.setCredentialHash(DeviceSecrets.hash(key));
    device.setCredentialPrefix(DeviceSecrets.prefix(key));
    device.setUpdatedAt(Instant.now());
    devices.save(device);
    audit(scope.organizationId(), scope.userId(), "API_KEY_REGENERATED", device.getId(), device.getCredentialPrefix(), "rotated", ip);
    Map<String, Object> out = deviceMap(device, Instant.now());
    out.put("apiKey", key);
    return out;
  }

  @Transactional
  public Map<String, Object> revokeKey(String id, String ip) {
    TenantScope scope = requireWrite();
    AttendanceDeviceEntity device = requireDevice(scope, id);
    device.setCredentialHash(null);
    device.setCredentialPrefix(null);
    device.setStatus("DISABLED");
    device.setUpdatedAt(Instant.now());
    devices.save(device);
    audit(scope.organizationId(), scope.userId(), "API_KEY_REVOKED", device.getId(), null, "revoked", ip);
    return deviceMap(device, Instant.now());
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDevices(String branch, String type, String status, String location) {
    TenantScope scope = TenantContext.require();
    Instant now = Instant.now();
    List<Map<String, Object>> out = new ArrayList<>();
    for (AttendanceDeviceEntity device : devices.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId())) {
      if (device.getSerialNumber() == null || device.getSerialNumber().isBlank()) {
        continue;
      }
      if (branch != null && !branch.isBlank() && !branch.equals(device.getBranchId())) {
        continue;
      }
      if (type != null && !type.isBlank() && !type.equalsIgnoreCase(device.getDeviceType())) {
        continue;
      }
      if (location != null && !location.isBlank() && !location.equalsIgnoreCase(String.valueOf(device.getLocation()))) {
        continue;
      }
      Map<String, Object> row = deviceMap(device, now);
      if (status != null && !status.isBlank() && !status.equalsIgnoreCase(String.valueOf(row.get("health")))) {
        continue;
      }
      out.add(row);
    }
    return out;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> health() {
    List<Map<String, Object>> rows = listDevices(null, null, null, null);
    int online = 0;
    int offline = 0;
    int syncing = 0;
    int error = 0;
    for (Map<String, Object> row : rows) {
      String health = String.valueOf(row.get("health"));
      if ("ONLINE".equals(health)) online++;
      else if ("OFFLINE".equals(health)) offline++;
      else if ("SYNCING".equals(health)) syncing++;
      else if ("ERROR".equals(health)) error++;
      else if ("WARNING".equals(health)) online++;
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("total", rows.size());
    out.put("online", online);
    out.put("offline", offline);
    out.put("syncing", syncing);
    out.put("error", error);
    out.put("devices", rows);
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> live(int limit) {
    TenantScope scope = TenantContext.require();
    List<Map<String, Object>> out = new ArrayList<>();
    for (BiometricEventEntity event : events.findTop200ByOrganizationIdOrderByReceivedAtDesc(scope.organizationId())) {
      if (out.size() >= Math.max(1, Math.min(limit, 50))) {
        break;
      }
      if (!"PROCESSED".equals(event.getStatus()) && !"UNKNOWN_PERSON".equals(event.getStatus())) {
        continue;
      }
      out.add(liveRow(scope.organizationId(), event));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> saveEnrollment(Map<String, Object> body) {
    TenantScope scope = requireWrite();
    String code = required(body, "enrollmentCode");
    BiometricEnrollmentEntity entity =
        enrollments.findByOrganizationIdAndEnrollmentCode(scope.organizationId(), code).orElse(null);
    String before = entity == null ? null : entity.getPersonCode();
    if (entity == null) {
      entity = new BiometricEnrollmentEntity();
      entity.setId(UUID.randomUUID());
      entity.setOrganizationId(scope.organizationId());
      entity.setCreatedAt(Instant.now());
    }
    entity.setBranchId(text(body.get("branchId")) == null ? scope.branchId() : text(body.get("branchId")));
    entity.setPersonType(upper(required(body, "personType"), "STUDENT"));
    entity.setPersonId(text(body.get("personId")));
    entity.setPersonCode(text(body.get("personCode")));
    entity.setDisplayName(required(body, "displayName"));
    entity.setClassSection(text(body.get("classSection")));
    entity.setSectionId(parseUuid(text(body.get("sectionId"))));
    entity.setEnrollmentCode(code);
    entity.setVerificationType(upper(text(body.get("verificationType")), "FINGERPRINT"));
    entity.setStatus(upper(text(body.get("status")), "ACTIVE"));
    entity.setNotifyMobile(text(body.get("notifyMobile")));
    entity.setNotifyEmail(text(body.get("notifyEmail")));
    entity.setDeviceId(text(body.get("deviceId")));
    entity.setUpdatedAt(Instant.now());
    enrollments.save(entity);
    audit(scope.organizationId(), scope.userId(), "ENROLLMENT_CHANGED", entity.getId().toString(), before, code, null);
    return enrollmentMap(entity);
  }

  @Transactional
  public Map<String, Object> disableEnrollment(UUID id) {
    TenantScope scope = requireWrite();
    BiometricEnrollmentEntity entity =
        enrollments
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Enrollment not found"));
    entity.setStatus("DISABLED");
    entity.setUpdatedAt(Instant.now());
    audit(scope.organizationId(), scope.userId(), "ENROLLMENT_CHANGED", id.toString(), "ACTIVE", "DISABLED", null);
    return enrollmentMap(enrollments.save(entity));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listEnrollments(String query) {
    TenantScope scope = TenantContext.require();
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    List<Map<String, Object>> out = new ArrayList<>();
    for (BiometricEnrollmentEntity entity :
        enrollments.findByOrganizationIdOrderByDisplayNameAsc(scope.organizationId())) {
      String blob =
          (entity.getDisplayName() + " " + entity.getEnrollmentCode() + " " + entity.getPersonCode())
              .toLowerCase(Locale.ROOT);
      if (!needle.isEmpty() && !blob.contains(needle)) {
        continue;
      }
      out.add(enrollmentMap(entity));
    }
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listEvents(String status) {
    TenantScope scope = TenantContext.require();
    List<BiometricEventEntity> rows =
        status == null || status.isBlank()
            ? events.findTop200ByOrganizationIdOrderByReceivedAtDesc(scope.organizationId())
            : events.findTop100ByOrganizationIdAndStatusOrderByReceivedAtDesc(
                scope.organizationId(), status.toUpperCase(Locale.ROOT));
    List<Map<String, Object>> out = new ArrayList<>();
    for (BiometricEventEntity event : rows) {
      out.add(eventMap(event));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> ignoreEvent(UUID id) {
    TenantScope scope = requireWrite();
    BiometricEventEntity event =
        events
            .findByIdAndOrganizationId(id, scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Event not found"));
    String before = event.getStatus();
    event.setStatus("REJECTED");
    event.setErrorMessage("Ignored by " + scope.userId());
    event.setProcessedAt(Instant.now());
    audit(scope.organizationId(), scope.userId(), "EVENT_IGNORED", id.toString(), before, "REJECTED", null);
    return eventMap(events.save(event));
  }

  @Transactional
  public Map<String, Object> saveRule(Map<String, Object> body) {
    TenantScope scope = requireWrite();
    String branch = text(body.get("branchId"));
    if (branch == null) {
      branch = scope.branchId() == null ? "" : scope.branchId();
    }
    BiometricRuleEntity rule =
        rules.findByOrganizationIdAndBranchId(scope.organizationId(), branch).orElseGet(BiometricRuleEntity::new);
    if (rule.getId() == null) {
      rule.setId(UUID.randomUUID());
      rule.setOrganizationId(scope.organizationId());
      rule.setBranchId(branch);
    }
    String mode = upper(text(body.get("punchMode")), rule.getPunchMode());
    if (!MODES.contains(mode)) {
      throw new AttendanceException("VALIDATION", "punchMode must be FIRST_LAST, DEVICE_STATUS, GATE, or TIME_SPLIT");
    }
    rule.setPunchMode(mode);
    if (text(body.get("schoolStart")) != null) {
      rule.setSchoolStart(LocalTime.parse(text(body.get("schoolStart"))));
    }
    if (body.get("graceMinutes") != null) {
      rule.setGraceMinutes(Integer.parseInt(String.valueOf(body.get("graceMinutes"))));
    }
    if (text(body.get("schoolEnd")) != null) {
      rule.setSchoolEnd(LocalTime.parse(text(body.get("schoolEnd"))));
    }
    if (text(body.get("splitTime")) != null) {
      rule.setSplitTime(LocalTime.parse(text(body.get("splitTime"))));
    }
    if (body.get("halfDayMinutes") != null) {
      rule.setHalfDayMinutes(Integer.parseInt(String.valueOf(body.get("halfDayMinutes"))));
    }
    if (text(body.get("timeZone")) != null) {
      rule.setTimeZone(text(body.get("timeZone")));
    }
    if (body.get("driftThresholdSeconds") != null) {
      rule.setDriftThresholdSeconds(Integer.parseInt(String.valueOf(body.get("driftThresholdSeconds"))));
    }
    if (body.get("notifyOnCheckIn") != null) {
      rule.setNotifyOnCheckIn(Boolean.parseBoolean(String.valueOf(body.get("notifyOnCheckIn"))));
    }
    if (text(body.get("notifyChannels")) != null) {
      rule.setNotifyChannels(text(body.get("notifyChannels")));
    }
    rule.setUpdatedAt(Instant.now());
    audit(scope.organizationId(), scope.userId(), "RULE_UPDATED", rule.getId().toString(), null, mode, null);
    return ruleMap(rules.save(rule));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> getRule() {
    TenantScope scope = TenantContext.require();
    String branch = scope.branchId() == null ? "" : scope.branchId();
    return ruleMap(ruleForOrg(scope.organizationId(), branch));
  }

  @Transactional
  public Map<String, Object> queueCommand(String deviceId, Map<String, Object> body, String ip) {
    TenantScope scope = requireWrite();
    AttendanceDeviceEntity device = requireDevice(scope, deviceId);
    String action = upper(required(body, "action"), "");
    boolean dangerous = "REBOOT".equals(action) || "CLEAR_LOG".equals(action);
    if (dangerous && !Boolean.TRUE.equals(body.get("confirm"))) {
      throw new AttendanceException("VALIDATION", "Confirm this command before sending it");
    }
    String text =
        switch (action) {
          case "TEST" -> "INFO";
          case "SYNC_TIME" -> "SET TIME " + ZonedDateTime.now(zone(device.getTimeZone())).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
          case "PULL" -> "DATA QUERY ATTLOG";
          case "CLEAR_LOG" -> "CLEAR LOG";
          case "REBOOT" -> "REBOOT";
          case "SYNC_USERS" -> "DATA QUERY USERINFO";
          default -> throw new AttendanceException("VALIDATION", "Unknown command");
        };
    BiometricCommandEntity command = new BiometricCommandEntity();
    command.setId(UUID.randomUUID());
    command.setOrganizationId(scope.organizationId());
    command.setDeviceId(device.getId());
    command.setCommandText(text);
    command.setDangerous(dangerous);
    command.setRequestedBy(scope.userId());
    command.setRequestedAt(Instant.now());
    command.setStatus("QUEUED");
    commands.save(command);
    audit(scope.organizationId(), scope.userId(), "DEVICE_COMMAND", command.getId().toString(), null, action, ip);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", command.getId());
    out.put("deviceId", device.getId());
    out.put("action", action);
    out.put("status", command.getStatus());
    out.put("requestedAt", command.getRequestedAt());
    return out;
  }

  @Transactional
  public Map<String, Object> correct(UUID dayId, Map<String, Object> body, String ip) {
    TenantScope scope = requireWrite();
    BiometricDayEntity day =
        days.findByIdAndOrganizationId(dayId, scope.organizationId())
            .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Attendance day not found"));
    String next = upper(required(body, "status"), "");
    if (!STATUSES.contains(next)) {
      throw new AttendanceException("VALIDATION", "Unsupported attendance status");
    }
    String reason = required(body, "reason");
    AttendanceCorrectionEntity correction = new AttendanceCorrectionEntity();
    correction.setId(UUID.randomUUID());
    correction.setOrganizationId(scope.organizationId());
    correction.setBiometricDayId(day.getId());
    correction.setOriginalStatus(day.getStatus());
    correction.setCorrectedStatus(next);
    correction.setReason(reason);
    correction.setCorrectedBy(scope.userId());
    correction.setCorrectedAt(Instant.now());
    corrections.save(correction);
    String original = day.getStatus();
    day.setStatus(next);
    day.setCorrected(true);
    day.setUpdatedAt(Instant.now());
    days.save(day);
    if (day.getAttendanceMarkId() != null) {
      marks.findById(day.getAttendanceMarkId()).ifPresent(mark -> {
        mark.setStatus(BiometricRules.rosterStatus(next));
        mark.setRemark("Corrected: " + reason);
        mark.setUpdatedAt(Instant.now());
        marks.save(mark);
      });
    }
    audit(scope.organizationId(), scope.userId(), "ATTENDANCE_CORRECTED", day.getId().toString(), original, next, ip);
    Map<String, Object> out = dayMap(day);
    out.put("correctionId", correction.getId());
    return out;
  }

  @Transactional(readOnly = true)
  public String rangeCsv(LocalDate from, LocalDate to, String personType) {
    TenantScope scope = TenantContext.require();
    StringBuilder csv =
        new StringBuilder("person,code,type,class,date,in,out,status,lateMinutes,earlyMinutes,workingMinutes\n");
    String type = personType == null || personType.isBlank() ? "STUDENT" : personType.toUpperCase(Locale.ROOT);
    for (BiometricDayEntity day :
        days.findByOrganizationIdAndPersonTypeAndAttendanceDateBetween(
            scope.organizationId(), type, from, to)) {
      csv.append(csvCell(day.getDisplayName())).append(',')
          .append(csvCell(day.getPersonCode())).append(',')
          .append(day.getPersonType()).append(',')
          .append(csvCell(day.getClassSection())).append(',')
          .append(day.getAttendanceDate()).append(',')
          .append(day.getFirstIn()).append(',')
          .append(day.getLastOut()).append(',')
          .append(day.getStatus()).append(',')
          .append(day.getLateMinutes()).append(',')
          .append(day.getEarlyMinutes()).append(',')
          .append(day.getWorkingMinutes()).append('\n');
    }
    return csv.toString();
  }

  @Transactional(readOnly = true)
  public String dailyCsv(LocalDate date) {
    TenantScope scope = TenantContext.require();
    StringBuilder csv = new StringBuilder("person,type,class,in,out,status,lateMinutes,earlyMinutes\n");
    for (BiometricDayEntity day :
        days.findByOrganizationIdAndAttendanceDateOrderByDisplayNameAsc(scope.organizationId(), date)) {
      csv.append(csvCell(day.getDisplayName())).append(',')
          .append(day.getPersonType()).append(',')
          .append(csvCell(day.getClassSection())).append(',')
          .append(day.getFirstIn()).append(',')
          .append(day.getLastOut()).append(',')
          .append(day.getStatus()).append(',')
          .append(day.getLateMinutes()).append(',')
          .append(day.getEarlyMinutes()).append('\n');
    }
    return csv.toString();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> summary(LocalDate date) {
    TenantScope scope = TenantContext.require();
    int present = 0;
    int absent = 0;
    int late = 0;
    int leave = 0;
    for (BiometricDayEntity day :
        days.findByOrganizationIdAndAttendanceDateOrderByDisplayNameAsc(scope.organizationId(), date)) {
      switch (day.getStatus()) {
        case "LATE" -> late++;
        case "LEAVE" -> leave++;
        case "ABSENT" -> absent++;
        default -> present++;
      }
    }
    Instant start = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("date", date.toString());
    out.put("present", present);
    out.put("absent", absent);
    out.put("late", late);
    out.put("leave", leave);
    out.put("punches", events.countByOrganizationIdAndStatusAndReceivedAtAfter(scope.organizationId(), "PROCESSED", start));
    out.put("failed", events.countByOrganizationIdAndStatusAndReceivedAtAfter(scope.organizationId(), "FAILED", start));
    out.put("unknownPersons", events.countByOrganizationIdAndStatusAndReceivedAtAfter(scope.organizationId(), "UNKNOWN_PERSON", start));
    int offline = 0;
    for (Map<String, Object> device : listDevices(null, null, null, null)) {
      if ("OFFLINE".equals(device.get("health")) || "ERROR".equals(device.get("health"))) {
        offline++;
      }
    }
    out.put("offlineDevices", offline);
    return out;
  }

  private BiometricDayEntity newDay(AttendanceDeviceEntity device, BiometricEnrollmentEntity enrollment, LocalDate date) {
    BiometricDayEntity day = new BiometricDayEntity();
    day.setId(UUID.randomUUID());
    day.setOrganizationId(device.getOrganizationId());
    day.setBranchId(device.getBranchId());
    day.setPersonType(enrollment.getPersonType());
    day.setPersonCode(enrollment.getEnrollmentCode());
    day.setDisplayName(enrollment.getDisplayName());
    day.setClassSection(enrollment.getClassSection());
    day.setAttendanceDate(date);
    day.setStatus("PRESENT");
    return day;
  }

  private BiometricRuleEntity ruleFor(AttendanceDeviceEntity device) {
    String branch = device.getBranchId() == null ? "" : device.getBranchId();
    return ruleForOrg(device.getOrganizationId(), branch);
  }

  private BiometricRuleEntity ruleForOrg(String organizationId, String branch) {
    return rules
        .findByOrganizationIdAndBranchId(organizationId, branch == null ? "" : branch)
        .or(() -> rules.findByOrganizationIdAndBranchId(organizationId, ""))
        .orElseGet(
            () -> {
              BiometricRuleEntity created = new BiometricRuleEntity();
              created.setOrganizationId(organizationId);
              created.setBranchId(branch == null ? "" : branch);
              return created;
            });
  }

  private Map<String, Object> deviceMap(AttendanceDeviceEntity device, Instant now) {
    int timeout = device.getHeartbeatTimeoutSeconds() > 0 ? device.getHeartbeatTimeoutSeconds() : 120;
    boolean fresh =
        device.getLastHeartbeatAt() != null
            && Duration.between(device.getLastHeartbeatAt(), now).getSeconds() <= timeout;
    long pending = commands.findByDeviceIdAndStatusOrderByRequestedAtAsc(device.getId(), "QUEUED").size();
    long failed = events.countByDeviceIdAndStatus(device.getId(), "FAILED");
    BiometricRuleEntity rule = ruleFor(device);
    Long drift = null;
    if (device.getLastDeviceTime() != null && device.getLastHeartbeatAt() != null) {
      drift = Duration.between(device.getLastDeviceTime(), device.getLastHeartbeatAt()).abs().getSeconds();
    }
    String health;
    if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
      health = "OFFLINE";
    } else if (!fresh) {
      health = "OFFLINE";
    } else if (pending > 0) {
      health = "SYNCING";
    } else if (failed > 0) {
      health = "ERROR";
    } else if ((drift != null && drift > rule.getDriftThresholdSeconds()) || device.getErrorCount() > 0) {
      health = "WARNING";
    } else {
      health = "ONLINE";
    }
    Instant dayStart = LocalDate.now(zone(device.getTimeZone())).atStartOfDay(zone(device.getTimeZone())).toInstant();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", device.getId());
    row.put("name", device.getName());
    row.put("vendor", device.getVendor());
    row.put("deviceType", device.getDeviceType());
    row.put("serialNumber", device.getSerialNumber());
    row.put("branchId", device.getBranchId());
    row.put("location", device.getLocation());
    row.put("gate", device.getGateName());
    row.put("firmware", device.getFirmware());
    row.put("direction", device.getDirection());
    row.put("timeZone", device.getTimeZone());
    row.put("status", device.getStatus());
    row.put("health", health);
    row.put("credentialPrefix", device.getCredentialPrefix());
    row.put("lastHeartbeat", device.getLastHeartbeatAt());
    row.put("lastEvent", device.getLastEventAt());
    row.put("deviceTime", device.getLastDeviceTime());
    row.put("serverTime", now);
    row.put("driftSeconds", drift);
    row.put("errorCount", device.getErrorCount());
    row.put("punchesToday", events.countByDeviceIdAndReceivedAtAfter(device.getId(), dayStart));
    row.put("pendingCommands", pending);
    row.put("failedEvents", failed);
    return row;
  }

  private Map<String, Object> liveRow(String organizationId, BiometricEventEntity event) {
    BiometricEnrollmentEntity enrollment =
        enrollments
            .findByOrganizationIdAndEnrollmentCode(organizationId, event.getPersonCode())
            .orElse(null);
    AttendanceDeviceEntity device = event.getDeviceId() == null ? null : devices.findById(event.getDeviceId()).orElse(null);
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("eventTime", event.getEventTime());
    row.put("person", enrollment == null ? event.getPersonCode() : enrollment.getDisplayName());
    row.put("classSection", enrollment == null ? "" : enrollment.getClassSection());
    row.put("verification", event.getVerificationType());
    row.put("gate", device == null ? "" : device.getGateName());
    row.put("eventType", event.getEventType());
    row.put("status", event.getStatus());
    row.put("parentNotified", event.isNotified());
    return row;
  }

  private Map<String, Object> eventMap(BiometricEventEntity event) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", event.getId());
    row.put("deviceId", event.getDeviceId());
    row.put("serialNumber", event.getSerialNumber());
    row.put("personCode", event.getPersonCode());
    row.put("eventTime", event.getEventTime());
    row.put("eventType", event.getEventType());
    row.put("verificationType", event.getVerificationType());
    row.put("source", event.getSource());
    row.put("status", event.getStatus());
    row.put("error", event.getErrorMessage());
    row.put("receivedAt", event.getReceivedAt());
    row.put("processedAt", event.getProcessedAt());
    row.put("notified", event.isNotified());
    row.put("retryCount", event.getRetryCount());
    row.put("duplicate", event.isDuplicateReplay());
    if (event.isDuplicateReplay()) {
      row.put("status", "DUPLICATE");
    }
    return row;
  }

  private Map<String, Object> enrollmentMap(BiometricEnrollmentEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId());
    row.put("personType", entity.getPersonType());
    row.put("personId", entity.getPersonId());
    row.put("personCode", entity.getPersonCode());
    row.put("displayName", entity.getDisplayName());
    row.put("classSection", entity.getClassSection());
    row.put("sectionId", entity.getSectionId());
    row.put("enrollmentCode", entity.getEnrollmentCode());
    row.put("verificationType", entity.getVerificationType());
    row.put("status", entity.getStatus());
    row.put("notifyMobile", entity.getNotifyMobile());
    row.put("notifyEmail", entity.getNotifyEmail());
    row.put("deviceId", entity.getDeviceId());
    row.put("updatedAt", entity.getUpdatedAt());
    return row;
  }

  private Map<String, Object> ruleMap(BiometricRuleEntity rule) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", rule.getId());
    row.put("branchId", rule.getBranchId());
    row.put("punchMode", rule.getPunchMode());
    row.put("schoolStart", rule.getSchoolStart());
    row.put("graceMinutes", rule.getGraceMinutes());
    row.put("schoolEnd", rule.getSchoolEnd());
    row.put("splitTime", rule.getSplitTime());
    row.put("halfDayMinutes", rule.getHalfDayMinutes());
    row.put("timeZone", rule.getTimeZone());
    row.put("driftThresholdSeconds", rule.getDriftThresholdSeconds());
    row.put("notifyOnCheckIn", rule.isNotifyOnCheckIn());
    row.put("notifyChannels", rule.getNotifyChannels());
    return row;
  }

  private Map<String, Object> dayMap(BiometricDayEntity day) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", day.getId());
    row.put("person", day.getDisplayName());
    row.put("personCode", day.getPersonCode());
    row.put("date", day.getAttendanceDate());
    row.put("status", day.getStatus());
    row.put("firstIn", day.getFirstIn());
    row.put("lastOut", day.getLastOut());
    row.put("lateMinutes", day.getLateMinutes());
    row.put("earlyMinutes", day.getEarlyMinutes());
    row.put("workingMinutes", day.getWorkingMinutes());
    row.put("corrected", day.isCorrected());
    return row;
  }

  private void fillDevice(AttendanceDeviceEntity device, Map<String, Object> body) {
    if (text(body.get("name")) != null) {
      device.setName(text(body.get("name")));
    }
    if (text(body.get("vendor")) != null) {
      device.setVendor(text(body.get("vendor")));
    }
    if (text(body.get("deviceType")) != null) {
      device.setDeviceType(text(body.get("deviceType")).toUpperCase(Locale.ROOT));
    }
    if (text(body.get("location")) != null) {
      device.setLocation(text(body.get("location")));
    }
    if (text(body.get("gate")) != null) {
      device.setGateName(text(body.get("gate")));
    }
    if (text(body.get("firmware")) != null) {
      device.setFirmware(text(body.get("firmware")));
    }
    if (text(body.get("direction")) != null) {
      device.setDirection(text(body.get("direction")).toUpperCase(Locale.ROOT));
    }
    if (text(body.get("timeZone")) != null) {
      device.setTimeZone(text(body.get("timeZone")));
    }
  }

  private AttendanceDeviceEntity requireDevice(TenantScope scope, String id) {
    return devices
        .findByIdAndOrganizationId(id, scope.organizationId())
        .orElseThrow(() -> new AttendanceException("NOT_FOUND", "Device not found"));
  }

  private TenantScope requireWrite() {
    TenantScope scope = TenantContext.require();
    PersonaRoles.requireStaffWrite(scope);
    return scope;
  }

  private void audit(String org, String user, String action, String entityId, String oldValue, String newValue, String ip) {
    BiometricAuditEntity row = new BiometricAuditEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(org);
    row.setUserId(user);
    row.setAction(action);
    row.setEntityName(action.startsWith("DEVICE") ? "device" : "biometric");
    row.setEntityId(entityId);
    row.setOldValue(oldValue);
    row.setNewValue(newValue);
    row.setIpAddress(ip);
    row.setCreatedAt(Instant.now());
    audits.save(row);
  }

  private static ZoneId zone(String id) {
    try {
      return ZoneId.of(id == null || id.isBlank() ? "Asia/Kolkata" : id);
    } catch (Exception ex) {
      return ZoneId.of("Asia/Kolkata");
    }
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String raw = String.valueOf(value).trim();
    return raw.isEmpty() || "null".equals(raw) ? null : raw;
  }

  private static String required(Map<String, Object> body, String key) {
    String value = text(body.get(key));
    if (value == null) {
      throw new AttendanceException("VALIDATION", key + " is required");
    }
    return value;
  }

  private static String upper(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
  }

  private static Instant parseInstant(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw);
    } catch (Exception ex) {
      try {
        return ZonedDateTime.parse(raw).toInstant();
      } catch (Exception ignored) {
        return null;
      }
    }
  }

  private static UUID parseUuid(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw.trim());
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private static String trim(String message) {
    if (message == null) {
      return "Processing failed";
    }
    return message.length() > 500 ? message.substring(0, 500) : message;
  }

  private static String csvCell(String value) {
    if (value == null) {
      return "";
    }
    if (value.contains(",") || value.contains("\"")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }
}
