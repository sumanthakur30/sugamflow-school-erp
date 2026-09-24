package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.ClassSectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassSectionRepository extends JpaRepository<ClassSectionEntity, UUID> {

  List<ClassSectionEntity> findByOrganizationIdOrderByNameAsc(String organizationId);

  List<ClassSectionEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByNameAsc(
          String organizationId, String branchId, String academicSessionId);

  List<ClassSectionEntity> findByOrganizationIdAndClassIdOrderByNameAsc(
      String organizationId, UUID classId);

  Optional<ClassSectionEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  long countByOrganizationId(String organizationId);
}
