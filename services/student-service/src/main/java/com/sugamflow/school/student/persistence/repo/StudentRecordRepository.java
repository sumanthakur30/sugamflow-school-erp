package com.sugamflow.school.student.persistence.repo;

import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentRecordRepository extends JpaRepository<StudentRecordEntity, UUID> {

  List<StudentRecordEntity> findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
      String organizationId);

  Page<StudentRecordEntity> findByOrganizationIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
      String organizationId, Pageable pageable);

  Page<StudentRecordEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
          String organizationId, String branchId, String academicSessionId, Pageable pageable);

  List<StudentRecordEntity>
      findByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
          String organizationId, String branchId, String academicSessionId);

  Page<StudentRecordEntity> findByOrganizationIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(
      String organizationId, Pageable pageable);

  Optional<StudentRecordEntity> findByIdAndOrganizationId(UUID id, String organizationId);

  Optional<StudentRecordEntity> findByOrganizationIdAndSourceApplicationIdAndDeletedAtIsNull(
      String organizationId, UUID sourceApplicationId);

  Optional<StudentRecordEntity> findByOrganizationIdAndAdmissionNoIgnoreCaseAndDeletedAtIsNull(
      String organizationId, String admissionNo);

  long countByOrganizationIdAndDeletedAtIsNull(String organizationId);

  long countByOrganizationIdAndStatusAndDeletedAtIsNull(String organizationId, String status);

  long countByOrganizationIdAndBranchIdAndAcademicSessionIdAndDeletedAtIsNull(
      String organizationId, String branchId, String academicSessionId);

  long countByOrganizationIdAndBranchIdAndAcademicSessionIdAndStatusAndDeletedAtIsNull(
      String organizationId, String branchId, String academicSessionId, String status);

  @Query(
      value =
          """
          SELECT * FROM student_record s
          WHERE s.organization_id = :org
            AND (
              (:includeDeleted = true AND s.deleted_at IS NOT NULL)
              OR (:includeDeleted = false AND s.deleted_at IS NULL)
              OR (:includeDeleted IS NULL AND s.deleted_at IS NULL)
            )
            AND (:branchBlank = true OR s.branch_id = :branch)
            AND (:sessionBlank = true OR s.academic_session_id = :session)
            AND (:statusBlank = true OR s.status = :status)
            AND (
              :qBlank = true OR
              lower(cast(s.id as text)) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.admission_no, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'fullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'studentName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'mobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'email', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'rollNo', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'fatherName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'motherName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'guardianName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'parentName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'classSection', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'classApplied', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'house', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'aadhaar', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'rfid', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'cardNo', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'barcode', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'qrCode', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers::text, '')) LIKE lower(concat('%', cast(:q as text), '%'))
            )
            AND (
              :classBlank = true OR
              s.answers->>'classSection' = :classSection OR
              s.answers->>'classApplied' = :classSection
            )
            AND (:genderBlank = true OR lower(coalesce(s.answers->>'gender', '')) = lower(cast(:gender as text)))
            AND (:categoryBlank = true OR lower(coalesce(s.answers->>'category', '')) = lower(cast(:category as text)))
            AND (:houseBlank = true OR lower(coalesce(s.answers->>'house', '')) = lower(cast(:house as text)))
            AND (:transportBlank = true OR lower(coalesce(s.answers->>'transport', 'false')) IN ('true','yes','1'))
            AND (:hostelBlank = true OR lower(coalesce(s.answers->>'hostel', 'false')) IN ('true','yes','1'))
            AND (:scholarshipBlank = true OR lower(coalesce(s.answers->>'scholarship', 'false')) IN ('true','yes','1'))
          ORDER BY s.updated_at DESC
          """,
      countQuery =
          """
          SELECT count(*) FROM student_record s
          WHERE s.organization_id = :org
            AND (
              (:includeDeleted = true AND s.deleted_at IS NOT NULL)
              OR (:includeDeleted = false AND s.deleted_at IS NULL)
              OR (:includeDeleted IS NULL AND s.deleted_at IS NULL)
            )
            AND (:branchBlank = true OR s.branch_id = :branch)
            AND (:sessionBlank = true OR s.academic_session_id = :session)
            AND (:statusBlank = true OR s.status = :status)
            AND (
              :qBlank = true OR
              lower(cast(s.id as text)) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.admission_no, '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'fullName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'studentName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'mobile', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'email', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'rollNo', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'fatherName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'motherName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'guardianName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'parentName', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'classSection', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'classApplied', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'house', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'aadhaar', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'rfid', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'cardNo', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'barcode', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers->>'qrCode', '')) LIKE lower(concat('%', cast(:q as text), '%')) OR
              lower(coalesce(s.answers::text, '')) LIKE lower(concat('%', cast(:q as text), '%'))
            )
            AND (
              :classBlank = true OR
              s.answers->>'classSection' = :classSection OR
              s.answers->>'classApplied' = :classSection
            )
            AND (:genderBlank = true OR lower(coalesce(s.answers->>'gender', '')) = lower(cast(:gender as text)))
            AND (:categoryBlank = true OR lower(coalesce(s.answers->>'category', '')) = lower(cast(:category as text)))
            AND (:houseBlank = true OR lower(coalesce(s.answers->>'house', '')) = lower(cast(:house as text)))
            AND (:transportBlank = true OR lower(coalesce(s.answers->>'transport', 'false')) IN ('true','yes','1'))
            AND (:hostelBlank = true OR lower(coalesce(s.answers->>'hostel', 'false')) IN ('true','yes','1'))
            AND (:scholarshipBlank = true OR lower(coalesce(s.answers->>'scholarship', 'false')) IN ('true','yes','1'))
          """,
      nativeQuery = true)
  Page<StudentRecordEntity> searchDirectory(
      @Param("org") String org,
      @Param("branch") String branch,
      @Param("branchBlank") boolean branchBlank,
      @Param("session") String session,
      @Param("sessionBlank") boolean sessionBlank,
      @Param("status") String status,
      @Param("statusBlank") boolean statusBlank,
      @Param("q") String q,
      @Param("qBlank") boolean qBlank,
      @Param("classSection") String classSection,
      @Param("classBlank") boolean classBlank,
      @Param("gender") String gender,
      @Param("genderBlank") boolean genderBlank,
      @Param("category") String category,
      @Param("categoryBlank") boolean categoryBlank,
      @Param("house") String house,
      @Param("houseBlank") boolean houseBlank,
      @Param("transportBlank") boolean transportBlank,
      @Param("hostelBlank") boolean hostelBlank,
      @Param("scholarshipBlank") boolean scholarshipBlank,
      @Param("includeDeleted") Boolean includeDeleted,
      Pageable pageable);
}
