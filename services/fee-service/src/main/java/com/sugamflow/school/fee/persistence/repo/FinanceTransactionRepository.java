package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.FinanceTransactionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransactionEntity, UUID> {

  List<FinanceTransactionEntity> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

  Optional<FinanceTransactionEntity> findByOrganizationIdAndIdempotencyKey(
      String organizationId, String idempotencyKey);

  Optional<FinanceTransactionEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  @Query(
      value =
          """
          SELECT * FROM finance_transaction
          WHERE transaction_type = 'PAYMENT_INTENT'
            AND payload->>'gatewayOrderId' = :orderId
          ORDER BY created_at DESC
          LIMIT 1
          """,
      nativeQuery = true)
  Optional<FinanceTransactionEntity> findPaymentIntentByGatewayOrderId(
      @Param("orderId") String orderId);
}
