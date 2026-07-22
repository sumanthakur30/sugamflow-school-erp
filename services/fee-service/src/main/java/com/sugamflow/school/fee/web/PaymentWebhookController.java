package com.sugamflow.school.fee.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.fee.service.FinanceService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public webhook endpoints (no JWT). Gateway must permit these paths and still set {@code
 * X-Gateway-Verified}.
 */
@RestController
@RequestMapping("/api/fee/finance/payments/webhooks")
public class PaymentWebhookController {

  private final FinanceService financeService;

  public PaymentWebhookController(FinanceService financeService) {
    this.financeService = financeService;
  }

  @PostMapping("/{adapter}")
  public ResponseEntity<ApiResponse<Map<String, Object>>> handle(
      @PathVariable("adapter") String adapter,
      @RequestBody String rawBody,
      @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
      @RequestHeader(value = "X-Sim-Signature", required = false) String simSignature) {
    try {
      String signature =
          razorpaySignature != null && !razorpaySignature.isBlank()
              ? razorpaySignature
              : simSignature;
      Map<String, Object> result = financeService.handleWebhook(adapter, rawBody, signature);
      return ResponseEntity.ok(ApiResponse.ok(result));
    } catch (FeeException ex) {
      HttpStatus status =
          "NOT_FOUND".equals(ex.getCode())
              ? HttpStatus.NOT_FOUND
              : "WEBHOOK_INVALID".equals(ex.getCode())
                  ? HttpStatus.UNAUTHORIZED
                  : HttpStatus.BAD_REQUEST;
      return ResponseEntity.status(status)
          .body(new ApiResponse<>(false, Map.of("code", ex.getCode()), ex.getMessage()));
    }
  }
}
