package com.sugamflow.school.settings.persistence.repo;

import com.sugamflow.school.settings.persistence.entity.OrgBranchEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgBranchRepository extends JpaRepository<OrgBranchEntity, Long> {

  List<OrgBranchEntity> findByOrganizationIdOrderByNameAsc(String organizationId);

  Optional<OrgBranchEntity> findByOrganizationIdAndBranchKey(String organizationId, String branchKey);

  long countByOrganizationId(String organizationId);

  @org.springframework.data.jpa.repository.Query(
      "SELECT e FROM OrgBranchEntity e WHERE e.organizationId = :org AND e.isDefault = true")
  List<OrgBranchEntity> findDefaults(@org.springframework.data.repository.query.Param("org") String org);
}
