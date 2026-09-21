package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ExamDefinitionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamDefinitionRepository extends JpaRepository<ExamDefinitionEntity, UUID> {

  Optional<ExamDefinitionEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  List<ExamDefinitionEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  List<ExamDefinitionEntity> findByOrganizationIdAndSectionIdOrderByUpdatedAtDesc(
      String organizationId, UUID sectionId);

  List<ExamDefinitionEntity> findByOrganizationIdAndSectionIdAndSubjectIdOrderByUpdatedAtDesc(
      String organizationId, UUID sectionId, UUID subjectId);

  List<ExamDefinitionEntity> findByOrganizationIdAndStatusOrderByUpdatedAtDesc(
      String organizationId, String status);

  List<ExamDefinitionEntity> findByOrganizationIdAndSectionIdAndTermKeyOrderByNameAsc(
      String organizationId, UUID sectionId, String termKey);

  List<ExamDefinitionEntity> findByOrganizationIdAndSectionIdAndTermKeyAndStatusOrderByNameAsc(
      String organizationId, UUID sectionId, String termKey, String status);
}
