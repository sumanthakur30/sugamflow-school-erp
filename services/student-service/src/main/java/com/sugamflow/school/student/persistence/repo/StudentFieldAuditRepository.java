package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.StudentFieldAuditEntity;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentFieldAuditRepository extends JpaRepository<StudentFieldAuditEntity, UUID> {

  Page<StudentFieldAuditEntity> findByOrganizationIdAndStudentIdOrderByChangedAtDesc(
      String organizationId, UUID studentId, Pageable pageable);
}
