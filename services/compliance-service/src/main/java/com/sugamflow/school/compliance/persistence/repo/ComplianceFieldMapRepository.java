package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceFieldMapEntity;

public interface ComplianceFieldMapRepository extends JpaRepository<ComplianceFieldMapEntity, Long> {
  List<ComplianceFieldMapEntity> findByBoardCodeAndActiveTrueOrderBySortOrderAsc(String boardCode);

  List<ComplianceFieldMapEntity> findByBoardCodeOrderBySortOrderAscEntityTypeAsc(String boardCode);

  List<ComplianceFieldMapEntity> findByBoardCodeAndEntityTypeAndActiveTrueOrderBySortOrderAsc(
      String boardCode, String entityType);
}
