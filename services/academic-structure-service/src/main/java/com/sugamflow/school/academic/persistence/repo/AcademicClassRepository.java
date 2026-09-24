package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.AcademicClassEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicClassRepository extends JpaRepository<AcademicClassEntity, UUID> {

  List<AcademicClassEntity> findByOrganizationIdOrderBySequenceNoAscNameAsc(String organizationId);

  List<AcademicClassEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderBySequenceNoAscNameAsc(
          String organizationId, String branchId, String academicSessionId);

  Optional<AcademicClassEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  long countByOrganizationId(String organizationId);
}
