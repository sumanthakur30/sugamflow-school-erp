package com.sugamflow.school.library.persistence.repo;

import com.sugamflow.school.library.persistence.entity.LibraryCirculationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryCirculationRepository extends JpaRepository<LibraryCirculationEntity, UUID> {
  List<LibraryCirculationEntity> findByOrganizationIdAndStatusOrderByIssuedAtDesc(
      String organizationId, String status);

  Optional<LibraryCirculationEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  long countByOrganizationIdAndAdmissionNoAndStatus(
      String organizationId, String admissionNo, String status);

  List<LibraryCirculationEntity> findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
      String organizationId, String admissionNo, String status);
}
