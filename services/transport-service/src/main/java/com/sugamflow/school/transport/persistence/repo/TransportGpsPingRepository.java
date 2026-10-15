package com.sugamflow.school.transport.persistence.repo;

import com.sugamflow.school.transport.persistence.entity.TransportGpsPingEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransportGpsPingRepository extends JpaRepository<TransportGpsPingEntity, UUID> {

  List<TransportGpsPingEntity> findTop50ByOrganizationIdOrderByRecordedAtDesc(String organizationId);
}
