package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.TimetableSlotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlotEntity, UUID> {

  List<TimetableSlotEntity> findByOrganizationId(String organizationId);

  List<TimetableSlotEntity> findByOrganizationIdAndSectionIdOrderByDayOfWeekAsc(
      String organizationId, UUID sectionId);

  List<TimetableSlotEntity> findByOrganizationIdAndTeacherUsernameOrderByDayOfWeekAsc(
      String organizationId, String teacherUsername);

  Optional<TimetableSlotEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  void deleteByOrganizationIdAndSectionId(String organizationId, UUID sectionId);
}
