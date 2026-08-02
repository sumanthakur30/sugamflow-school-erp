package com.sugamflow.school.website.persistence.repo;

import com.sugamflow.school.website.persistence.entity.WebsiteTemplate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteTemplateRepository extends JpaRepository<WebsiteTemplate, String> {
  List<WebsiteTemplate> findByActiveTrueOrderBySortOrderAscNameAsc();
}
