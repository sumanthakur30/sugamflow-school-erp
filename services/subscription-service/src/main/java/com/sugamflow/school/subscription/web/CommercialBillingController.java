package com.sugamflow.school.subscription.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.subscription.service.AddonMarketplaceService;
import com.sugamflow.school.subscription.service.CommercialBillingService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commercial billing APIs (additive). Amounts are minor units (paise). Existing entitlements unchanged.
 */
@RestController
@RequestMapping("/api/subscription")
public class CommercialBillingController {

  private final CommercialBillingService commercialBillingService;
  private final AddonMarketplaceService addonMarketplaceService;

  public CommercialBillingController(
      CommercialBillingService commercialBillingService,
      AddonMarketplaceService addonMarketplaceService) {
    this.commercialBillingService = commercialBillingService;
    this.addonMarketplaceService = addonMarketplaceService;
  }

  @GetMapping({"/billing/cycles", "/subscriptions/billing/cycles"})
  public ApiResponse<List<Map<String, Object>>> cycles() {
    return ApiResponse.ok(commercialBillingService.listBillingCycles());
  }

  @GetMapping({"/billing/price-books", "/subscriptions/billing/price-books"})
  public ApiResponse<List<Map<String, Object>>> priceBooks() {
    return ApiResponse.ok(commercialBillingService.listPriceBooks());
  }

  @PutMapping({"/billing/price-books", "/subscriptions/billing/price-books"})
  public ApiResponse<Map<String, Object>> upsertPriceBook(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(commercialBillingService.upsertPriceBook(body));
  }

  @GetMapping({
    "/billing/price-books/{priceBookId}/prices",
    "/subscriptions/billing/price-books/{priceBookId}/prices"
  })
  public ApiResponse<List<Map<String, Object>>> priceBookPrices(@PathVariable String priceBookId) {
    return ApiResponse.ok(commercialBillingService.listPlanPrices(priceBookId));
  }

  @PutMapping({"/billing/plan-prices", "/subscriptions/billing/plan-prices"})
  public ApiResponse<Map<String, Object>> upsertPlanPrice(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(commercialBillingService.upsertPlanPrice(body));
  }

  @GetMapping({"/billing/plans/{planId}/prices", "/subscriptions/billing/plans/{planId}/prices"})
  public ApiResponse<List<Map<String, Object>>> pricesForPlan(@PathVariable String planId) {
    return ApiResponse.ok(commercialBillingService.listPricesForPlan(planId));
  }

  @GetMapping({"/billing/tax-rules", "/subscriptions/billing/tax-rules"})
  public ApiResponse<List<Map<String, Object>>> taxRules() {
    return ApiResponse.ok(commercialBillingService.listTaxRules());
  }

  @PutMapping({"/billing/tax-rules", "/subscriptions/billing/tax-rules"})
  public ApiResponse<Map<String, Object>> upsertTaxRule(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(commercialBillingService.upsertTaxRule(body));
  }

  @GetMapping({"/billing/coupons", "/subscriptions/billing/coupons"})
  public ApiResponse<List<Map<String, Object>>> coupons() {
    return ApiResponse.ok(commercialBillingService.listCoupons());
  }

  @PutMapping({"/billing/coupons", "/subscriptions/billing/coupons"})
  public ApiResponse<Map<String, Object>> upsertCoupon(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(commercialBillingService.upsertCoupon(body));
  }

  @GetMapping({"/billing/marketplace/addons", "/subscriptions/billing/marketplace/addons"})
  public ApiResponse<List<Map<String, Object>>> marketplaceAddons(
      @RequestParam(required = false) String priceBookId) {
    return ApiResponse.ok(addonMarketplaceService.listCatalog(priceBookId));
  }

  @GetMapping({"/tenants/current/addons", "/subscriptions/addons"})
  public ApiResponse<List<Map<String, Object>>> tenantAddons() {
    return ApiResponse.ok(addonMarketplaceService.listTenantAddons(org()));
  }

  @GetMapping({"/tenants/current/credits", "/subscriptions/credits"})
  public ApiResponse<List<Map<String, Object>>> tenantCredits() {
    return ApiResponse.ok(addonMarketplaceService.listCreditWallets(org()));
  }

  @PostMapping({
    "/tenants/current/marketplace/purchase",
    "/subscriptions/marketplace/purchase"
  })
  public ApiResponse<Map<String, Object>> purchaseAddon(
      @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(
        addonMarketplaceService.createPurchaseDraft(org(), body == null ? Map.of() : body));
  }

  @PostMapping({"/tenants/current/invoices/draft", "/subscriptions/invoices/draft"})
  public ApiResponse<Map<String, Object>> draftInvoice(
      @RequestBody(required = false) Map<String, Object> body) {
    return ApiResponse.ok(
        commercialBillingService.createDraftInvoice(org(), body == null ? Map.of() : body));
  }

  @GetMapping({"/tenants/current/invoices", "/subscriptions/invoices"})
  public ApiResponse<List<Map<String, Object>>> listInvoices() {
    return ApiResponse.ok(commercialBillingService.listInvoices(org()));
  }

  @GetMapping({"/tenants/current/invoices/{invoiceId}", "/subscriptions/invoices/{invoiceId}"})
  public ApiResponse<Map<String, Object>> getInvoice(@PathVariable long invoiceId) {
    return ApiResponse.ok(commercialBillingService.getInvoice(org(), invoiceId));
  }

  @PostMapping({
    "/tenants/current/invoices/{invoiceId}/issue",
    "/subscriptions/invoices/{invoiceId}/issue"
  })
  public ApiResponse<Map<String, Object>> issueInvoice(@PathVariable long invoiceId) {
    return ApiResponse.ok(commercialBillingService.issueInvoice(org(), invoiceId));
  }

  @PostMapping({
    "/tenants/current/invoices/{invoiceId}/razorpay-order",
    "/subscriptions/invoices/{invoiceId}/razorpay-order"
  })
  public ApiResponse<Map<String, Object>> razorpayOrder(@PathVariable long invoiceId) {
    return ApiResponse.ok(commercialBillingService.createRazorpayOrder(org(), invoiceId));
  }

  @PostMapping({
    "/tenants/current/invoices/{invoiceId}/void",
    "/subscriptions/invoices/{invoiceId}/void"
  })
  public ApiResponse<Map<String, Object>> voidInvoice(
      @PathVariable long invoiceId, @RequestBody(required = false) Map<String, Object> body) {
    String notes = body == null || body.get("notes") == null ? null : String.valueOf(body.get("notes"));
    return ApiResponse.ok(commercialBillingService.voidInvoice(org(), invoiceId, notes));
  }

  @GetMapping({"/tenants/current/payments", "/subscriptions/payments"})
  public ApiResponse<List<Map<String, Object>>> listPayments() {
    return ApiResponse.ok(commercialBillingService.listPayments(org()));
  }

  /** Gateway/payment-provider webhook — body must include organizationId + invoiceId. */
  @PostMapping({"/billing/payments/webhook", "/subscriptions/billing/payments/webhook"})
  public ApiResponse<Map<String, Object>> paymentWebhook(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(commercialBillingService.applyPaymentWebhook(body));
  }

  /** Client checkout confirm after Razorpay success (or simulated pay). */
  @PostMapping({
    "/billing/payments/razorpay/confirm",
    "/subscriptions/billing/payments/razorpay/confirm"
  })
  public ApiResponse<Map<String, Object>> razorpayConfirm(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(commercialBillingService.confirmRazorpayPayment(body));
  }

  /** Razorpay server-to-server webhook. */
  @PostMapping({
    "/billing/payments/razorpay/webhook",
    "/subscriptions/billing/payments/razorpay/webhook"
  })
  public ApiResponse<Map<String, Object>> razorpayWebhook(
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
    return ApiResponse.ok(commercialBillingService.applyRazorpayWebhook(rawBody, signature));
  }

  private static String org() {
    return TenantContext.require().organizationId();
  }
}
