package com.sugamflow.school.fee.persistence.repo;

import com.sugamflow.school.fee.persistence.entity.FeeCollectionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeeCollectionRepository extends JpaRepository<FeeCollectionEntity, UUID> {

  List<FeeCollectionEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<FeeCollectionEntity> findByOrganizationIdOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<FeeCollectionEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  List<FeeCollectionEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId);

  Optional<FeeCollectionEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0)
          FROM fee_collection
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
            AND (:feeTypeBlank = true OR lower(coalesce(answers->>'feeHead','')) = lower(:feeType))
            AND (:modeBlank = true OR lower(coalesce(answers->>'paymentMode','')) = lower(:paymentMode))
            AND (:collectedByBlank = true OR created_by = :collectedBy)
          """,
      nativeQuery = true)
  Double sumApprovedAmount(
      @Param("org") String org,
      @Param("fromTs") String fromTs,
      @Param("toTs") String toTs,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId,
      @Param("feeTypeBlank") boolean feeTypeBlank,
      @Param("feeType") String feeType,
      @Param("modeBlank") boolean modeBlank,
      @Param("paymentMode") String paymentMode,
      @Param("collectedByBlank") boolean collectedByBlank,
      @Param("collectedBy") String collectedBy);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0)
          FROM fee_collection
          WHERE organization_id = :org
            AND status IN ('IN_PROGRESS', 'INFO_REQUESTED')
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          """,
      nativeQuery = true)
  Double sumPendingAmount(
      @Param("org") String org,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0)
          FROM fee_collection
          WHERE organization_id = :org
            AND status IN ('IN_PROGRESS', 'INFO_REQUESTED')
            AND COALESCE(NULLIF(answers->>'pendingDays','')::numeric, 0) > 30
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          """,
      nativeQuery = true)
  Double sumOverdueAmount(
      @Param("org") String org,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT COALESCE(answers->>'feeHead', 'Other') AS fee_head,
                 COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0) AS total
          FROM fee_collection
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY fee_head
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumByFeeHead(
      @Param("org") String org,
      @Param("fromTs") String fromTs,
      @Param("toTs") String toTs,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT COALESCE(answers->>'paymentMode', 'OTHER') AS mode,
                 COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0) AS total
          FROM fee_collection
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY mode
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumByPaymentMode(
      @Param("org") String org,
      @Param("fromTs") String fromTs,
      @Param("toTs") String toTs,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT to_char(created_at AT TIME ZONE 'Asia/Kolkata', 'YYYY-MM') AS ym,
                 COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0) AS total
          FROM fee_collection
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY ym
          ORDER BY ym
          """,
      nativeQuery = true)
  List<Object[]> sumByMonth(
      @Param("org") String org,
      @Param("fromTs") String fromTs,
      @Param("toTs") String toTs,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT COALESCE(branch_id, 'main') AS branch_key,
                 COALESCE(SUM(NULLIF(answers->>'amount','')::numeric), 0) AS total
          FROM fee_collection
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          GROUP BY branch_key
          ORDER BY total DESC
          """,
      nativeQuery = true)
  List<Object[]> sumByBranch(
      @Param("org") String org,
      @Param("fromTs") String fromTs,
      @Param("toTs") String toTs,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT *
          FROM fee_collection
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          ORDER BY created_at DESC
          LIMIT :limit
          """,
      nativeQuery = true)
  List<FeeCollectionEntity> recentApproved(
      @Param("org") String org,
      @Param("fromTs") String fromTs,
      @Param("toTs") String toTs,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId,
      @Param("limit") int limit);
}
