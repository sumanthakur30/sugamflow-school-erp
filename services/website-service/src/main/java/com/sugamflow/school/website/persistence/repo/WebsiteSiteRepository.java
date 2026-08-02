package com.sugamflow.school.website.persistence.repo;

import com.sugamflow.school.website.persistence.entity.WebsiteSite;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteSiteRepository extends JpaRepository<WebsiteSite, UUID> {

  Optional<WebsiteSite> findByOrganizationId(String organizationId);
}
