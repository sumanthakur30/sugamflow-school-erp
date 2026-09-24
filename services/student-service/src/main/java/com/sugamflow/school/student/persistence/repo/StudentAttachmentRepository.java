package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.StudentAttachmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface StudentAttachmentRepository extends JpaRepository<StudentAttachmentEntity, UUID> {

  List<StudentAttachmentEntity> findByOrganizationIdAndStudentIdOrderByCreatedAtDesc(
      String organizationId, UUID studentId);

  List<StudentAttachmentEntity> findByOrganizationIdAndStudentIdAndAttachmentTypeOrderByCreatedAtDesc(
      String organizationId, UUID studentId, String attachmentType);

  Optional<StudentAttachmentEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  @Modifying
  @Transactional
  void deleteByOrganizationIdAndStudentIdAndAttachmentType(
      String organizationId, UUID studentId, String attachmentType);
}
