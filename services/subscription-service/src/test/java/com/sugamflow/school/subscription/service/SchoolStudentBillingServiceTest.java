package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.billing.BillableStudentCounter;
import com.sugamflow.school.subscription.billing.PerStudentInvoiceCommand;
import com.sugamflow.school.subscription.billing.PerStudentInvoiceIssuer;
import com.sugamflow.school.subscription.billing.SchoolPackageRate;
import com.sugamflow.school.subscription.billing.SchoolPackageRateSource;
import com.sugamflow.school.subscription.billing.TaxRuleView;
import com.sugamflow.school.subscription.persistence.entity.SchoolBillingContractEntity;
import com.sugamflow.school.subscription.persistence.entity.SchoolBillingRunEntity;
import com.sugamflow.school.subscription.persistence.repo.SchoolBillingContractRepository;
import com.sugamflow.school.subscription.persistence.repo.SchoolBillingRunRepository;
import com.sugamflow.school.subscription.persistence.repo.SubscriptionPaymentRepository;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchoolStudentBillingServiceTest {

  private static final String ORG = "SCH-800";
  private static final YearMonth OCTOBER = YearMonth.of(2026, 10);

  @Mock private SchoolBillingContractRepository contracts;
  @Mock private SchoolBillingRunRepository runs;
  @Mock private SubscriptionPaymentRepository payments;
  @Mock private BillableStudentCounter students;
  @Mock private SchoolPackageRateSource packageRates;
  @Mock private PerStudentInvoiceIssuer invoices;
  @Mock private SubscriptionLifecycleService lifecycle;

  private final Map<String, SchoolBillingRunEntity> saved = new HashMap<>();
  private SchoolStudentBillingService service;
  private SchoolBillingContractEntity contract;

  @BeforeEach
  void setUp() {
    service =
        new SchoolStudentBillingService(
            contracts, runs, payments, students, packageRates, invoices, lifecycle);
    contract = new SchoolBillingContractEntity();
    contract.setOrganizationId(ORG);
    contract.setActive(true);
    contract.setPerStudentRateMinor(3_000L);
    contract.setBaseFeeMinor(0);
    contract.setDiscountMinor(0);
    contract.setBuyerStateCode("09");
    contract.setSellerStateCode("09");
    when(contracts.findById(ORG)).thenReturn(Optional.of(contract));
    when(lifecycle.getLicense(ORG)).thenReturn(Map.of("resolvedStatus", "ACTIVE"));
    when(packageRates.openSchoolRate()).thenReturn(Optional.of(new SchoolPackageRate(9L, 2_500L, false)));
    when(invoices.defaultTaxRule()).thenReturn(new TaxRuleView("tax-in-gst18", 900, 900, 1800));
    when(runs.findByOrganizationIdAndPeriodKeyAndObligationType(any(), any(), any()))
        .thenAnswer(invocation -> Optional.ofNullable(saved.get(invocation.getArgument(1))));
    when(runs.save(any()))
        .thenAnswer(
            invocation -> {
              SchoolBillingRunEntity entity = invocation.getArgument(0);
              if (entity.getId() == null) {
                entity.setId((long) saved.size() + 1);
              }
              saved.put(entity.getPeriodKey(), entity);
              return entity;
            });
    when(payments.sumSuccessfulForInvoice(any(Long.class))).thenReturn(0L);
  }

  @Test
  void billsEightHundredStudentsFromTheContractedRateNotThePackageSeed() {
    when(students.countActive(ORG)).thenReturn(800);
    when(invoices.issuePerStudent(any())).thenReturn(Map.of("id", 77L, "status", "ISSUED"));

    Map<String, Object> result = service.billOrganization(ORG, OCTOBER);

    ArgumentCaptor<PerStudentInvoiceCommand> command = ArgumentCaptor.forClass(PerStudentInvoiceCommand.class);
    verify(invoices).issuePerStudent(command.capture());
    assertEquals(800, command.getValue().quantity());
    assertEquals(3_000L, command.getValue().unitAmountMinor());
    assertEquals(2_400_000L, command.getValue().taxableMinor());
    assertEquals(216_000L, command.getValue().cgstMinor());
    assertEquals(2_832_000L, command.getValue().totalMinor());
    assertEquals("ISSUED", result.get("jobStatus"));
    assertEquals(0L, result.get("collectedMinor"));
    assertEquals(800, result.get("billableCount"));
  }

  @Test
  void retryDoesNotCreateASecondInvoiceOrRewriteTheSnapshot() {
    when(students.countActive(ORG)).thenReturn(800, 900);
    when(invoices.issuePerStudent(any())).thenReturn(Map.of("id", 77L, "status", "ISSUED"));

    service.billOrganization(ORG, OCTOBER);
    contract.setPerStudentRateMinor(9_999L);
    Map<String, Object> again = service.billOrganization(ORG, OCTOBER);

    verify(invoices, times(1)).issuePerStudent(any());
    verify(students, times(1)).countActive(ORG);
    assertEquals(800, again.get("billableCount"));
    assertEquals(3_000L, again.get("unitAmountMinor"));
    assertEquals(77L, ((Number) again.get("invoiceId")).longValue());
  }

  @Test
  void laterPeriodUsesTheNewRateAndLeavesTheIssuedPeriodAlone() {
    when(students.countActive(ORG)).thenReturn(800);
    when(invoices.issuePerStudent(any())).thenReturn(Map.of("id", 77L, "status", "ISSUED"));
    service.billOrganization(ORG, OCTOBER);

    contract.setPerStudentRateMinor(4_000L);
    when(invoices.issuePerStudent(any())).thenReturn(Map.of("id", 78L, "status", "ISSUED"));
    service.billOrganization(ORG, YearMonth.of(2026, 11));

    assertEquals(3_000L, saved.get("2026-10").getUnitAmountMinor());
    assertEquals(2_400_000L, saved.get("2026-10").getTaxableMinor());
    assertEquals(4_000L, saved.get("2026-11").getUnitAmountMinor());
    assertEquals(3_200_000L, saved.get("2026-11").getTaxableMinor());
  }

  @Test
  void failedRunCanBeRetriedWithoutASecondSuccessfulInvoice() {
    when(students.countActive(ORG)).thenReturn(800);
    AtomicInteger attempts = new AtomicInteger();
    when(invoices.issuePerStudent(any()))
        .thenAnswer(
            invocation -> {
              if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("student service down");
              }
              return Map.of("id", 77L, "status", "ISSUED");
            });

    Map<String, Object> failed = service.billOrganization(ORG, OCTOBER);
    Map<String, Object> retried = service.billOrganization(ORG, OCTOBER);

    assertEquals("FAILED", failed.get("jobStatus"));
    assertEquals("ISSUED", retried.get("jobStatus"));
    verify(invoices, times(2)).issuePerStudent(any());
    assertEquals(1, saved.size());
    assertEquals(77L, saved.get("2026-10").getInvoiceId());
  }

  @Test
  void unpaidIssuedInvoiceIsNotReportedAsCollected() {
    when(students.countActive(ORG)).thenReturn(800);
    when(invoices.issuePerStudent(any())).thenReturn(Map.of("id", 77L, "status", "ISSUED"));
    service.billOrganization(ORG, OCTOBER);
    when(runs.findByPeriodKeyAndObligationTypeOrderByOrganizationIdAsc("2026-10", "SCHOOL_PER_STUDENT"))
        .thenReturn(List.of(saved.get("2026-10")));
    when(contracts.countByActiveTrueAndUnitModel("PER_STUDENT")).thenReturn(1L);

    Map<String, Object> summary = service.summary(OCTOBER);

    assertEquals(0L, summary.get("collectedMinor"));
    assertEquals(2_832_000L, summary.get("outstandingMinor"));
    assertEquals(2_400_000L, summary.get("invoicedBaseMinor"));
    assertEquals(800, summary.get("billableStudentCount"));
    verify(payments, never()).findByOrganizationIdOrderByCreatedAtDesc(any());
  }
}
