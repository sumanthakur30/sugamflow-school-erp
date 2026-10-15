package com.sugamflow.school.student.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.RequestCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SchoolPrivacyServiceTest {

  @Mock private SchoolPrivacyNoticeRepository notices;
  @Mock private SchoolPrivacyConsentRepository consents;
  @Mock private SchoolPrivacyRequestRepository requests;
  @Mock private SchoolPrivacyAuditRepository audits;

  private SchoolPrivacyService service;
  private final List<SchoolPrivacyConsentEntity> storedConsents = new ArrayList<>();
  private final List<SchoolPrivacyRequestEntity> storedRequests = new ArrayList<>();

  @BeforeEach
  void setUp() {
    service = new SchoolPrivacyService(notices, consents, requests, audits);
    TenantContext.set(new TenantScope("org-1", "branch-1", "session-1", "user-1", "ADMIN"));
    lenient()
        .when(notices.findByNoticeCodeAndVersion(SchoolPrivacyService.NOTICE_CODE, SchoolPrivacyService.NOTICE_VERSION))
        .thenReturn(Optional.of(existingNotice()));
    lenient()
        .when(consents.findByOrganizationIdAndSubjectRefAndPurposeCode(any(), any(), any()))
        .thenAnswer(invocation -> storedConsents.stream()
            .filter(row -> invocation.getArgument(0).equals(row.getOrganizationId()))
            .filter(row -> invocation.getArgument(1).equals(row.getSubjectRef()))
            .filter(row -> invocation.getArgument(2).equals(row.getPurposeCode()))
            .findFirst());
    lenient().when(requests.existsByOrganizationIdAndDataCategoryAndSubjectLabelAndRequestTypeAndStatusIn(
            any(), any(), any(), any(), any()))
        .thenReturn(false);
    lenient().when(audits.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    lenient().when(consents.save(any())).thenAnswer(invocation -> {
      SchoolPrivacyConsentEntity row = invocation.getArgument(0);
      storedConsents.removeIf(existing -> existing.getPurposeCode().equals(row.getPurposeCode()));
      storedConsents.add(row);
      return row;
    });
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void optionalUpdatesStayOffUntilGrantedAndTurnOffAfterWithdraw() {
    assertFalse(service.optionalUpdatesAllowed());
    service.setConsent(SchoolPrivacyService.OPTIONAL_UPDATES, "GRANT");
    assertTrue(service.optionalUpdatesAllowed());
    service.setConsent(SchoolPrivacyService.OPTIONAL_UPDATES, "WITHDRAW");
    assertFalse(service.optionalUpdatesAllowed());
  }

  @Test
  void schoolOperationsCannotBeWithdrawn() {
    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.setConsent(SchoolPrivacyService.SCHOOL_OPERATIONS, "WITHDRAW"));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void completingErasureOnlyRecordsTheRequest() {
    when(requests.save(any())).thenAnswer(invocation -> {
      SchoolPrivacyRequestEntity row = invocation.getArgument(0);
      storedRequests.clear();
      storedRequests.add(row);
      return row;
    });
    when(requests.findById(any())).thenAnswer(invocation -> storedRequests.stream()
        .filter(row -> invocation.getArgument(0).equals(row.getId()))
        .findFirst());

    SchoolPrivacyViews.RequestView created =
        service.submit(new RequestCommand("STUDENT", "ADM-1042", "ERASURE", "Parent asked to review the file."));
    SchoolPrivacyViews.RequestView completed = service.complete(created.id(), null);

    assertEquals("COMPLETED", completed.status());
    assertEquals("RECORDED_ONLY", completed.actionTaken());
    assertEquals(1, storedRequests.size());
  }

  @Test
  void missingNoticeDoesNotTryToInsertOne() {
    when(notices.findByNoticeCodeAndVersion(SchoolPrivacyService.NOTICE_CODE, SchoolPrivacyService.NOTICE_VERSION))
        .thenReturn(Optional.empty());

    ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.currentNotice());
    assertEquals(503, ex.getStatusCode().value());
  }

  @Test
  void openDuplicateRequestIsRejected() {
    when(requests.existsByOrganizationIdAndDataCategoryAndSubjectLabelAndRequestTypeAndStatusIn(
            any(), any(), any(), any(), any()))
        .thenReturn(true);

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> service.submit(new RequestCommand("STUDENT", "ADM-1042", "ACCESS", "Again")));
    assertEquals(409, ex.getStatusCode().value());
  }

  @Test
  void rejectingARequestDoesNotDeleteRecords() {
    when(requests.save(any())).thenAnswer(invocation -> {
      SchoolPrivacyRequestEntity row = invocation.getArgument(0);
      storedRequests.clear();
      storedRequests.add(row);
      return row;
    });
    when(requests.findById(any())).thenAnswer(invocation -> storedRequests.stream()
        .filter(row -> invocation.getArgument(0).equals(row.getId()))
        .findFirst());

    SchoolPrivacyViews.RequestView created =
        service.submit(new RequestCommand("PARENT", "Parent A-2", "ERASURE", "Please review."));
    SchoolPrivacyViews.RequestView rejected = service.updateStatus(created.id(), "REJECTED", "Kept for school records.");

    assertEquals("REJECTED", rejected.status());
    assertEquals("REJECTED", rejected.actionTaken());
  }

  private static SchoolPrivacyNoticeEntity existingNotice() {
    SchoolPrivacyNoticeEntity notice = new SchoolPrivacyNoticeEntity();
    notice.setId(UUID.randomUUID());
    notice.setNoticeCode(SchoolPrivacyService.NOTICE_CODE);
    notice.setVersion(SchoolPrivacyService.NOTICE_VERSION);
    notice.setTitle("Student and parent information");
    notice.setBody("notice");
    return notice;
  }
}
