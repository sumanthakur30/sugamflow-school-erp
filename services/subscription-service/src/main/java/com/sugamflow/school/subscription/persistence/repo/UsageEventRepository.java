package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.UsageEventEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsageEventRepository extends JpaRepository<UsageEventEntity, Long> {
  List<UsageEventEntity> findTop50ByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  List<UsageEventEntity> findTop50ByOrganizationIdAndLimitCodeOrderByCreatedAtDesc(
      String organizationId, String limitCode);
}
