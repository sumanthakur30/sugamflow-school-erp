package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ExamRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamRecordRepository extends JpaRepository<ExamRecordEntity, UUID> {

  List<ExamRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<ExamRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId, Pageable pageable);
  Page<ExamRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<ExamRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
