package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.HomeworkSubmissionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeworkSubmissionRepository
    extends JpaRepository<HomeworkSubmissionEntity, UUID> {

  List<HomeworkSubmissionEntity> findByOrganizationIdAndHomeworkIdOrderBySubmittedAtDesc(
      String organizationId, UUID homeworkId);

  Optional<HomeworkSubmissionEntity> findByHomeworkIdAndAdmissionNoIgnoreCase(
      UUID homeworkId, String admissionNo);

  Optional<HomeworkSubmissionEntity> findByHomeworkIdAndStudentId(UUID homeworkId, UUID studentId);

  Optional<HomeworkSubmissionEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
