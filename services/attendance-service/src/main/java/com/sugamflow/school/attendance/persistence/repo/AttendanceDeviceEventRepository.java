package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.AttendanceDeviceEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceDeviceEventRepository
    extends JpaRepository<AttendanceDeviceEventEntity, String> {

  List<AttendanceDeviceEventEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<AttendanceDeviceEventEntity> findByOrganizationIdAndDeviceIdOrderByCreatedAtDesc(
      String organizationId, String deviceId);
}
