package com.sugamflow.school.cms.persistence.repo;

import com.sugamflow.school.cms.persistence.entity.CmsEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CmsEventRepository extends JpaRepository<CmsEvent, UUID> {
  List<CmsEvent> findByOrganizationIdAndStatusOrderByStartsAtAsc(
      String organizationId, String status);

  List<CmsEvent> findByOrganizationIdOrderByStartsAtDesc(String organizationId);

  Optional<CmsEvent> findByIdAndOrganizationId(UUID id, String organizationId);
}
