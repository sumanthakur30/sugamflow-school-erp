package com.sugamflow.school.transport.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.transport.persistence.entity.TransportGpsPingEntity;
import com.sugamflow.school.transport.persistence.repo.TransportGpsPingRepository;
import com.sugamflow.school.transport.web.TransportException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransportGpsService {

  private final TransportGpsPingRepository repository;

  public TransportGpsService(TransportGpsPingRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> latest() {
    TenantScope scope = TenantContext.require();
    return repository.findTop50ByOrganizationIdOrderByRecordedAtDesc(scope.organizationId()).stream()
        .map(this::toDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> ping(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String vehicle = text(body.get("vehicleNo"));
    if (vehicle == null) {
      throw new TransportException("VALIDATION", "vehicleNo is required");
    }
    BigDecimal lat = decimal(body.get("latitude"));
    BigDecimal lng = decimal(body.get("longitude"));
    if (lat == null || lng == null) {
      throw new TransportException("VALIDATION", "latitude and longitude are required");
    }
    TransportGpsPingEntity entity = new TransportGpsPingEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setVehicleNo(vehicle);
    entity.setLatitude(lat);
    entity.setLongitude(lng);
    entity.setNote(text(body.get("note")));
    entity.setRecordedAt(Instant.now());
    return toDto(repository.save(entity));
  }

  private Map<String, Object> toDto(TransportGpsPingEntity entity) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("id", entity.getId().toString());
    row.put("vehicleNo", entity.getVehicleNo());
    row.put("latitude", entity.getLatitude());
    row.put("longitude", entity.getLongitude());
    row.put("note", entity.getNote());
    row.put("recordedAt", entity.getRecordedAt().toString());
    return row;
  }

  private static String text(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private static BigDecimal decimal(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    try {
      return new BigDecimal(String.valueOf(value).trim());
    } catch (NumberFormatException ex) {
      return null;
    }
  }
}
