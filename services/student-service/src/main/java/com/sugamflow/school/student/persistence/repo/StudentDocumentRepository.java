package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.StudentDocumentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentDocumentRepository extends JpaRepository<StudentDocumentEntity, UUID> {

  List<StudentDocumentEntity> findByOrganizationIdAndStudentIdOrderByIssuedAtDesc(
      String organizationId, UUID studentId);

  Optional<StudentDocumentEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<StudentDocumentEntity> findByVerificationToken(String verificationToken);
}
