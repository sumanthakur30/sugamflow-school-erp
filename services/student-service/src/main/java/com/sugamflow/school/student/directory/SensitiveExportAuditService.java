package com.sugamflow.school.student.directory;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.persistence.entity.SensitiveExportAuditEntity;
import com.sugamflow.school.student.persistence.repo.SensitiveExportAuditRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SensitiveExportAuditService {

  private final SensitiveExportAuditRepository repository;

  public SensitiveExportAuditService(SensitiveExportAuditRepository repository) {
    this.repository = repository;
  }

  /** Commits even when the caller is a read-only export transaction. */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void record(String exportKind, boolean includesAadhaar, int rowCount) {
    TenantScope scope = TenantContext.require();
    SensitiveExportAuditEntity row = new SensitiveExportAuditEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(scope.organizationId());
    row.setBranchId(scope.branchId());
    row.setExportKind(exportKind);
    row.setIncludesAadhaar(includesAadhaar);
    row.setRowCount(Math.max(rowCount, 0));
    row.setActorId(scope.userId());
    row.setActorRole(scope.roleCode());
    row.setCreatedAt(Instant.now());
    repository.save(row);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> recent() {
    TenantScope scope = TenantContext.require();
    return repository.findTop30ByOrganizationIdOrderByCreatedAtDesc(scope.organizationId()).stream()
        .map(this::toMap)
        .toList();
  }

  private Map<String, Object> toMap(SensitiveExportAuditEntity row) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", row.getId().toString());
    out.put("exportKind", row.getExportKind());
    out.put("includesAadhaar", row.isIncludesAadhaar());
    out.put("rowCount", row.getRowCount());
    out.put("actorId", row.getActorId());
    out.put("actorRole", row.getActorRole());
    out.put("branchId", row.getBranchId());
    out.put("createdAt", row.getCreatedAt() != null ? row.getCreatedAt().toString() : null);
    return out;
  }
}
