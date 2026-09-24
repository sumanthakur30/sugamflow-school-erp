package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sugamflow.school.compliance.persistence.entity.BoardDefinitionEntity;

public interface BoardDefinitionRepository extends JpaRepository<BoardDefinitionEntity, String> {
  List<BoardDefinitionEntity> findByActiveTrueOrderByCodeAsc();
}
