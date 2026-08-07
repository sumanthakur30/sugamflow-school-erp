package com.sugamflow.school.compliance.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;

public interface SchoolComplianceProfileRepository
    extends JpaRepository<SchoolComplianceProfileEntity, Long> {
  Optional<SchoolComplianceProfileEntity> findByOrganizationId(String organizationId);
}
