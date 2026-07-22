package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.AttendanceSessionEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSessionEntity, UUID> {

  Optional<AttendanceSessionEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<AttendanceSessionEntity>
      findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodId(
          String organizationId, UUID sectionId, LocalDate attendanceDate, UUID periodId);

  Optional<AttendanceSessionEntity>
      findByOrganizationIdAndSectionIdAndAttendanceDateAndPeriodIdIsNull(
          String organizationId, UUID sectionId, LocalDate attendanceDate);

  List<AttendanceSessionEntity> findByOrganizationIdAndSectionIdAndAttendanceDateBetweenOrderByAttendanceDateDesc(
      String organizationId, UUID sectionId, LocalDate from, LocalDate to);
}
