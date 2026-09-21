package com.sugamflow.school.library.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.library.persistence.entity.LibraryCirculationEntity;
import com.sugamflow.school.library.persistence.entity.LibraryRecordEntity;
import com.sugamflow.school.library.persistence.repo.LibraryCirculationRepository;
import com.sugamflow.school.library.persistence.repo.LibraryRecordRepository;
import com.sugamflow.school.library.web.LibraryException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LibraryClearanceService {

  private final LibraryRecordRepository repository;
  private final LibraryCirculationRepository circulations;

  public LibraryClearanceService(
      LibraryRecordRepository repository, LibraryCirculationRepository circulations) {
    this.repository = repository;
    this.circulations = circulations;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> snapshot(String admissionNo) {
    TenantScope scope = TenantContext.require();
    if (admissionNo == null || admissionNo.isBlank()) {
      throw new LibraryException("VALIDATION", "admissionNo is required");
    }
    String ref = admissionNo.trim();
    List<Map<String, Object>> items = new ArrayList<>();
    int outstandingBooks = 0;
    int maxOverdueDays = 0;
    LocalDate today = LocalDate.now();

    // Deep ledger (preferred)
    for (LibraryCirculationEntity c :
        circulations.findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
            scope.organizationId(), ref, "ISSUED")) {
      outstandingBooks++;
      int overdueDays = overdueFromInstant(c.getDueAt(), today);
      maxOverdueDays = Math.max(maxOverdueDays, overdueDays);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("source", "CIRCULATION");
      item.put("recordId", c.getId().toString());
      item.put("bookId", c.getBookId().toString());
      item.put("bookTitle", "");
      item.put("dueDate", c.getDueAt() == null ? "" : c.getDueAt().toString());
      item.put("overdueDays", overdueDays);
      items.add(item);
    }

    // Legacy workflow records (until fully migrated)
    for (LibraryRecordEntity entity : scopedRecords(scope)) {
      Map<String, Object> answers = entity.getAnswers() != null ? entity.getAnswers() : Map.of();
      if (!ref.equalsIgnoreCase(stringOr(answers.get("admissionNo"), ""))) {
        continue;
      }
      if (!"APPROVED".equalsIgnoreCase(entity.getStatus())) {
        continue;
      }
      if (isReturned(answers)) {
        continue;
      }
      outstandingBooks++;
      int overdueDays = overdueDays(answers, today);
      maxOverdueDays = Math.max(maxOverdueDays, overdueDays);
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("source", "WORKFLOW");
      item.put("recordId", entity.getId().toString());
      item.put("bookId", stringOr(answers.get("bookId"), ""));
      item.put("bookTitle", stringOr(answers.get("bookTitle"), ""));
      item.put("dueDate", stringOr(answers.get("dueDate"), ""));
      item.put("overdueDays", overdueDays);
      items.add(item);
    }

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("admissionNo", ref);
    out.put("outstandingBooks", outstandingBooks);
    out.put("maxOverdueDays", maxOverdueDays);
    out.put("hasOutstanding", outstandingBooks > 0);
    out.put("items", items);
    return out;
  }

  private List<LibraryRecordEntity> scopedRecords(TenantScope scope) {
    if (scope.branchId() != null
        && !scope.branchId().isBlank()
        && scope.academicSessionId() != null
        && !scope.academicSessionId().isBlank()) {
      return repository.findByOrganizationIdAndBranchIdAndAcademicSessionIdOrderByUpdatedAtDesc(
          scope.organizationId(), scope.branchId(), scope.academicSessionId());
    }
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(scope.organizationId());
  }

  private static boolean isReturned(Map<String, Object> answers) {
    if (Boolean.TRUE.equals(answers.get("returned"))) {
      return true;
    }
    String returnDate = stringOr(answers.get("returnDate"), "");
    return !returnDate.isBlank();
  }

  private static int overdueDays(Map<String, Object> answers, LocalDate today) {
    String due = stringOr(answers.get("dueDate"), "");
    if (due.isBlank()) {
      return 0;
    }
    try {
      LocalDate dueDate = LocalDate.parse(due);
      if (!dueDate.isBefore(today)) {
        return 0;
      }
      return (int) ChronoUnit.DAYS.between(dueDate, today);
    } catch (Exception ex) {
      return 0;
    }
  }

  private static int overdueFromInstant(Instant dueAt, LocalDate today) {
    if (dueAt == null) {
      return 0;
    }
    LocalDate dueDate = LocalDate.ofInstant(dueAt, ZoneOffset.UTC);
    if (!dueDate.isBefore(today)) {
      return 0;
    }
    return (int) ChronoUnit.DAYS.between(dueDate, today);
  }

  private static String stringOr(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() ? fallback : s;
  }
}
