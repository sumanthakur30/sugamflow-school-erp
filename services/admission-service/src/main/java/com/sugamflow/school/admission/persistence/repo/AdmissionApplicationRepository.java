package com.sugamflow.school.admission.persistence.repo;

import com.sugamflow.school.admission.persistence.entity.AdmissionApplicationEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdmissionApplicationRepository extends JpaRepository<AdmissionApplicationEntity, UUID> {

  List<AdmissionApplicationEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);

  Page<AdmissionApplicationEntity> findByOrganizationIdOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<AdmissionApplicationEntity> findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
      String organizationId, String branchId, String academicSessionId, Pageable pageable);

  Optional<AdmissionApplicationEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  @Query(
      value =
          """
          SELECT * FROM admission_application a
          WHERE a.organization_id = :org
            AND (:branchBlank = true OR a.branch_id = :branch)
            AND (:sessionBlank = true OR a.academic_session_id = :session)
            AND (:statusBlank = true OR upper(a.status) = upper(cast(:status as text)))
            AND (
              :qBlank = true OR
              lower(cast(a.id as text)) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.status, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.current_step_name, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.assignee_role, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'fullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'mobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'email', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'classApplied', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'classSection', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'guardianFullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'guardianName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'guardianMobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers::text, '')) LIKE lower(concat('%', cast(:q as text), '%'))
            )
          ORDER BY
            CASE WHEN :sortKey = 'fullName' AND :sortAsc = true
              THEN lower(coalesce(a.answers->>'fullName', '')) END ASC NULLS LAST,
            CASE WHEN :sortKey = 'fullName' AND :sortAsc = false
              THEN lower(coalesce(a.answers->>'fullName', '')) END DESC NULLS LAST,
            CASE WHEN :sortKey = 'status' AND :sortAsc = true THEN a.status END ASC NULLS LAST,
            CASE WHEN :sortKey = 'status' AND :sortAsc = false THEN a.status END DESC NULLS LAST,
            CASE WHEN :sortKey = 'updatedAt' AND :sortAsc = true THEN a.updated_at END ASC NULLS LAST,
            CASE WHEN :sortKey = 'updatedAt' AND :sortAsc = false THEN a.updated_at END DESC NULLS LAST,
            a.updated_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM admission_application a
          WHERE a.organization_id = :org
            AND (:branchBlank = true OR a.branch_id = :branch)
            AND (:sessionBlank = true OR a.academic_session_id = :session)
            AND (:statusBlank = true OR upper(a.status) = upper(cast(:status as text)))
            AND (
              :qBlank = true OR
              lower(cast(a.id as text)) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.status, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.current_step_name, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.assignee_role, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'fullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'mobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'email', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'classApplied', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'classSection', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'guardianFullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'guardianName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers->>'guardianMobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(a.answers::text, '')) LIKE lower(concat('%', cast(:q as text), '%'))
            )
          """,
      nativeQuery = true)
  Page<AdmissionApplicationEntity> search(
      @Param("org") String org,
      @Param("branch") String branch,
      @Param("branchBlank") boolean branchBlank,
      @Param("session") String session,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("status") String status,
      @Param("statusBlank") boolean statusBlank,
      @Param("q") String q,
      @Param("qBlank") boolean qBlank,
      @Param("sortKey") String sortKey,
      @Param("sortAsc") boolean sortAsc,
      Pageable pageable);
}
