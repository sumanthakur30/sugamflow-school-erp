package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.ValidationRuleEntity;

public interface ValidationRuleRepository extends JpaRepository<ValidationRuleEntity, Long> {
  List<ValidationRuleEntity> findByBoardCodeAndActiveTrue(String boardCode);

  List<ValidationRuleEntity> findByBoardCodeOrderByRuleCodeAsc(String boardCode);
}
