package com.sugamflow.school.payroll.persistence.repo;

import com.sugamflow.school.payroll.persistence.entity.PayrollRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollRecordRepository extends JpaRepository<PayrollRecordEntity, UUID> {

  List<PayrollRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<PayrollRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<PayrollRecordEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<PayrollRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(NULLIF(answers->>'netPay','')::numeric), 0)
          FROM payroll_record
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          """,
      nativeQuery = true)
  Double sumApprovedNetPay(
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
          SELECT COALESCE(SUM(NULLIF(answers->>'netPay','')::numeric), 0)
          FROM payroll_record
          WHERE organization_id = :org
            AND status IN ('IN_PROGRESS', 'INFO_REQUESTED')
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          """,
      nativeQuery = true)
  Double sumPendingNetPay(
      @Param("org") String org,
      @Param("allBranches") boolean allBranches,
      @Param("branchCsv") String branchCsv,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("sessionId") String sessionId);

  @Query(
      value =
          """
          SELECT COALESCE(SUM(NULLIF(answers->>'allowances','')::numeric), 0)
          FROM payroll_record
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          """,
      nativeQuery = true)
  Double sumApprovedAllowances(
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
          SELECT COALESCE(SUM(NULLIF(answers->>'deductions','')::numeric), 0)
          FROM payroll_record
          WHERE organization_id = :org
            AND status = 'APPROVED'
            AND created_at >= CAST(:fromTs AS timestamptz)
            AND created_at < CAST(:toTs AS timestamptz)
            AND (:allBranches = true OR branch_id = ANY(string_to_array(:branchCsv, ',')))
            AND (:sessionBlank = true OR academic_session_id = :sessionId)
          """,
      nativeQuery = true)
  Double sumApprovedDeductions(
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
                 COALESCE(SUM(NULLIF(answers->>'netPay','')::numeric), 0) AS total
          FROM payroll_record
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
}
