package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceDeviceRepository extends JpaRepository<AttendanceDeviceEntity, String> {

  List<AttendanceDeviceEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Optional<AttendanceDeviceEntity> findByOrganizationIdAndDeviceKey(
      String organizationId, String deviceKey);

  Optional<AttendanceDeviceEntity> findByIdAndOrganizationId(String id, String organizationId);
}
