package com.sugamflow.school.staff.persistence.repo;

import com.sugamflow.school.staff.persistence.entity.StaffRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StaffRecordRepository extends JpaRepository<StaffRecordEntity, UUID> {

  List<StaffRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<StaffRecordEntity> findByOrganizationIdOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<StaffRecordEntity> findByOrganizationIdAndBranchIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, Pageable pageable);

  List<StaffRecordEntity> findByOrganizationIdAndBranchIdOrderByUpdatedAtDesc(
      String organizationId, String branchId);

  Optional<StaffRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<StaffRecordEntity> findByOrganizationIdAndEmployeeNo(
      String organizationId, String employeeNo);

  long countByOrganizationId(String organizationId);

  long countByOrganizationIdAndStatus(String organizationId, String status);

  long countByOrganizationIdAndBranchId(String organizationId, String branchId);

  long countByOrganizationIdAndBranchIdAndStatus(
      String organizationId, String branchId, String status);

  @Query(
      value =
          """
          SELECT * FROM staff_record s
          WHERE s.organization_id = :org
            AND (:branchBlank = true OR s.branch_id = :branch)
            AND (
              (:statusBlank = true AND upper(coalesce(s.status, '')) <> 'DELETED')
              OR (:statusBlank = false AND s.status = :status)
            )
            AND (
              :qBlank = true OR
              lower(coalesce(s.employee_no, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'fullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'mobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'email', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers::text, '')) LIKE lower(concat('%', cast(:q as text), '%'))
            )
            AND (:departmentBlank = true OR lower(coalesce(s.answers->>'department', '')) = lower(cast(:department as text)))
            AND (:designationBlank = true OR lower(coalesce(s.answers->>'designation', '')) = lower(cast(:designation as text)))
            AND (:employmentTypeBlank = true OR lower(coalesce(s.answers->>'employmentType', '')) = lower(cast(:employmentType as text)))
            AND (:genderBlank = true OR lower(coalesce(s.answers->>'gender', '')) = lower(cast(:gender as text)))
            AND (
              :staffGroupBlank = true
              OR (
                upper(cast(:staffGroup as text)) = 'TEACHER'
                AND (
                  lower(coalesce(s.answers->>'designation', '')) LIKE '%teacher%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%principal%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%coordinator%'
                )
              )
              OR (
                upper(cast(:staffGroup as text)) = 'NON_TEACHING'
                AND NOT (
                  lower(coalesce(s.answers->>'designation', '')) LIKE '%teacher%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%principal%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%coordinator%'
                )
              )
            )
            AND (
              :joinedWithinDaysBlank = true
              OR s.created_at >= (CURRENT_TIMESTAMP - (cast(:joinedWithinDays as integer) * INTERVAL '1 day'))
            )
          ORDER BY s.updated_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM staff_record s
          WHERE s.organization_id = :org
            AND (:branchBlank = true OR s.branch_id = :branch)
            AND (
              (:statusBlank = true AND upper(coalesce(s.status, '')) <> 'DELETED')
              OR (:statusBlank = false AND s.status = :status)
            )
            AND (
              :qBlank = true OR
              lower(coalesce(s.employee_no, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'fullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'mobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'email', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers::text, '')) LIKE lower(concat('%', cast(:q as text), '%'))
            )
            AND (:departmentBlank = true OR lower(coalesce(s.answers->>'department', '')) = lower(cast(:department as text)))
            AND (:designationBlank = true OR lower(coalesce(s.answers->>'designation', '')) = lower(cast(:designation as text)))
            AND (:employmentTypeBlank = true OR lower(coalesce(s.answers->>'employmentType', '')) = lower(cast(:employmentType as text)))
            AND (:genderBlank = true OR lower(coalesce(s.answers->>'gender', '')) = lower(cast(:gender as text)))
            AND (
              :staffGroupBlank = true
              OR (
                upper(cast(:staffGroup as text)) = 'TEACHER'
                AND (
                  lower(coalesce(s.answers->>'designation', '')) LIKE '%teacher%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%principal%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%coordinator%'
                )
              )
              OR (
                upper(cast(:staffGroup as text)) = 'NON_TEACHING'
                AND NOT (
                  lower(coalesce(s.answers->>'designation', '')) LIKE '%teacher%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%principal%'
                  OR lower(coalesce(s.answers->>'designation', '')) LIKE '%coordinator%'
                )
              )
            )
            AND (
              :joinedWithinDaysBlank = true
              OR s.created_at >= (CURRENT_TIMESTAMP - (cast(:joinedWithinDays as integer) * INTERVAL '1 day'))
            )
          """,
      nativeQuery = true)
  Page<StaffRecordEntity> searchDirectory(
      @Param("org") String org,
      @Param("branch") String branch,
      @Param("branchBlank") boolean branchBlank,
      @Param("status") String status,
      @Param("statusBlank") boolean statusBlank,
      @Param("q") String q,
      @Param("qBlank") boolean qBlank,
      @Param("department") String department,
      @Param("departmentBlank") boolean departmentBlank,
      @Param("designation") String designation,
      @Param("designationBlank") boolean designationBlank,
      @Param("employmentType") String employmentType,
      @Param("employmentTypeBlank") boolean employmentTypeBlank,
      @Param("gender") String gender,
      @Param("genderBlank") boolean genderBlank,
      @Param("staffGroup") String staffGroup,
      @Param("staffGroupBlank") boolean staffGroupBlank,
      @Param("joinedWithinDays") Integer joinedWithinDays,
      @Param("joinedWithinDaysBlank") boolean joinedWithinDaysBlank,
      Pageable pageable);
}
