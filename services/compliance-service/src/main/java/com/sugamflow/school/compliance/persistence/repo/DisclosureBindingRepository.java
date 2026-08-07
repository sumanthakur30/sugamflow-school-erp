package com.sugamflow.school.compliance.persistence.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.DisclosureBindingEntity;

public interface DisclosureBindingRepository extends JpaRepository<DisclosureBindingEntity, Long> {
  Optional<DisclosureBindingEntity> findByOrganizationIdAndSectionKey(
      String organizationId, String sectionKey);
}
