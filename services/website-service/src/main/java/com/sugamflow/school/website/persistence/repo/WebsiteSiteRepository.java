package com.sugamflow.school.website.persistence.repo;

import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteSiteRepository extends JpaRepository<WebsiteSite, UUID> {

  /** Prefer default campus; falls back for legacy single-site orgs. */
  Optional<WebsiteSite> findFirstByOrganizationIdAndDefaultSiteTrueOrderByCreatedAtAsc(
      String organizationId);

  Optional<WebsiteSite> findByOrganizationIdAndBranchId(String organizationId, String branchId);

  List<WebsiteSite> findByOrganizationIdOrderByDefaultSiteDescBranchIdAsc(String organizationId);

  long countByOrganizationId(String organizationId);

  /** Legacy helper — returns default site when present. */
  default Optional<WebsiteSite> findByOrganizationId(String organizationId) {
    return findFirstByOrganizationIdAndDefaultSiteTrueOrderByCreatedAtAsc(organizationId)
        .or(() -> findByOrganizationIdAndBranchId(organizationId, "main"));
  }
}
