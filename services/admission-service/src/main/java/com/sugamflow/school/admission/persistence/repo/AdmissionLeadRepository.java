package com.sugamflow.school.admission.persistence.repo;

import com.sugamflow.school.admission.persistence.entity.AdmissionLeadEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionLeadRepository
    extends JpaRepository<AdmissionLeadEntity, UUID>, JpaSpecificationExecutor<AdmissionLeadEntity> {

  Optional<AdmissionLeadEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  boolean existsByOrganizationIdAndExampleSeedTrue(String organizationId);

  @Query(
      "select l.status, count(l) from AdmissionLeadEntity l where l.organizationId = :org group by l.status")
  List<Object[]> countByStatus(@Param("org") String organizationId);
}
