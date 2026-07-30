package com.sugamflow.school.subscription.persistence.repo;

import com.sugamflow.school.subscription.persistence.entity.EnterpriseOrgSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnterpriseOrgSettingsRepository
    extends JpaRepository<EnterpriseOrgSettingsEntity, String> {}
