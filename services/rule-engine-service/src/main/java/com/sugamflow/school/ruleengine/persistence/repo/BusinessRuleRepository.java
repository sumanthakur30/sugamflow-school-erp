package com.sugamflow.school.ruleengine.persistence.repo;
import com.sugamflow.school.ruleengine.persistence.entity.BusinessRuleEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface BusinessRuleRepository extends JpaRepository<BusinessRuleEntity, Long> {
  Optional<BusinessRuleEntity> findByOrganizationIdAndRuleId(String organizationId, String ruleId);
  Optional<BusinessRuleEntity> findByOrganizationIdIsNullAndRuleId(String ruleId);
  @Query("select r from BusinessRuleEntity r where r.organizationId = :org or r.organizationId is null")
  List<BusinessRuleEntity> findVisible(@Param("org") String org);
}