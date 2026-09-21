package com.sugamflow.school.workflow.persistence.repo;
import com.sugamflow.school.workflow.persistence.entity.WorkflowDefinitionEntity;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, Long> {
  Optional<WorkflowDefinitionEntity> findByOrganizationIdAndWorkflowKey(String organizationId, String workflowKey);
  Optional<WorkflowDefinitionEntity> findByOrganizationIdIsNullAndWorkflowKey(String workflowKey);
  @Query("select w from WorkflowDefinitionEntity w where w.organizationId = :org or w.organizationId is null")
  List<WorkflowDefinitionEntity> findVisible(@Param("org") String org);
}