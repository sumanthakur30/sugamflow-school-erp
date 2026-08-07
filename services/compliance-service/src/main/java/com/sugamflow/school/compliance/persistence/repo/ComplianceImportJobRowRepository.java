package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ComplianceImportJobRowEntity;

public interface ComplianceImportJobRowRepository
    extends JpaRepository<ComplianceImportJobRowEntity, Long> {
  List<ComplianceImportJobRowEntity> findByJobIdOrderByRowNoAsc(Long jobId);

  void deleteByJobId(Long jobId);
}
