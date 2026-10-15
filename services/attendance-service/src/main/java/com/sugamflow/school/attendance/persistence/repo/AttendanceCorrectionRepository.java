package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.AttendanceCorrectionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceCorrectionRepository extends JpaRepository<AttendanceCorrectionEntity, UUID> {

  List<AttendanceCorrectionEntity> findByBiometricDayIdOrderByCorrectedAtDesc(UUID biometricDayId);
}
