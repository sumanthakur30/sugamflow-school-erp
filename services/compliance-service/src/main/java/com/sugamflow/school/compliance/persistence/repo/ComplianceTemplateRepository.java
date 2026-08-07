package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceTemplateEntity;

public interface ComplianceTemplateRepository extends JpaRepository<ComplianceTemplateEntity, Long> {
  List<ComplianceTemplateEntity> findAllByOrderByBoardCodeAscPackKeyAsc();

  List<ComplianceTemplateEntity> findByActiveTrueAndStatusOrderByBoardCodeAscPackKeyAsc(String status);

  Optional<ComplianceTemplateEntity> findByPackKey(String packKey);

  List<ComplianceTemplateEntity> findByBoardCodeAndActiveTrueAndStatusOrderByPackKeyAsc(
      String boardCode, String status);
}
