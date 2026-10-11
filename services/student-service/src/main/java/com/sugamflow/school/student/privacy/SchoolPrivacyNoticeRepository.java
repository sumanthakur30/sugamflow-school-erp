package com.sugamflow.school.student.privacy;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolPrivacyNoticeRepository extends JpaRepository<SchoolPrivacyNoticeEntity, UUID> {

  long countByNoticeCode(String noticeCode);

  Optional<SchoolPrivacyNoticeEntity> findByNoticeCodeAndVersion(String noticeCode, String version);
}
