package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.TimetableSubstituteEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableSubstituteRepository extends JpaRepository<TimetableSubstituteEntity, UUID> {

  Optional<TimetableSubstituteEntity> findBySlotIdAndSubstituteDate(UUID slotId, LocalDate substituteDate);

  List<TimetableSubstituteEntity> findByOrganizationIdAndTeacherUsernameAndSubstituteDate(
      String organizationId, String teacherUsername, LocalDate substituteDate);

  List<TimetableSubstituteEntity> findBySlotIdInAndSubstituteDateGreaterThanEqual(
      List<UUID> slotIds, LocalDate substituteDate);
}
