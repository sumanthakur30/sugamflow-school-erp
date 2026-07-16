package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentRecordRepository extends JpaRepository<StudentRecordEntity, UUID> {

  List<StudentRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<StudentRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId, Pageable pageable);

  Page<StudentRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  List<StudentRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId);

  Optional<StudentRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<StudentRecordEntity> findByOrganizationIdAndSourceApplicationId(
      String organizationId, UUID sourceApplicationId);
}
