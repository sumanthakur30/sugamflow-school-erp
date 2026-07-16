package com.sugamflow.school.formbuilder.persistence.repo;

import com.sugamflow.school.formbuilder.persistence.entity.FormDefinitionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FormDefinitionRepository extends JpaRepository<FormDefinitionEntity, Long> {
  Optional<FormDefinitionEntity> findByOrganizationIdAndFormKey(String organizationId, String formKey);
  Optional<FormDefinitionEntity> findByOrganizationIdIsNullAndFormKey(String formKey);
  @Query("select f from FormDefinitionEntity f where f.organizationId = :org or f.organizationId is null order by f.formKey")
  List<FormDefinitionEntity> findVisible(@Param("org") String org);
}