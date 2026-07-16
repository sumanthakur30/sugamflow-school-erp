package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.FinanceTransactionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransactionEntity, UUID> {

  List<FinanceTransactionEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<FinanceTransactionEntity> findByOrganizationIdAndIdempotencyKey(
      String organizationId, String idempotencyKey);

  Optional<FinanceTransactionEntity> findByIdAndOrganizationId(UUID id, String organizationId);
}
