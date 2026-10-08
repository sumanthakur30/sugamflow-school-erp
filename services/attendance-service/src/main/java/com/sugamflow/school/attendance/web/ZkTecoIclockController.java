package com.sugamflow.school.attendance.web;

import com.sugamflow.school.attendance.biometric.BiometricDeviceRejected;
import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEntity;
import com.sugamflow.school.attendance.service.BiometricAttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ZKTeco ADMS push. Responses stay plain text because device firmware does not parse JSON. */
@RestController
@RequestMapping("/iclock")
public class ZkTecoIclockController {

  private static final Logger log = LoggerFactory.getLogger(ZkTecoIclockController.class);
  private static final DateTimeFormatter ZK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final BiometricAttendanceService biometric;

  public ZkTecoIclockController(BiometricAttendanceService biometric) {
    this.biometric = biometric;
  }

  @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> handshake(
      @RequestParam(value = "SN", required = false) String serial,
      @RequestParam(value = "key", required = false) String key,
      @RequestHeader(value = "X-Device-Key", required = false) String headerKey,
      @RequestParam(value = "time", required = false) String deviceTime) {
    try {
      AttendanceDeviceEntity device = biometric.authenticate(serial, first(key, headerKey));
      biometric.heartbeat(device, parseDeviceTime(deviceTime, device.getTimeZone()));
      return ResponseEntity.ok(options(device));
    } catch (BiometricDeviceRejected ex) {
      log.warn("iclock handshake rejected: {}", ex.getCode());
      return ResponseEntity.status(406).body("406");
    }
  }

  @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> push(
      @RequestParam(value = "SN", required = false) String serial,
      @RequestParam(value = "key", required = false) String key,
      @RequestParam(value = "table", required = false) String table,
      @RequestHeader(value = "X-Device-Key", required = false) String headerKey,
      @RequestBody(required = false) String body) {
    try {
      AttendanceDeviceEntity device = biometric.authenticate(serial, first(key, headerKey));
      biometric.heartbeat(device, null);
      if (table == null || table.equalsIgnoreCase("ATTLOG")) {
        biometric.ingestAttlog(device, body);
      }
      return ResponseEntity.ok("OK");
    } catch (BiometricDeviceRejected ex) {
      log.warn("iclock push rejected: {}", ex.getCode());
      return ResponseEntity.status(406).body("406");
    }
  }

  @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> poll(
      @RequestParam(value = "SN", required = false) String serial,
      @RequestParam(value = "key", required = false) String key,
      @RequestHeader(value = "X-Device-Key", required = false) String headerKey,
      @RequestParam(value = "time", required = false) String deviceTime) {
    try {
      AttendanceDeviceEntity device = biometric.authenticate(serial, first(key, headerKey));
      biometric.heartbeat(device, parseDeviceTime(deviceTime, device.getTimeZone()));
      return ResponseEntity.ok(biometric.pollCommands(device));
    } catch (BiometricDeviceRejected ex) {
      log.warn("iclock poll rejected: {}", ex.getCode());
      return ResponseEntity.status(406).body("406");
    }
  }

  @RequestMapping(value = "/devicecmd", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> commandResult(
      @RequestParam(value = "SN", required = false) String serial,
      @RequestParam(value = "key", required = false) String key,
      @RequestHeader(value = "X-Device-Key", required = false) String headerKey,
      @RequestBody(required = false) String body) {
    try {
      AttendanceDeviceEntity device = biometric.authenticate(serial, first(key, headerKey));
      biometric.completeCommands(device, body);
      return ResponseEntity.ok("OK");
    } catch (BiometricDeviceRejected ex) {
      log.warn("iclock command rejected: {}", ex.getCode());
      return ResponseEntity.status(406).body("406");
    }
  }

  @GetMapping(value = "/ping", produces = MediaType.TEXT_PLAIN_VALUE)
  public ResponseEntity<String> ping(
      @RequestParam(value = "SN", required = false) String serial,
      @RequestParam(value = "key", required = false) String key,
      @RequestHeader(value = "X-Device-Key", required = false) String headerKey) {
    try {
      AttendanceDeviceEntity device = biometric.authenticate(serial, first(key, headerKey));
      biometric.heartbeat(device, null);
      return ResponseEntity.ok("OK");
    } catch (BiometricDeviceRejected ex) {
      return ResponseEntity.status(406).body("406");
    }
  }

  private static String options(AttendanceDeviceEntity device) {
    return "GET OPTION FROM: "
        + device.getSerialNumber()
        + "\nATTLOGStamp=0\nOPERLOGStamp=0\nErrorDelay=30\nDelay=10\nTransInterval=1\n"
        + "TransFlag=TransData AttLog\nRealtime=1\nEncrypt=0\nTimeZone="
        + device.getTimeZone()
        + "\n";
  }

  private static String first(String query, String header) {
    if (query != null && !query.isBlank()) {
      return query.trim();
    }
    return header == null ? "" : header.trim();
  }

  private static Instant parseDeviceTime(String raw, String zone) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      ZoneId id = ZoneId.of(zone == null || zone.isBlank() ? "Asia/Kolkata" : zone);
      return LocalDateTime.parse(raw.trim(), ZK_TIME).atZone(id).toInstant();
    } catch (java.time.DateTimeException ex) {
      return null;
    }
  }

  static String clientIp(HttpServletRequest request) {
    if (request == null) {
      return null;
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
