package com.sugamflow.school.academic.persistence.repo;

import com.sugamflow.school.academic.persistence.entity.TimetableRoomEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimetableRoomRepository extends JpaRepository<TimetableRoomEntity, UUID> {

  List<TimetableRoomEntity> findByOrganizationIdOrderByNameAsc(String organizationId);

  Optional<TimetableRoomEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
