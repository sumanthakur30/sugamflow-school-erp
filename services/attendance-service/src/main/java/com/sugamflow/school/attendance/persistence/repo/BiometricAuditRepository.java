package com.sugamflow.school.attendance.persistence.repo;

import com.sugamflow.school.attendance.persistence.entity.BiometricAuditEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BiometricAuditRepository extends JpaRepository<BiometricAuditEntity, UUID> {}
