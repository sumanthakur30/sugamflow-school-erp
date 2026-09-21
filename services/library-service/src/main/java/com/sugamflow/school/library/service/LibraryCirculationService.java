package com.sugamflow.school.library.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.library.persistence.entity.LibraryBookEntity;
import com.sugamflow.school.library.persistence.entity.LibraryCirculationEntity;
import com.sugamflow.school.library.persistence.repo.LibraryBookRepository;
import com.sugamflow.school.library.persistence.repo.LibraryCirculationRepository;
import com.sugamflow.school.library.web.LibraryException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Deep library — catalog + issue/return ledger (replaces generic JSON-only circulation). */
@Service
public class LibraryCirculationService {

  private final LibraryBookRepository books;
  private final LibraryCirculationRepository circulations;

  public LibraryCirculationService(
      LibraryBookRepository books, LibraryCirculationRepository circulations) {
    this.books = books;
    this.circulations = circulations;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listBooks() {
    TenantScope scope = TenantContext.require();
    return books.findByOrganizationIdOrderByTitleAsc(scope.organizationId()).stream()
        .map(this::bookDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> upsertBook(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String title = str(body.get("title"));
    if (title == null) {
      throw new LibraryException("VALIDATION", "title is required");
    }
    UUID id = parseUuid(body.get("id"));
    LibraryBookEntity book =
        id == null
            ? null
            : books.findByIdAndOrganizationId(id, scope.organizationId()).orElse(null);
    if (book == null) {
      book = new LibraryBookEntity();
      book.setId(UUID.randomUUID());
      book.setOrganizationId(scope.organizationId());
      book.setBranchId(scope.branchId());
      book.setCreatedAt(Instant.now());
      book.setCopiesTotal(1);
      book.setCopiesAvailable(1);
    }
    book.setTitle(title);
    book.setAuthor(str(body.get("author")));
    book.setIsbn(str(body.get("isbn")));
    book.setCategoryKey(strOr(body.get("categoryKey"), "general"));
    if (body.get("copiesTotal") instanceof Number n) {
      int total = Math.max(1, n.intValue());
      int issued = book.getCopiesTotal() - book.getCopiesAvailable();
      book.setCopiesTotal(total);
      book.setCopiesAvailable(Math.max(0, total - Math.max(0, issued)));
    }
    book.setUpdatedAt(Instant.now());
    return bookDto(books.save(book));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> openIssues() {
    TenantScope scope = TenantContext.require();
    return circulations
        .findByOrganizationIdAndStatusOrderByIssuedAtDesc(scope.organizationId(), "ISSUED")
        .stream()
        .map(this::circDto)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> openIssuesForAdmission(String admissionNo) {
    TenantScope scope = TenantContext.require();
    if (admissionNo == null || admissionNo.isBlank()) {
      return List.of();
    }
    return circulations
        .findByOrganizationIdAndAdmissionNoIgnoreCaseAndStatus(
            scope.organizationId(), admissionNo.trim(), "ISSUED")
        .stream()
        .map(this::circDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> issue(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    UUID bookId = parseUuid(body.get("bookId"));
    if (bookId == null) {
      throw new LibraryException("VALIDATION", "bookId is required");
    }
    LibraryBookEntity book =
        books
            .findByIdAndOrganizationId(bookId, scope.organizationId())
            .orElseThrow(() -> new LibraryException("NOT_FOUND", "Book not found"));
    if (book.getCopiesAvailable() <= 0) {
      throw new LibraryException("UNAVAILABLE", "No copies available");
    }
    String admission = str(body.get("admissionNo"));
    if (admission == null) {
      throw new LibraryException("VALIDATION", "admissionNo is required");
    }
    int loanDays = body.get("loanDays") instanceof Number n ? Math.max(1, n.intValue()) : 14;
    LibraryCirculationEntity c = new LibraryCirculationEntity();
    c.setId(UUID.randomUUID());
    c.setOrganizationId(scope.organizationId());
    c.setBranchId(scope.branchId());
    c.setBookId(book.getId());
    c.setStudentId(parseUuid(body.get("studentId")));
    c.setAdmissionNo(admission);
    c.setStudentName(str(body.get("studentName")));
    c.setClassSection(str(body.get("classSection")));
    c.setStatus("ISSUED");
    c.setIssuedAt(Instant.now());
    c.setDueAt(Instant.now().plus(loanDays, ChronoUnit.DAYS));
    c.setCreatedBy(scope.userId());
    c.setCreatedAt(Instant.now());
    c.setUpdatedAt(Instant.now());
    book.setCopiesAvailable(book.getCopiesAvailable() - 1);
    book.setUpdatedAt(Instant.now());
    books.save(book);
    return circDto(circulations.save(c));
  }

  @Transactional
  public Map<String, Object> returnBook(UUID circulationId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    LibraryCirculationEntity c =
        circulations
            .findByIdAndOrganizationId(circulationId, scope.organizationId())
            .orElseThrow(() -> new LibraryException("NOT_FOUND", "Issue not found"));
    if (!"ISSUED".equals(c.getStatus())) {
      throw new LibraryException("CONFLICT", "Already returned");
    }
    LibraryBookEntity book =
        books
            .findByIdAndOrganizationId(c.getBookId(), scope.organizationId())
            .orElseThrow(() -> new LibraryException("NOT_FOUND", "Book not found"));
    c.setStatus("RETURNED");
    c.setReturnedAt(Instant.now());
    BigDecimal fine = BigDecimal.ZERO;
    if (c.getDueAt() != null && Instant.now().isAfter(c.getDueAt())) {
      long days = ChronoUnit.DAYS.between(c.getDueAt(), Instant.now());
      fine = BigDecimal.valueOf(Math.max(1, days)); // ₹1/day placeholder; ops fine policy next
    }
    if (body != null && body.get("fineAmount") instanceof Number n) {
      fine = BigDecimal.valueOf(n.doubleValue());
    }
    c.setFineAmount(fine);
    c.setUpdatedAt(Instant.now());
    book.setCopiesAvailable(book.getCopiesAvailable() + 1);
    book.setUpdatedAt(Instant.now());
    books.save(book);
    return circDto(circulations.save(c));
  }

  private Map<String, Object> bookDto(LibraryBookEntity b) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", b.getId().toString());
    m.put("isbn", b.getIsbn());
    m.put("title", b.getTitle());
    m.put("author", b.getAuthor());
    m.put("categoryKey", b.getCategoryKey());
    m.put("copiesTotal", b.getCopiesTotal());
    m.put("copiesAvailable", b.getCopiesAvailable());
    m.put("status", b.getStatus());
    return m;
  }

  private Map<String, Object> circDto(LibraryCirculationEntity c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", c.getId().toString());
    m.put("bookId", c.getBookId().toString());
    m.put("studentId", c.getStudentId() == null ? null : c.getStudentId().toString());
    m.put("admissionNo", c.getAdmissionNo());
    m.put("studentName", c.getStudentName());
    m.put("classSection", c.getClassSection());
    m.put("status", c.getStatus());
    m.put("issuedAt", c.getIssuedAt().toString());
    m.put("dueAt", c.getDueAt() == null ? null : c.getDueAt().toString());
    m.put("returnedAt", c.getReturnedAt() == null ? null : c.getReturnedAt().toString());
    m.put("fineAmount", c.getFineAmount());
    books
        .findByIdAndOrganizationId(c.getBookId(), c.getOrganizationId())
        .ifPresent(
            book -> {
              m.put("bookTitle", book.getTitle());
              m.put("bookAuthor", book.getAuthor());
            });
    return m;
  }

  private static UUID parseUuid(Object v) {
    if (v == null) return null;
    try {
      return UUID.fromString(String.valueOf(v));
    } catch (Exception ex) {
      return null;
    }
  }

  private static String str(Object v) {
    if (v == null) return null;
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
  }

  private static String strOr(Object v, String fallback) {
    String s = str(v);
    return s == null ? fallback : s;
  }
}
