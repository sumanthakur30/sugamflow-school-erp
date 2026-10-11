package com.sugamflow.school.student.privacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "school_privacy_notice")
public class SchoolPrivacyNoticeEntity {

  @Id private UUID id;

  @Column(name = "notice_code", nullable = false, length = 80)
  private String noticeCode;

  @Column(nullable = false, length = 40)
  private String version;

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String body;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() { return id; }
  public void setId(UUID id) { this.id = id; }
  public String getNoticeCode() { return noticeCode; }
  public void setNoticeCode(String noticeCode) { this.noticeCode = noticeCode; }
  public String getVersion() { return version; }
  public void setVersion(String version) { this.version = version; }
  public String getTitle() { return title; }
  public void setTitle(String title) { this.title = title; }
  public String getBody() { return body; }
  public void setBody(String body) { this.body = body; }
  public Instant getCreatedAt() { return createdAt; }
  public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
