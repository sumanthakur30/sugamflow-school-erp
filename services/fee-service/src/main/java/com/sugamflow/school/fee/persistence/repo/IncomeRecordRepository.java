package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.IncomeRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncomeRecordRepository extends JpaRepository<IncomeRecordEntity, UUID> {

  Optional<IncomeRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Page<IncomeRecordEntity> findByOrganizationIdAndStatusNotOrderByIncomeDateDescCreatedAtDesc(
      String organizationId, String status, Pageable pageable);

  Page<IncomeRecordEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusNotOrderByIncomeDateDescCreatedAtDesc(
          String organizationId,
          String branchId,
          String academicSessionId,
          String status,
          Pageable pageable);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(amount), 0)
          FROM income_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND income_date >= CAST(:fromDate AS date)
            AND income_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
            AND (:sourceBlank = true OR source = :source)
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
      @Param("sourceBlank") boolean sourceBlank,
      @Param("source") String source,
      @Param("modeBlank") boolean modeBlank,
      @Param("paymentMode") String paymentMode);

  @Query(
      value =
          """
          SELECT source, COALESCE(SUM(amount), 0) AS total
          FROM income_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND income_date >= CAST(:fromDate AS date)
            AND income_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY source
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumBySource(
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
          FROM income_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND income_date >= CAST(:fromDate AS date)
            AND income_date <= CAST(:toDate AS date)
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
          SELECT to_char(income_date, 'YYYY-MM') AS ym, COALESCE(SUM(amount), 0) AS total
          FROM income_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND income_date >= CAST(:fromDate AS date)
            AND income_date <= CAST(:toDate AS date)
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
          FROM income_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND income_date >= CAST(:fromDate AS date)
            AND income_date <= CAST(:toDate AS date)
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
          FROM income_record
          WHERE organization_id = :org
            AND status <> 'VOID'
            AND income_date >= CAST(:fromDate AS date)
            AND income_date <= CAST(:toDate AS date)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          ORDER BY income_date DESC, created_at DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<IncomeRecordEntity> recent(
      @Param("org") String org,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId,
      @Param("limit") int limit);
}
