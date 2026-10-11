package com.sugamflow.school.student.privacy;

import java.time.Instant;
import java.util.UUID;

public final class SchoolPrivacyViews {

  private SchoolPrivacyViews() {}

  public record NoticeView(String noticeCode, String version, String title, String body) {}

  public record PurposeView(
      String purposeCode,
      String legalGround,
      String description,
      boolean withdrawable,
      boolean allowed) {}

  public record ConsentView(String purposeCode, String status, Instant grantedAt, Instant withdrawnAt) {}

  public record RequestView(
      UUID id,
      String requestNumber,
      String dataCategory,
      String subjectLabel,
      String requestType,
      String status,
      String detail,
      String actionTaken,
      String responseNote,
      Instant createdAt) {}

  public record ConsentCommand(String purposeCode, String action) {}

  public record RequestCommand(String dataCategory, String subjectLabel, String requestType, String detail) {}

  public record CompleteCommand(String note) {}
}
