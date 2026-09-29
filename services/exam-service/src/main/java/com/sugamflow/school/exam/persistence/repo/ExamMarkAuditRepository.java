package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ExamMarkAuditEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamMarkAuditRepository extends JpaRepository<ExamMarkAuditEntity, UUID> {

  List<ExamMarkAuditEntity> findByExamDefinitionIdAndStudentIdOrderByChangedAtDesc(
      UUID examDefinitionId, UUID studentId);

  List<ExamMarkAuditEntity> findByExamDefinitionIdAndAdmissionNoOrderByChangedAtDesc(
      UUID examDefinitionId, String admissionNo);
}
