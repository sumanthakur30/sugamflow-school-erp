package com.sugamflow.school.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sugamflow.school.subscription.persistence.entity.CreditPolicyEntity;
import com.sugamflow.school.subscription.persistence.entity.CreditWalletEntity;
import com.sugamflow.school.subscription.persistence.entity.TenantSubscriptionEntity;
import com.sugamflow.school.subscription.persistence.repo.CreditLedgerRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditPeriodRunRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditPolicyRepository;
import com.sugamflow.school.subscription.persistence.repo.CreditWalletRepository;
import com.sugamflow.school.subscription.persistence.repo.TenantSubscriptionRepository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CreditMonetizationServiceTest {

  @Mock private CreditPolicyRepository policyRepository;
  @Mock private CreditPeriodRunRepository periodRunRepository;
  @Mock private CreditWalletRepository walletRepository;
  @Mock private CreditLedgerRepository ledgerRepository;
  @Mock private TenantSubscriptionRepository tenantSubscriptionRepository;

  private CreditMonetizationService service;

  @BeforeEach
  void setUp() {
    service =
        new CreditMonetizationService(
            policyRepository,
            periodRunRepository,
            walletRepository,
            ledgerRepository,
            tenantSubscriptionRepository);
  }

  @Test
  void carryForwardAppliesPctCapAndExpiresRemainder() {
    CreditPolicyEntity policy = aiPolicy();
    when(policyRepository.findById("ai_credits")).thenReturn(Optional.of(policy));
    when(periodRunRepository.findByOrganizationIdAndMeterCodeAndPeriodKey("ORG1", "ai_credits", "2026-07"))
        .thenReturn(Optional.empty());

    CreditWalletEntity wallet = wallet("ORG1", "ai_credits", 1000);
    when(walletRepository.findById(any())).thenReturn(Optional.of(wallet));
    when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(ledgerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    AtomicLong ids = new AtomicLong(1);
    when(periodRunRepository.save(any()))
        .thenAnswer(
            inv -> {
              var r = inv.getArgument(0);
              try {
                r.getClass().getMethod("setId", Long.class).invoke(r, ids.getAndIncrement());
              } catch (Exception ignored) {
              }
              return r;
            });
    TenantSubscriptionEntity t = new TenantSubscriptionEntity();
    t.setOrganizationId("ORG1");
    when(tenantSubscriptionRepository.findById("ORG1")).thenReturn(Optional.of(t));

    Map<String, Object> run =
        service.runCarryForward(
            "ORG1", Map.of("meterCode", "ai_credits", "periodKey", "2026-07", "planGrantAmount", 100));

    // 50% of 1000 = 500, cap 2500 → carry 500, expire 500, grant 100 → close 600
    assertEquals(1000L, ((Number) run.get("openingBalance")).longValue());
    assertEquals(500L, ((Number) run.get("carriedForward")).longValue());
    assertEquals(500L, ((Number) run.get("expired")).longValue());
    assertEquals(100L, ((Number) run.get("granted")).longValue());
    assertEquals(600L, ((Number) run.get("closingBalance")).longValue());
  }

  @Test
  void consumeRejectsInsufficientBalance() {
    CreditWalletEntity wallet = wallet("ORG1", "ai_credits", 10);
    when(walletRepository.findById(any())).thenReturn(Optional.of(wallet));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.consume("ORG1", Map.of("meterCode", "ai_credits", "amount", 50)));
  }

  @Test
  void previewMatchesPolicyMath() {
    when(policyRepository.findById("ai_credits")).thenReturn(Optional.of(aiPolicy()));
    when(walletRepository.findById(any())).thenReturn(Optional.of(wallet("ORG1", "ai_credits", 4000)));

    Map<String, Object> preview = service.previewCarryForward("ORG1", "ai_credits");
    // 50% of 4000 = 2000, cap 2500 → carry 2000, expire 2000
    assertEquals(2000L, ((Number) preview.get("carriedForward")).longValue());
    assertEquals(2000L, ((Number) preview.get("expired")).longValue());
    assertTrue(preview.containsKey("periodKey"));
  }

  private static CreditPolicyEntity aiPolicy() {
    CreditPolicyEntity p = new CreditPolicyEntity();
    p.setMeterCode("ai_credits");
    p.setName("AI credits");
    p.setPeriodType("MONTHLY");
    p.setCarryForwardBps(5000);
    p.setCarryForwardCap(2500L);
    p.setExpireUnused(true);
    p.setPlanGrantAmount(0);
    p.setActive(true);
    return p;
  }

  private static CreditWalletEntity wallet(String org, String meter, long balance) {
    CreditWalletEntity w = new CreditWalletEntity();
    w.setOrganizationId(org);
    w.setMeterCode(meter);
    w.setBalance(balance);
    return w;
  }
}
