package com.sugamflow.school.website.persistence.repo;

import com.sugamflow.school.website.persistence.entity.WebsiteDomain;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteDomainRepository extends JpaRepository<WebsiteDomain, UUID> {

  Optional<WebsiteDomain> findByHostIgnoreCaseAndStatus(String host, String status);

  List<WebsiteDomain> findByOrganizationIdOrderByPrimaryDescHostAsc(String organizationId);
}
