package com.sugamflow.school.notification.persistence.repo;
import com.sugamflow.school.notification.persistence.entity.NotificationTemplateEntity;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplateEntity, Long> {
  List<NotificationTemplateEntity> findByOrganizationIdOrderByUpdatedAtDesc(String organizationId);
  Optional<NotificationTemplateEntity> findByOrganizationIdAndTemplateId(String organizationId, String templateId);
}