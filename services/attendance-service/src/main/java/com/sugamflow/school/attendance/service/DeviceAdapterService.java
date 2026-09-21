package com.sugamflow.school.attendance.service;

import com.sugamflow.school.attendance.device.DeviceAdapterCatalog;
import com.sugamflow.school.attendance.integration.ConfigEngineClient;
import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEntity;
import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEventEntity;
import com.sugamflow.school.attendance.persistence.repo.AttendanceDeviceEventRepository;
import com.sugamflow.school.attendance.persistence.repo.AttendanceDeviceRepository;
import com.sugamflow.school.attendance.web.AttendanceException;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceAdapterService {

  public static final String FEATURE_ATTENDANCE = AttendanceRecordService.FEATURE_ATTENDANCE;

  private final AttendanceDeviceRepository deviceRepo;
  private final AttendanceDeviceEventRepository eventRepo;
  private final ConfigEngineClient engines;
  private final AttendanceRecordService attendanceRecords;

  public DeviceAdapterService(
      AttendanceDeviceRepository deviceRepo,
      AttendanceDeviceEventRepository eventRepo,
      ConfigEngineClient engines,
      AttendanceRecordService attendanceRecords) {
    this.deviceRepo = deviceRepo;
    this.eventRepo = eventRepo;
    this.engines = engines;
    this.attendanceRecords = attendanceRecords;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireAttendance(scope);
    Map<String, Object> module = moduleSettings(scope);
    Map<String, Object> settings = settingsMap(module);

    List<Map<String, Object>> types = new ArrayList<>();
    for (Map<String, Object> type : DeviceAdapterCatalog.adapterTypes()) {
      Map<String, Object> row = new LinkedHashMap<>(type);
      String flag = String.valueOf(type.get("requiredFeatureFlag"));
      row.put("featureEnabled", engines.isFeatureEnabled(scope, flag));
      types.add(row);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("aiAttendanceEnabled", boolOr(settings.get("aiAttendanceEnabled"), false));
    out.put("autoSubmitFromDevice", boolOr(settings.get("autoSubmitFromDevice"), true));
    out.put("minConfidence", doubleOr(settings.get("minConfidence"), 0.8));
    out.put("acceptedAdapterTypes", settings.getOrDefault("acceptedAdapterTypes", defaultAccepted()));
    out.put("adapterTypes", types);
    out.put("devices", listDevices());
    out.put(
        "flags",
        Map.of(
            "FEATURE_AI", engines.isFeatureEnabled(scope, "FEATURE_AI"),
            "FEATURE_BIOMETRIC", engines.isFeatureEnabled(scope, "FEATURE_BIOMETRIC"),
            "FEATURE_FACE_RECOGNITION", engines.isFeatureEnabled(scope, "FEATURE_FACE_RECOGNITION"),
            "FEATURE_GPS", engines.isFeatureEnabled(scope, "FEATURE_GPS")));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDevices() {
    TenantScope scope = TenantContext.require();
    requireAttendance(scope);
    return deviceRepo.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId()).stream()
        .map(this::toDeviceMap)
        .toList();
  }

  @Transactional
  public Map<String, Object> registerDevice(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireAttendance(scope);
    String adapterType =
        String.valueOf(body.getOrDefault("adapterType", "")).trim().toUpperCase(Locale.ROOT);
    if (!DeviceAdapterCatalog.isKnown(adapterType)) {
      throw new AttendanceException("UNKNOWN_ADAPTER", "Unknown adapterType: " + adapterType);
    }
    String flag = DeviceAdapterCatalog.requiredFlag(adapterType);
    if (!engines.isFeatureEnabled(scope, flag)) {
      throw new AttendanceException(
          "FEATURE_DISABLED", flag + " is off for this subscription plan.");
    }
    String deviceKey = normalizeKey(String.valueOf(body.getOrDefault("deviceKey", "")));
    if (deviceKey.isBlank()) {
      throw new AttendanceException("INVALID_DEVICE", "deviceKey is required");
    }
    if (deviceRepo.findByOrganizationIdAndDeviceKey(scope.organizationId(), deviceKey).isPresent()) {
      throw new AttendanceException("DEVICE_EXISTS", "Device already registered: " + deviceKey);
    }
    AttendanceDeviceEntity e = new AttendanceDeviceEntity();
    e.setId(UUID.randomUUID().toString());
    e.setOrganizationId(scope.organizationId());
    e.setBranchId(scope.branchId());
    e.setDeviceKey(deviceKey);
    e.setName(String.valueOf(body.getOrDefault("name", deviceKey)));
    e.setAdapterType(adapterType);
    e.setStatus(String.valueOf(body.getOrDefault("status", "ACTIVE")).toUpperCase(Locale.ROOT));
    if (body.get("config") instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cfg = (Map<String, Object>) m;
      e.setConfigJson(new LinkedHashMap<>(cfg));
    } else {
      e.setConfigJson(new LinkedHashMap<>());
    }
    e.setCreatedAt(Instant.now());
    e.setUpdatedAt(Instant.now());
    return toDeviceMap(deviceRepo.save(e));
  }

  @Transactional
  public Map<String, Object> updateDevice(String id, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireAttendance(scope);
    AttendanceDeviceEntity e = requireDevice(id, scope.organizationId());
    if (body.get("name") != null) {
      e.setName(String.valueOf(body.get("name")));
    }
    if (body.get("status") != null) {
      e.setStatus(String.valueOf(body.get("status")).toUpperCase(Locale.ROOT));
    }
    if (body.get("config") instanceof Map<?, ?> m) {
      @SuppressWarnings("unchecked")
      Map<String, Object> cfg = (Map<String, Object>) m;
      e.setConfigJson(new LinkedHashMap<>(cfg));
    }
    e.setUpdatedAt(Instant.now());
    return toDeviceMap(deviceRepo.save(e));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listEvents(String deviceId) {
    TenantScope scope = TenantContext.require();
    requireAttendance(scope);
    List<AttendanceDeviceEventEntity> events;
    if (deviceId != null && !deviceId.isBlank()) {
      events =
          eventRepo.findByOrganizationIdAndDeviceIdOrderByCreatedAtDesc(
              scope.organizationId(), deviceId);
    } else {
      events = eventRepo.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId());
    }
    return events.stream().limit(100).map(this::toEventMap).toList();
  }

  @Transactional
  public Map<String, Object> ingest(String deviceId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireAttendance(scope);
    AttendanceDeviceEntity device = requireDevice(deviceId, scope.organizationId());
    if (!"ACTIVE".equalsIgnoreCase(device.getStatus())) {
      throw new AttendanceException("DEVICE_INACTIVE", "Device is not ACTIVE");
    }
    String flag = DeviceAdapterCatalog.requiredFlag(device.getAdapterType());
    if (!engines.isFeatureEnabled(scope, flag)) {
      throw new AttendanceException(
          "FEATURE_DISABLED", flag + " is off for this subscription plan.");
    }

    Map<String, Object> module = moduleSettings(scope);
    Map<String, Object> settings = settingsMap(module);
    boolean aiEnabled = boolOr(settings.get("aiAttendanceEnabled"), false);
    boolean autoSubmit = boolOr(settings.get("autoSubmitFromDevice"), true);
    double minConfidence = doubleOr(settings.get("minConfidence"), 0.8);
    List<String> accepted = asStringList(settings.get("acceptedAdapterTypes"));
    if (!accepted.isEmpty() && !accepted.contains(device.getAdapterType())) {
      throw new AttendanceException(
          "ADAPTER_NOT_ACCEPTED",
          "Adapter " + device.getAdapterType() + " is not in attendance module acceptedAdapterTypes");
    }

    Map<String, Object> payload = body != null ? new LinkedHashMap<>(body) : new LinkedHashMap<>();
    double confidence = doubleOr(payload.get("confidence"), 1.0);
    payload.putIfAbsent("capturedAt", Instant.now().toString());
    payload.put("deviceKey", device.getDeviceKey());
    payload.put("adapterType", device.getAdapterType());

    AttendanceDeviceEventEntity event = new AttendanceDeviceEventEntity();
    event.setId(UUID.randomUUID().toString());
    event.setOrganizationId(scope.organizationId());
    event.setBranchId(scope.branchId());
    event.setDeviceId(device.getId());
    event.setAdapterType(device.getAdapterType());
    event.setPayloadJson(payload);
    event.setCreatedAt(Instant.now());

    if (!aiEnabled) {
      event.setStatus("IGNORED_AI_OFF");
      return toEventMap(eventRepo.save(event));
    }
    if (confidence < minConfidence) {
      event.setStatus("REJECTED_LOW_CONFIDENCE");
      event.getPayloadJson().put("minConfidence", minConfidence);
      return toEventMap(eventRepo.save(event));
    }

    if (autoSubmit) {
      Map<String, Object> answers = toAttendanceAnswers(payload, device);
      try {
        Map<String, Object> record =
            attendanceRecords.submit(Map.of("answers", answers, "source", "DEVICE_ADAPTER"));
        event.setStatus("SUBMITTED");
        event.setAttendanceRecordId(String.valueOf(record.get("id")));
        event.getPayloadJson().put("attendanceRecord", Map.of("id", record.get("id"), "status", record.get("status")));
      } catch (AttendanceException ex) {
        event.setStatus("SUBMIT_FAILED");
        event.getPayloadJson().put("error", Map.of("code", ex.getCode(), "message", ex.getMessage()));
      }
    } else {
      event.setStatus("ACCEPTED");
    }
    return toEventMap(eventRepo.save(event));
  }

  @Transactional
  public Map<String, Object> simulate(String deviceId) {
    TenantScope scope = TenantContext.require();
    AttendanceDeviceEntity device = requireDevice(deviceId, scope.organizationId());
    Map<String, Object> sample = new LinkedHashMap<>();
    sample.put("externalUserId", "ADM-1001");
    sample.put("studentName", "Priya Nair");
    sample.put("admissionNo", "ADM-1001");
    sample.put("classSection", "Grade 8-A");
    sample.put("status", "PRESENT");
    sample.put("attendancePercent", 96);
    sample.put("eventType", "CHECK_IN");
    sample.put("confidence", 0.94);
    sample.put("email", "priya@example.com");
    sample.put("mobile", "9999900001");
    sample.put("simulated", true);
    if (DeviceAdapterCatalog.TYPE_GPS.equals(device.getAdapterType())) {
      sample.put("geo", Map.of("lat", 12.9716, "lng", 77.5946, "accuracyM", 12));
    }
    return ingest(deviceId, sample);
  }

  private Map<String, Object> toAttendanceAnswers(
      Map<String, Object> payload, AttendanceDeviceEntity device) {
    Map<String, Object> answers = new LinkedHashMap<>();
    answers.put(
        "studentName",
        firstNonBlank(payload.get("studentName"), payload.get("name"), "Device Student"));
    answers.put(
        "admissionNo",
        firstNonBlank(payload.get("admissionNo"), payload.get("externalUserId"), "ADM-DEV"));
    answers.put(
        "classSection", firstNonBlank(payload.get("classSection"), "Grade 8-A"));
    answers.put(
        "attendanceDate",
        firstNonBlank(
            payload.get("attendanceDate"), LocalDate.now(ZoneOffset.UTC).toString()));
    answers.put("status", firstNonBlank(payload.get("status"), "PRESENT"));
    answers.put(
        "attendancePercent",
        payload.get("attendancePercent") != null ? payload.get("attendancePercent") : 95);
    if (payload.get("email") != null) {
      answers.put("email", payload.get("email"));
    }
    if (payload.get("mobile") != null) {
      answers.put("mobile", payload.get("mobile"));
    }
    answers.put("deviceKey", device.getDeviceKey());
    answers.put("adapterType", device.getAdapterType());
    answers.put("source", "DEVICE_ADAPTER");
    return answers;
  }

  private AttendanceDeviceEntity requireDevice(String id, String org) {
    return deviceRepo
        .findByIdAndOrganizationId(id, org)
        .orElseThrow(() -> new AttendanceException("DEVICE_NOT_FOUND", "Device not found: " + id));
  }

  private void requireAttendance(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FEATURE_ATTENDANCE)) {
      throw new AttendanceException(
          "FEATURE_DISABLED", "FEATURE_ATTENDANCE is off for this subscription plan.");
    }
  }

  private Map<String, Object> moduleSettings(TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, AttendanceRecordService.MODULE_ATTENDANCE);
    return module != null ? module : Map.of();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> settingsMap(Map<String, Object> module) {
    Object settings = module.get("settings");
    if (settings instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    // Module settings API may return flattened ModuleSettings or payload as root
    if (module.containsKey("aiAttendanceEnabled") || module.containsKey("formKey")) {
      return module;
    }
    return module;
  }

  private Map<String, Object> toDeviceMap(AttendanceDeviceEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("organizationId", e.getOrganizationId());
    m.put("branchId", e.getBranchId());
    m.put("deviceKey", e.getDeviceKey());
    m.put("name", e.getName());
    m.put("adapterType", e.getAdapterType());
    m.put("status", e.getStatus());
    m.put("config", e.getConfigJson());
    m.put("requiredFeatureFlag", DeviceAdapterCatalog.requiredFlag(e.getAdapterType()));
    m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
    return m;
  }

  private Map<String, Object> toEventMap(AttendanceDeviceEventEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("deviceId", e.getDeviceId());
    m.put("adapterType", e.getAdapterType());
    m.put("status", e.getStatus());
    m.put("payload", e.getPayloadJson());
    m.put("attendanceRecordId", e.getAttendanceRecordId());
    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    return m;
  }

  private static List<String> defaultAccepted() {
    return List.of(
        DeviceAdapterCatalog.TYPE_BIOMETRIC,
        DeviceAdapterCatalog.TYPE_FACE,
        DeviceAdapterCatalog.TYPE_GPS,
        DeviceAdapterCatalog.TYPE_AI_CAMERA);
  }

  private static List<String> asStringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return defaultAccepted();
    }
    List<String> out = new ArrayList<>();
    for (Object item : list) {
      if (item != null) {
        out.add(String.valueOf(item));
      }
    }
    return out.isEmpty() ? defaultAccepted() : out;
  }

  private static String normalizeKey(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim()
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9_-]+", "-")
        .replaceAll("-+", "-")
        .replaceAll("^-|-$", "");
  }

  private static boolean boolOr(Object v, boolean d) {
    if (v instanceof Boolean b) {
      return b;
    }
    if (v != null) {
      return "true".equalsIgnoreCase(String.valueOf(v));
    }
    return d;
  }

  private static double doubleOr(Object v, double d) {
    if (v instanceof Number n) {
      return n.doubleValue();
    }
    try {
      return v != null ? Double.parseDouble(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }

  private static String firstNonBlank(Object... values) {
    for (Object v : values) {
      if (v != null) {
        String s = String.valueOf(v).trim();
        if (!s.isBlank() && !"null".equalsIgnoreCase(s)) {
          return s;
        }
      }
    }
    return "";
  }
}
