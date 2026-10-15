package com.sugamflow.school.student.privacy;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.CompleteCommand;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.ConsentView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.NoticeView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.PurposeView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.RequestCommand;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.RequestView;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SchoolPrivacyService {

  public static final String NOTICE_CODE = "SCHOOL_STUDENT";
  public static final String NOTICE_VERSION = "2026.10";
  public static final String SCHOOL_OPERATIONS = "SCHOOL_OPERATIONS";
  public static final String OPTIONAL_UPDATES = "OPTIONAL_SCHOOL_UPDATES";

  private static final Set<String> REQUEST_TYPES =
      Set.of("ACCESS", "CORRECTION", "ERASURE", "GRIEVANCE");
  private static final Set<String> CATEGORIES = Set.of("STUDENT", "PARENT");
  private static final Set<String> ADMIN_ROLES =
      Set.of("SHOP_OWNER", "SUPER_ADMIN", "ADMIN", "PRINCIPAL", "OWNER");
  private static final Set<String> OPEN_STATUSES = Set.of("OPEN", "IN_REVIEW");
  private static final Set<String> NEXT_STATUSES =
      Set.of("IN_REVIEW", "COMPLETED", "REJECTED", "CANCELLED");
  private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ./_-]{0,79}");

  private final SchoolPrivacyNoticeRepository notices;
  private final SchoolPrivacyConsentRepository consents;
  private final SchoolPrivacyRequestRepository requests;
  private final SchoolPrivacyAuditRepository audits;

  public SchoolPrivacyService(
      SchoolPrivacyNoticeRepository notices,
      SchoolPrivacyConsentRepository consents,
      SchoolPrivacyRequestRepository requests,
      SchoolPrivacyAuditRepository audits) {
    this.notices = notices;
    this.consents = consents;
    this.requests = requests;
    this.audits = audits;
  }

  @Transactional
  public NoticeView currentNotice() {
    SchoolPrivacyNoticeEntity notice = ensureNotice();
    return new NoticeView(notice.getNoticeCode(), notice.getVersion(), notice.getTitle(), notice.getBody());
  }

  @Transactional
  public List<PurposeView> purposes() {
    ensureNotice();
    TenantScope scope = TenantContext.require();
    return List.of(
        new PurposeView(
            SCHOOL_OPERATIONS,
            "OTHER",
            "Admission, attendance, fees, exams, and notices required to run the school.",
            false,
            true),
        new PurposeView(
            OPTIONAL_UPDATES,
            "CONSENT",
            "Optional school updates that are not required to run the school.",
            true,
            optionalAllowed(scope)));
  }

  @Transactional
  public boolean optionalUpdatesAllowed() {
    return optionalAllowed(TenantContext.require());
  }

  @Transactional
  public ConsentView setConsent(String purposeCode, String action) {
    ensureNotice();
    TenantScope scope = TenantContext.require();
    String purpose = required(purposeCode, "purposeCode").toUpperCase();
    String decision = required(action, "action").toUpperCase();
    if (SCHOOL_OPERATIONS.equals(purpose)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "School operations cannot be withdrawn from this page.");
    }
    if (!OPTIONAL_UPDATES.equals(purpose)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown purpose.");
    }
    if (!decision.equals("GRANT") && !decision.equals("WITHDRAW")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action must be GRANT or WITHDRAW.");
    }
    Instant now = Instant.now();
    SchoolPrivacyConsentEntity row =
        consents
            .findByOrganizationIdAndSubjectRefAndPurposeCode(
                scope.organizationId(), subjectRef(scope), purpose)
            .orElseGet(() -> newConsent(scope, purpose, now));
    if ("GRANT".equals(decision)) {
      row.setStatus("GRANTED");
      row.setGrantedAt(now);
      row.setWithdrawnAt(null);
    } else {
      row.setStatus("WITHDRAWN");
      row.setWithdrawnAt(now);
    }
    row.setNoticeCode(NOTICE_CODE);
    row.setNoticeVersion(NOTICE_VERSION);
    SchoolPrivacyConsentEntity saved = consents.save(row);
    audit(scope, "GRANT".equals(decision) ? "CONSENT_GRANTED" : "CONSENT_WITHDRAWN", purpose, null);
    return new ConsentView(
        saved.getPurposeCode(), saved.getStatus(), saved.getGrantedAt(), saved.getWithdrawnAt());
  }

  @Transactional
  public RequestView submit(RequestCommand command) {
    ensureNotice();
    TenantScope scope = TenantContext.require();
    if (command == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
    }
    String category = required(command.dataCategory(), "dataCategory").toUpperCase();
    String type = required(command.requestType(), "requestType").toUpperCase();
    if (!CATEGORIES.contains(category) || !REQUEST_TYPES.contains(type)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown category or request type.");
    }
    String label = required(command.subjectLabel(), "subjectLabel");
    if (!REFERENCE.matcher(label).matches()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Use an admission number or a short parent reference. Do not paste a document.");
    }
    if (requests.existsByOrganizationIdAndDataCategoryAndSubjectLabelAndRequestTypeAndStatusIn(
        scope.organizationId(), category, label, type, OPEN_STATUSES)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "An open request already exists for this reference and type.");
    }
    Instant now = Instant.now();
    SchoolPrivacyRequestEntity row = new SchoolPrivacyRequestEntity();
    row.setId(UUID.randomUUID());
    row.setRequestNumber("SPR-" + row.getId().toString().substring(0, 8).toUpperCase());
    row.setOrganizationId(scope.organizationId());
    row.setSubjectRef(subjectRef(scope));
    row.setDataCategory(category);
    row.setSubjectLabel(label);
    row.setRequestType(type);
    row.setStatus("OPEN");
    row.setDetail(clip(command.detail(), 2000));
    row.setActionTaken("NONE");
    row.setCreatedAt(now);
    row.setUpdatedAt(now);
    SchoolPrivacyRequestEntity saved = requests.save(row);
    audit(scope, "REQUEST_CREATED", saved.getRequestNumber(), category + " " + type);
    return toView(saved);
  }

  @Transactional(readOnly = true)
  public List<RequestView> mine() {
    TenantScope scope = TenantContext.require();
    return requests
        .findByOrganizationIdAndSubjectRefOrderByCreatedAtDesc(scope.organizationId(), subjectRef(scope))
        .stream()
        .map(this::toView)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<RequestView> organization() {
    TenantScope scope = requireAdmin();
    return requests.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId()).stream()
        .map(this::toView)
        .toList();
  }

  @Transactional
  public RequestView complete(UUID id, CompleteCommand command) {
    return updateStatus(id, "COMPLETED", command == null ? null : command.note());
  }

  @Transactional
  public RequestView updateStatus(UUID id, String status, String note) {
    TenantScope scope = requireAdmin();
    String next = required(status, "status").toUpperCase();
    if (!NEXT_STATUSES.contains(next)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown request status.");
    }
    SchoolPrivacyRequestEntity row =
        requests
            .findById(id)
            .filter(found -> scope.organizationId().equals(found.getOrganizationId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found."));
    if (!OPEN_STATUSES.contains(row.getStatus())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "This request is already closed.");
    }
    row.setStatus(next);
    row.setActionTaken("COMPLETED".equals(next) ? "RECORDED_ONLY" : next);
    row.setResponseNote(clip(note, 2000));
    row.setUpdatedAt(Instant.now());
    SchoolPrivacyRequestEntity saved = requests.save(row);
    audit(scope, "REQUEST_" + next, saved.getRequestNumber(), saved.getRequestType());
    return toView(saved);
  }

  private boolean optionalAllowed(TenantScope scope) {
    return consents
        .findByOrganizationIdAndSubjectRefAndPurposeCode(
            scope.organizationId(), subjectRef(scope), OPTIONAL_UPDATES)
        .map(row -> "GRANTED".equals(row.getStatus()))
        .orElse(false);
  }

  private SchoolPrivacyNoticeEntity ensureNotice() {
    return notices
        .findByNoticeCodeAndVersion(NOTICE_CODE, NOTICE_VERSION)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "The school privacy notice is not installed yet."));
  }

  private void audit(TenantScope scope, String action, String targetRef, String detail) {
    SchoolPrivacyAuditEntity event = new SchoolPrivacyAuditEntity();
    event.setId(UUID.randomUUID());
    event.setOrganizationId(scope.organizationId());
    event.setActor(subjectRef(scope));
    event.setAction(action);
    event.setTargetRef(clip(targetRef, 80));
    event.setDetail(clip(detail, 500));
    event.setCreatedAt(Instant.now());
    audits.save(event);
  }

  private SchoolPrivacyConsentEntity newConsent(TenantScope scope, String purpose, Instant now) {
    SchoolPrivacyConsentEntity row = new SchoolPrivacyConsentEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(scope.organizationId());
    row.setSubjectRef(subjectRef(scope));
    row.setPurposeCode(purpose);
    row.setLegalGround("CONSENT");
    row.setChannel("SCHOOL_ADMIN");
    row.setCreatedAt(now);
    return row;
  }

  private TenantScope requireAdmin() {
    TenantScope scope = TenantContext.require();
    String role = scope.roleCode() == null ? "" : scope.roleCode().trim().toUpperCase();
    if (!ADMIN_ROLES.contains(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "School privacy requests are limited to school admins.");
    }
    return scope;
  }

  private static String subjectRef(TenantScope scope) {
    String userId = scope.userId();
    if (userId == null || userId.isBlank()) {
      return "session";
    }
    return userId.length() > 128 ? userId.substring(0, 128) : userId;
  }

  private static String required(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
    }
    return value.trim();
  }

  private static String clip(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
  }

  private RequestView toView(SchoolPrivacyRequestEntity row) {
    return new RequestView(
        row.getId(),
        row.getRequestNumber(),
        row.getDataCategory(),
        row.getSubjectLabel(),
        row.getRequestType(),
        row.getStatus(),
        row.getDetail(),
        row.getActionTaken(),
        row.getResponseNote(),
        row.getCreatedAt());
  }
}
