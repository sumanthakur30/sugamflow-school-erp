package com.sugamflow.school.library.persistence.repo;

import com.sugamflow.school.library.persistence.entity.LibraryBookEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryBookRepository extends JpaRepository<LibraryBookEntity, UUID> {
  List<LibraryBookEntity> findByOrganizationIdOrderByTitleAsc(String organizationId);

  Optional<LibraryBookEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
