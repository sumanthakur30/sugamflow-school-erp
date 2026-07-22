package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.ExpenseRecordEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExpenseRecordRepository extends JpaRepository<ExpenseRecordEntity, UUID> {

  Optional<ExpenseRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  long countByOrganizationId(String organizationId);

  Page<ExpenseRecordEntity> findByOrganizationIdOrderByExpenseDateDescCreatedAtDesc(
      String organizationId, Pageable pageable);

  Page<ExpenseRecordEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusNotOrderByExpenseDateDescCreatedAtDesc(
          String organizationId,
          String branchId,
          String academicSessionId,
          String status,
          Pageable pageable);

  Page<ExpenseRecordEntity> findByOrganizationIdAndStatusNotOrderByExpenseDateDescCreatedAtDesc(
      String organizationId, String status, Pageable pageable);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(amount), 0)
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
            AND (:categoryBlank = true OR category = :category)
            AND (:modeBlank = true OR payment_mode = :paymentMode)
          """,
      nativeQuery = true)
  Double sumAmount(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId,
      @Param("categoryBlank") boolean categoryBlank,
      @Param("category") String category,
      @Param("modeBlank") boolean modeBlank,
      @Param("paymentMode") String paymentMode);

  @Query(
      value =
          """
          SELECT category, COALESCE(SUM(amount), 0) AS total
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY category
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumByCategory(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT payment_mode, COALESCE(SUM(amount), 0) AS total
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY payment_mode
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumByPaymentMode(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT to_char(expense_date, 'YYYY-MM') AS ym, COALESCE(SUM(amount), 0) AS total
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY ym
          ORDER BY ym
          """,
      nativeQuery = true)
  List<Object[]> sumByMonth(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT COALESCE(branch_id, 'main') AS branch_key, COALESCE(SUM(amount), 0) AS total
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY branch_key
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumByBranch(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT *
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
            AND (:categoryBlank = true OR category = :category)
          ORDER BY expense_date DESC, created_at DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<ExpenseRecordEntity> recent(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId,
      @Param("categoryBlank") boolean categoryBlank,
      @Param("category") String category,
      @Param("limit") int limit);

  @Query(
      value =
          """
          SELECT *
          FROM expense_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND expense_date >= CAST(:fromDate AS date)
            AND expense_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
            AND (:categoryBlank = true OR category = :category)
          ORDER BY amount DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<ExpenseRecordEntity> topByAmount(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId,
      @Param("categoryBlank") boolean categoryBlank,
      @Param("category") String category,
      @Param("limit") int limit);

  List<ExpenseRecordEntity> findByOrganizationIdAndExpenseDateBetweenOrderByExpenseDateDesc(
      String organizationId, LocalDate from, LocalDate to);
}
