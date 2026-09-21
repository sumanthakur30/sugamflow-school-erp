package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ExamMarkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamMarkRepository extends JpaRepository<ExamMarkEntity, UUID> {

  List<ExamMarkEntity> findByExamDefinitionIdOrderByStudentNameAsc(UUID examDefinitionId);

  Optional<ExamMarkEntity> findByExamDefinitionIdAndStudentId(UUID examDefinitionId, UUID studentId);

  Optional<ExamMarkEntity> findByExamDefinitionIdAndAdmissionNo(
      UUID examDefinitionId, String admissionNo);

  List<ExamMarkEntity> findByOrganizationIdAndStudentIdOrderByUpdatedAtDesc(
      String organizationId, UUID studentId);

  List<ExamMarkEntity> findByOrganizationIdAndAdmissionNoOrderByUpdatedAtDesc(
      String organizationId, String admissionNo);
}
