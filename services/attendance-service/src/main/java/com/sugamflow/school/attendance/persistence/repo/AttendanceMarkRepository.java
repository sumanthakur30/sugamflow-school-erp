package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.AttendanceMarkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceMarkRepository extends JpaRepository<AttendanceMarkEntity, UUID> {

  List<AttendanceMarkEntity> findBySessionIdOrderByStudentNameAsc(UUID sessionId);

  Optional<AttendanceMarkEntity> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

  Optional<AttendanceMarkEntity> findBySessionIdAndAdmissionNo(UUID sessionId, String admissionNo);

  List<AttendanceMarkEntity> findByOrganizationIdAndStudentIdOrderByMarkedAtDesc(
      String organizationId, UUID studentId);

  List<AttendanceMarkEntity> findByOrganizationIdAndAdmissionNoOrderByMarkedAtDesc(
      String organizationId, String admissionNo);
}
