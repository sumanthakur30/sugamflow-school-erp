package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.StaffAttendanceMonthEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StaffAttendanceMonthRepository
    extends JpaRepository<StaffAttendanceMonthEntity, UUID> {

  Optional<StaffAttendanceMonthEntity> findByOrganizationIdAndBranchIdAndYearMonth(
      String organizationId, String branchId, String yearMonth);
}
