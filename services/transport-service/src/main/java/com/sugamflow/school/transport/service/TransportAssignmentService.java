package com.sugamflow.school.transport.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.transport.persistence.entity.TransportAssignmentEntity;
import com.sugamflow.school.transport.persistence.entity.TransportRouteEntity;
import com.sugamflow.school.transport.persistence.repo.TransportAssignmentRepository;
import com.sugamflow.school.transport.persistence.repo.TransportRouteRepository;
import com.sugamflow.school.transport.web.TransportException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportAssignmentService {

  private final TransportRouteRepository routes;
  private final TransportAssignmentRepository assignments;

  public TransportAssignmentService(
      TransportRouteRepository routes, TransportAssignmentRepository assignments) {
    this.routes = routes;
    this.assignments = assignments;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listRoutes() {
    TenantScope scope = TenantContext.require();
    return routes.findByOrganizationIdOrderByRouteNameAsc(scope.organizationId()).stream()
        .map(this::routeDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsertRoute(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String key = strOr(body.get("routeKey"), "route_a");
    String name = str(body.get("routeName"));
    if (name == null) {
      throw new TransportException("VALIDATION", "routeName is required");
    }
    UUID id = parseUuid(body.get("id"));
    TransportRouteEntity route =
        id == null
            ? null
            : routes.findByIdAndOrganizationId(id, scope.organizationId()).orElse(null);
    if (route == null) {
      route = new TransportRouteEntity();
      route.setId(UUID.randomUUID());
      route.setOrganizationId(scope.organizationId());
      route.setBranchId(scope.branchId());
      route.setCreatedAt(Instant.now());
      route.setStatus("ACTIVE");
      route.setCapacity(40);
    }
    route.setRouteKey(key);
    route.setRouteName(name);
    route.setVehicleNo(str(body.get("vehicleNo")));
    if (body.get("capacity") instanceof Number n) {
      route.setCapacity(Math.max(1, n.intValue()));
    }
    route.setUpdatedAt(Instant.now());
    return routeDto(routes.save(route));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> activeAssignments() {
    TenantScope scope = TenantContext.require();
    return assignments
        .findByOrganizationIdAndStatusOrderByAssignedAtDesc(scope.organizationId(), "ACTIVE")
        .stream()
        .map(this::assignDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> activeAssignmentForAdmission(String admissionNo) {
    TenantScope scope = TenantContext.require();
    if (admissionNo == null || admissionNo.isBlank()) {
      return Map.of();
    }
    return assignments
        .findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
            scope.organizationId(), admissionNo.trim(), "ACTIVE")
        .map(this::assignDto)
        .orElse(Map.of());
  }

  @Transactional
  public Map<String, Object> assign(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    UUID routeId = parseUuid(body.get("routeId"));
    String admission = str(body.get("admissionNo"));
    if (routeId == null || admission == null) {
      throw new TransportException("VALIDATION", "routeId and admissionNo are required");
    }
    if (assignments
        .findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
            scope.organizationId(), admission, "ACTIVE")
        .isPresent()) {
      throw new TransportException("CONFLICT", "Student already has an active route assignment");
    }
    TransportRouteEntity route =
        routes
            .findByIdAndOrganizationId(routeId, scope.organizationId())
            .orElseThrow(() -> new TransportException("NOT_FOUND", "Route not found"));
    long seated =
        assignments.countByOrganizationIdAndRouteIdAndStatus(
            scope.organizationId(), route.getId(), "ACTIVE");
    if (seated >= route.getCapacity()) {
      throw new TransportException("UNAVAILABLE", "Route is at capacity");
    }
    TransportAssignmentEntity a = new TransportAssignmentEntity();
    a.setId(UUID.randomUUID());
    a.setOrganizationId(scope.organizationId());
    a.setBranchId(scope.branchId());
    a.setRouteId(route.getId());
    a.setStudentId(parseUuid(body.get("studentId")));
    a.setAdmissionNo(admission);
    a.setStudentName(str(body.get("studentName")));
    a.setStopName(str(body.get("stopName")));
    a.setPickupTime(str(body.get("pickupTime")));
    a.setStatus("ACTIVE");
    a.setAssignedAt(Instant.now());
    a.setCreatedBy(scope.userId());
    a.setCreatedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return assignDto(assignments.save(a));
  }

  @Transactional
  public Map<String, Object> end(UUID assignmentId) {
    TenantScope scope = TenantContext.require();
    TransportAssignmentEntity a =
        assignments
            .findByIdAndOrganizationId(assignmentId, scope.organizationId())
            .orElseThrow(() -> new TransportException("NOT_FOUND", "Assignment not found"));
    if (!"ACTIVE".equals(a.getStatus())) {
      throw new TransportException("CONFLICT", "Already ended");
    }
    a.setStatus("ENDED");
    a.setEndedAt(Instant.now());
    a.setUpdatedAt(Instant.now());
    return assignDto(assignments.save(a));
  }

  private Map<String, Object> routeDto(TransportRouteEntity r) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", r.getId().toString());
    m.put("routeKey", r.getRouteKey());
    m.put("routeName", r.getRouteName());
    m.put("vehicleNo", r.getVehicleNo());
    m.put("capacity", r.getCapacity());
    m.put("status", r.getStatus());
    return m;
  }

  private Map<String, Object> assignDto(TransportAssignmentEntity a) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", a.getId().toString());
    m.put("routeId", a.getRouteId().toString());
    m.put("studentId", a.getStudentId() == null ? null : a.getStudentId().toString());
    m.put("admissionNo", a.getAdmissionNo());
    m.put("studentName", a.getStudentName());
    m.put("stopName", a.getStopName());
    m.put("pickupTime", a.getPickupTime());
    m.put("status", a.getStatus());
    m.put("assignedAt", a.getAssignedAt().toString());
    m.put("endedAt", a.getEndedAt() == null ? null : a.getEndedAt().toString());
    routes
        .findByIdAndOrganizationId(a.getRouteId(), a.getOrganizationId())
        .ifPresent(
            route -> {
              m.put("routeKey", route.getRouteKey());
              m.put("routeName", route.getRouteName());
              m.put("vehicleNo", route.getVehicleNo());
            });
    return m;
  }

  private static UUID parseUuid(Object v) {
    if (v == null) return null;
    try {
      return UUID.fromString(String.valueOf(v));
    } catch (Exception ex) {
      return null;
    }
  }

  private static String str(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null ? fallback : s;
  }
}
