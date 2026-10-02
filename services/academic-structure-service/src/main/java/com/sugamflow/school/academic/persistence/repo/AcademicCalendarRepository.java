package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.AcademicCalendarEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AcademicCalendarRepository extends JpaRepository<AcademicCalendarEntity, UUID> {

  Optional<AcademicCalendarEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionId(
          String organizationId, String branchId, String academicSessionId);
}
