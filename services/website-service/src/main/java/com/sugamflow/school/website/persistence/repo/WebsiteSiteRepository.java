package com.sugamflow.school.website.persistence.repo;

import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebsiteSiteRepository extends JpaRepository<WebsiteSite, UUID> {

  /** Prefer default campus; falls back for legacy single-site orgs. */
  Optional<WebsiteSite> findFirstByOrganizationIdAndDefaultSiteTrueOrderByCreatedAtAsc(
      String organizationId);

  Optional<WebsiteSite> findByOrganizationIdAndBranchId(String organizationId, String branchId);

  List<WebsiteSite> findByOrganizationIdOrderByDefaultSiteDescBranchIdAsc(String organizationId);

  long countByOrganizationId(String organizationId);

  @Query(
      value =
          """
          select count(*) from website_site
          where organization_id = :org
            and (
              coalesce(homepage_json::text, '') ilike :needle escape '\\'
              or coalesce(theme_json::text, '') ilike :needle escape '\\'
              or coalesce(seo_json::text, '') ilike :needle escape '\\'
              or coalesce(navigation_json::text, '') ilike :needle escape '\\'
            )
          """,
      nativeQuery = true)
  long countMediaNeedle(@Param("org") String organizationId, @Param("needle") String needle);

  /** Legacy helper — returns default site when present. */
  default Optional<WebsiteSite> findByOrganizationId(String organizationId) {
    return findFirstByOrganizationIdAndDefaultSiteTrueOrderByCreatedAtAsc(organizationId)
        .or(() -> findByOrganizationIdAndBranchId(organizationId, "main"));
  }
}
