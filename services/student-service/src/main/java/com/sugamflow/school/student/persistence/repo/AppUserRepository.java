package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.AppUserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUserEntity, UUID> {

  List<AppUserEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Optional<AppUserEntity> findByOrganizationIdAndUsernameIgnoreCase(
      String organizationId, String username);
}
