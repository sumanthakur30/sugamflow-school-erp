package com.sugamflow.school.student.privacy;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolPrivacyAuditRepository extends JpaRepository<SchoolPrivacyAuditEntity, UUID> {}
