package com.sugamflow.school.library.persistence.repo;

import com.sugamflow.school.library.persistence.entity.LibraryRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryRecordRepository extends JpaRepository<LibraryRecordEntity, UUID> {

  List<LibraryRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<LibraryRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId, Pageable pageable);
  Page<LibraryRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  List<LibraryRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId);

  Optional<LibraryRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
