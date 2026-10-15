package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ClassroomResponseEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassroomResponseRepository extends JpaRepository<ClassroomResponseEntity, UUID> {

  List<ClassroomResponseEntity> findByOrganizationIdAndItemIdOrderByCreatedAtDesc(
      String organizationId, UUID itemId);
}
