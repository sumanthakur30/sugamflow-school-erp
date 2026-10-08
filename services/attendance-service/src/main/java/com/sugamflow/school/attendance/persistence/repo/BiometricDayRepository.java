package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.BiometricDayEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiometricDayRepository extends JpaRepository<BiometricDayEntity, UUID> {

  Optional<BiometricDayEntity> findByOrganizationIdAndPersonCodeAndAttendanceDate(
      String organizationId, String personCode, LocalDate attendanceDate);

  Optional<BiometricDayEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  List<BiometricDayEntity> findByOrganizationIdAndAttendanceDateOrderByDisplayNameAsc(
      String organizationId, LocalDate attendanceDate);

  List<BiometricDayEntity> findByOrganizationIdAndPersonTypeAndAttendanceDateBetween(
      String organizationId, String personType, LocalDate from, LocalDate to);
}
