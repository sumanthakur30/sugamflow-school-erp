package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.TimetablePeriodEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetablePeriodRepository extends JpaRepository<TimetablePeriodEntity, UUID> {

  List<TimetablePeriodEntity> findByOrganizationIdOrderByPeriodNoAsc(String organizationId);

  List<TimetablePeriodEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByPeriodNoAsc(
          String organizationId, String branchId, String academicSessionId);

  Optional<TimetablePeriodEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<TimetablePeriodEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdAndPeriodNo(
          String organizationId, String branchId, String academicSessionId, int periodNo);

  Optional<TimetablePeriodEntity> findByOrganizationIdAndPeriodNo(String organizationId, int periodNo);
}
