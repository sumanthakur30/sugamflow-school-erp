package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.BiometricRuleEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiometricRuleRepository extends JpaRepository<BiometricRuleEntity, UUID> {

  Optional<BiometricRuleEntity> findByOrganizationIdAndBranchId(String organizationId, String branchId);
}
