package com.sugamflow.school.reportbuilder.persistence.repo;
import com.sugamflow.school.reportbuilder.persistence.entity.ReportTemplateEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface ReportTemplateRepository extends JpaRepository<ReportTemplateEntity, Long> {
  Optional<ReportTemplateEntity> findByOrganizationIdAndTemplateKey(String organizationId, String templateKey);
  Optional<ReportTemplateEntity> findByOrganizationIdIsNullAndTemplateKey(String templateKey);
  @Query("select t from ReportTemplateEntity t where t.organizationId = :org or t.organizationId is null")
  List<ReportTemplateEntity> findVisible(@Param("org") String org);

  List<ReportTemplateEntity> findByTemplateKey(String templateKey);
}