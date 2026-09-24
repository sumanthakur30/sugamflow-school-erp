package com.sugamflow.school.compliance.persistence.repo;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sugamflow.school.compliance.persistence.entity.ValidationFindingEntity;

public interface ValidationFindingRepository extends JpaRepository<ValidationFindingEntity, Long> {
  @Modifying(clearAutomatically = true)
  @Query("delete from ValidationFindingEntity f where f.campaignId = :campaignId")
  void deleteByCampaignId(@Param("campaignId") Long campaignId);

  @Modifying(clearAutomatically = true)
  @Query(
      "delete from ValidationFindingEntity f where f.campaignId = :campaignId and f.source = :source")
  void deleteByCampaignIdAndSource(
      @Param("campaignId") Long campaignId, @Param("source") String source);

  Page<ValidationFindingEntity> findByOrganizationIdAndStatus(
      String organizationId, String status, Pageable pageable);

  Page<ValidationFindingEntity> findByOrganizationIdAndStatusAndEntityType(
      String organizationId, String status, String entityType, Pageable pageable);

  Page<ValidationFindingEntity> findByOrganizationIdAndStatusAndSeverity(
      String organizationId, String status, String severity, Pageable pageable);

  Page<ValidationFindingEntity> findByOrganizationIdAndStatusAndEntityTypeAndSeverity(
      String organizationId, String status, String entityType, String severity, Pageable pageable);

  Page<ValidationFindingEntity> findByCampaignIdAndStatus(
      Long campaignId, String status, Pageable pageable);

  long countByOrganizationIdAndStatusAndSeverity(
      String organizationId, String status, String severity);

  long countByOrganizationIdAndStatusAndEntityType(
      String organizationId, String status, String entityType);

  List<ValidationFindingEntity> findByOrganizationIdAndStatus(String organizationId, String status);

  @Query(
      """
      select f.entityType, f.fieldKey, f.severity, count(f)
      from ValidationFindingEntity f
      where f.organizationId = :org and f.status = 'OPEN'
      group by f.entityType, f.fieldKey, f.severity
      """)
  List<Object[]> readinessGroups(@Param("org") String organizationId);
}
