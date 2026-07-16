package com.sugamflow.school.notification.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "notification_template")
public class NotificationTemplateEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "organization_id", nullable = false, length = 64)
	private String organizationId;
	@Column(name = "template_id", nullable = false, length = 128)
	private String templateId;
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> payload = new LinkedHashMap<>();
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getOrganizationId() {
		return organizationId;
	}

	public void setOrganizationId(String v) {
		organizationId = v;
	}

	public String getTemplateId() {
		return templateId;
	}

	public void setTemplateId(String v) {
		templateId = v;
	}

	public Map<String, Object> getPayload() {
		return payload;
	}

	public void setPayload(Map<String, Object> v) {
		payload = v;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant v) {
		updatedAt = v;
	}
}