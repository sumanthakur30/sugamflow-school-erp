package com.sugamflow.school.website.persistence.repo;

import com.sugamflow.school.website.persistence.entity.WebsiteAnalyticsEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebsiteAnalyticsEventRepository extends JpaRepository<WebsiteAnalyticsEvent, UUID> {

  List<WebsiteAnalyticsEvent> findTop100ByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  @Query(
      """
      select e.eventType, count(e) from WebsiteAnalyticsEvent e
      where e.organizationId = :org and e.createdAt >= :since
      group by e.eventType
      """)
  List<Object[]> countByTypeSince(@Param("org") String organizationId, @Param("since") Instant since);

  @Modifying(clearAutomatically = true)
  @Query("delete from WebsiteAnalyticsEvent e where e.createdAt < :before")
  int deleteOlderThan(@Param("before") Instant before);
}
