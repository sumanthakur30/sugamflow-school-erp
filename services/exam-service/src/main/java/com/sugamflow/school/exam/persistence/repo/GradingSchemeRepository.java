package com.sugamflow.school.exam.persistence.repo;

import com.sugamflow.school.exam.persistence.entity.GradingSchemeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface GradingSchemeRepository extends JpaRepository<GradingSchemeEntity, UUID> {

  List<GradingSchemeEntity> findByOrganizationIdAndActiveTrueOrderByUpdatedAtDesc(
      String organizationId);
}
