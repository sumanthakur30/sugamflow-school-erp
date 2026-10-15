package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.ExamEntryPolicyEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamEntryPolicyRepository extends JpaRepository<ExamEntryPolicyEntity, String> {

  Optional<ExamEntryPolicyEntity> findByOrganizationId(String organizationId);
}
