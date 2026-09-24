package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.TeachingAssignmentEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeachingAssignmentRepository
    extends JpaRepository<TeachingAssignmentEntity, UUID> {

  List<TeachingAssignmentEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<TeachingAssignmentEntity> findByOrganizationIdAndSectionId(
      String organizationId, UUID sectionId);

  List<TeachingAssignmentEntity> findByOrganizationIdAndTeacherUsername(
      String organizationId, String teacherUsername);

  Optional<TeachingAssignmentEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
