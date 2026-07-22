package com.sugamflow.school.hostel.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.hostel.persistence.entity.HostelBedEntity;
import com.sugamflow.school.hostel.persistence.entity.HostelOccupancyEntity;
import com.sugamflow.school.hostel.persistence.repo.HostelBedRepository;
import com.sugamflow.school.hostel.persistence.repo.HostelOccupancyRepository;
import com.sugamflow.school.hostel.web.HostelException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HostelBedsService {

  private final HostelBedRepository beds;
  private final HostelOccupancyRepository occupancies;

  public HostelBedsService(HostelBedRepository beds, HostelOccupancyRepository occupancies) {
    this.beds = beds;
    this.occupancies = occupancies;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listBeds() {
    TenantScope scope = TenantContext.require();
    return beds.findByOrganizationIdOrderByBlockKeyAscRoomNoAscBedNoAsc(scope.organizationId())
        .stream()
        .map(this::bedDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsertBed(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String block = strOr(body.get("blockKey"), "main_block");
    String room = str(body.get("roomNo"));
    if (room == null) {
      throw new HostelException("VALIDATION", "roomNo is required");
    }
    int bedNo = body.get("bedNo") instanceof Number n ? n.intValue() : 1;
    if (bedNo < 1) {
      throw new HostelException("VALIDATION", "bedNo must be >= 1");
    }
    UUID id = parseUuid(body.get("id"));
    HostelBedEntity bed =
        id == null
            ? null
            : beds.findByIdAndOrganizationId(id, scope.organizationId()).orElse(null);
    if (bed == null) {
      bed = new HostelBedEntity();
      bed.setId(UUID.randomUUID());
      bed.setOrganizationId(scope.organizationId());
      bed.setBranchId(scope.branchId());
      bed.setCreatedAt(Instant.now());
      bed.setStatus("VACANT");
    }
    bed.setBlockKey(block);
    bed.setRoomNo(room);
    bed.setBedNo(bedNo);
    if (str(body.get("status")) != null && id != null) {
      bed.setStatus(str(body.get("status")));
    }
    bed.setUpdatedAt(Instant.now());
    return bedDto(beds.save(bed));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> activeOccupancies() {
    TenantScope scope = TenantContext.require();
    return occupancies
        .findByOrganizationIdAndStatusOrderByAllocatedAtDesc(scope.organizationId(), "ACTIVE")
        .stream()
        .map(this::occDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public Map<String, Object> activeOccupancyForAdmission(String admissionNo) {
    TenantScope scope = TenantContext.require();
    if (admissionNo == null || admissionNo.isBlank()) {
      return Map.of();
    }
    return occupancies
        .findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
            scope.organizationId(), admissionNo.trim(), "ACTIVE")
        .map(this::occDto)
        .orElse(Map.of());
  }

  @Transactional
  public Map<String, Object> allocate(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    UUID bedId = parseUuid(body.get("bedId"));
    String admission = str(body.get("admissionNo"));
    if (bedId == null || admission == null) {
      throw new HostelException("VALIDATION", "bedId and admissionNo are required");
    }
    if (occupancies
        .findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
            scope.organizationId(), admission, "ACTIVE")
        .isPresent()) {
      throw new HostelException("CONFLICT", "Student already has an active bed");
    }
    HostelBedEntity bed =
        beds.findByIdAndOrganizationId(bedId, scope.organizationId())
            .orElseThrow(() -> new HostelException("NOT_FOUND", "Bed not found"));
    if (!"VACANT".equals(bed.getStatus())) {
      throw new HostelException("UNAVAILABLE", "Bed is not vacant");
    }
    HostelOccupancyEntity occ = new HostelOccupancyEntity();
    occ.setId(UUID.randomUUID());
    occ.setOrganizationId(scope.organizationId());
    occ.setBranchId(scope.branchId());
    occ.setBedId(bed.getId());
    occ.setStudentId(parseUuid(body.get("studentId")));
    occ.setAdmissionNo(admission);
    occ.setStudentName(str(body.get("studentName")));
    occ.setStatus("ACTIVE");
    occ.setAllocatedAt(Instant.now());
    occ.setCreatedBy(scope.userId());
    occ.setCreatedAt(Instant.now());
    occ.setUpdatedAt(Instant.now());
    bed.setStatus("OCCUPIED");
    bed.setUpdatedAt(Instant.now());
    beds.save(bed);
    return occDto(occupancies.save(occ));
  }

  @Transactional
  public Map<String, Object> release(UUID occupancyId) {
    TenantScope scope = TenantContext.require();
    HostelOccupancyEntity occ =
        occupancies
            .findByIdAndOrganizationId(occupancyId, scope.organizationId())
            .orElseThrow(() -> new HostelException("NOT_FOUND", "Occupancy not found"));
    if (!"ACTIVE".equals(occ.getStatus())) {
      throw new HostelException("CONFLICT", "Already released");
    }
    HostelBedEntity bed =
        beds.findByIdAndOrganizationId(occ.getBedId(), scope.organizationId())
            .orElseThrow(() -> new HostelException("NOT_FOUND", "Bed not found"));
    occ.setStatus("RELEASED");
    occ.setReleasedAt(Instant.now());
    occ.setUpdatedAt(Instant.now());
    bed.setStatus("VACANT");
    bed.setUpdatedAt(Instant.now());
    beds.save(bed);
    return occDto(occupancies.save(occ));
  }

  private Map<String, Object> bedDto(HostelBedEntity b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId().toString());
    m.put("blockKey", b.getBlockKey());
    m.put("roomNo", b.getRoomNo());
    m.put("bedNo", b.getBedNo());
    m.put("status", b.getStatus());
    return m;
  }

  private Map<String, Object> occDto(HostelOccupancyEntity o) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", o.getId().toString());
    m.put("bedId", o.getBedId().toString());
    m.put("studentId", o.getStudentId() == null ? null : o.getStudentId().toString());
    m.put("admissionNo", o.getAdmissionNo());
    m.put("studentName", o.getStudentName());
    m.put("status", o.getStatus());
    m.put("allocatedAt", o.getAllocatedAt().toString());
    m.put("releasedAt", o.getReleasedAt() == null ? null : o.getReleasedAt().toString());
    beds.findByIdAndOrganizationId(o.getBedId(), o.getOrganizationId())
        .ifPresent(
            bed -> {
              m.put("blockKey", bed.getBlockKey());
              m.put("roomNo", bed.getRoomNo());
              m.put("bedNo", bed.getBedNo());
              m.put(
                  "label",
                  bed.getBlockKey() + " / Room " + bed.getRoomNo() + " / Bed " + bed.getBedNo());
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
