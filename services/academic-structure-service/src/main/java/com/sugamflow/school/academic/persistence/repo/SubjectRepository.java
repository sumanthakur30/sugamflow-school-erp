package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.SubjectEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<SubjectEntity, UUID> {

  List<SubjectEntity> findByOrganizationIdOrderByNameAsc(String organizationId);

  List<SubjectEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByNameAsc(
      String organizationId, String branchId, String academicSessionId);

  Optional<SubjectEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  long countByOrganizationId(String organizationId);
}
