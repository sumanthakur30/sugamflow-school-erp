package com.sugamflow.school.fee.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.finance.FinanceCatalog;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.persistence.entity.FinanceDefinitionEntity;
import com.sugamflow.school.fee.persistence.entity.FinanceTransactionEntity;
import com.sugamflow.school.fee.persistence.repo.FinanceDefinitionRepository;
import com.sugamflow.school.fee.persistence.repo.FinanceTransactionRepository;
import com.sugamflow.school.fee.web.FeeException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

  private final FinanceDefinitionRepository definitionRepo;
  private final FinanceTransactionRepository transactionRepo;
  private final ConfigEngineClient engines;

  public FinanceService(
      FinanceDefinitionRepository definitionRepo,
      FinanceTransactionRepository transactionRepo,
      ConfigEngineClient engines) {
    this.definitionRepo = definitionRepo;
    this.transactionRepo = transactionRepo;
    this.engines = engines;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    ensureDefaults(scope);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", true);
    out.put("paymentGatewayEnabled", engines.isFeatureEnabled(scope, FinanceCatalog.FEATURE_MULTI_PAYMENT_GATEWAY));
    out.put("accountingEnabled", engines.isFeatureEnabled(scope, FinanceCatalog.FEATURE_ACCOUNTING));
    out.put("heads", listDefinitions(FinanceCatalog.TYPE_FEE_HEAD));
    out.put("structures", listDefinitions(FinanceCatalog.TYPE_FEE_STRUCTURE));
    out.put("concessions", listDefinitions(FinanceCatalog.TYPE_CONCESSION));
    out.put("providers", listDefinitions(FinanceCatalog.TYPE_PAYMENT_PROVIDER));
    return out;
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listDefinitions(String type) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    return definitionRepo
        .findByOrganizationIdAndDefinitionTypeAndStatusOrderByDefinitionKeyAsc(
            scope.organizationId(), type, "ACTIVE")
        .stream()
        .map(this::toDefinitionDto)
        .toList();
  }

  @Transactional
  public Map<String, Object> saveDefinition(String type, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    String key = stringOr(body.get("definitionKey"), stringOr(body.get("code"), null));
    if (key == null || key.isBlank()) {
      throw new FeeException("VALIDATION", "definitionKey is required");
    }
    key = key.trim().toUpperCase().replace(' ', '_');
    Map<String, Object> payload = new LinkedHashMap<>(body);
    payload.put("definitionKey", key);
    payload.remove("id");
    payload.remove("version");

    FinanceDefinitionEntity existing =
        definitionRepo
            .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                scope.organizationId(), type, key, "ACTIVE")
            .orElse(null);

    FinanceDefinitionEntity entity = new FinanceDefinitionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setDefinitionType(type);
    entity.setDefinitionKey(key);
    entity.setStatus("ACTIVE");
    entity.setVersion(existing == null ? 1 : existing.getVersion() + 1);
    entity.setPayload(payload);
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());

    if (existing != null) {
      existing.setStatus("SUPERSEDED");
      existing.setUpdatedAt(Instant.now());
      definitionRepo.save(existing);
    }
    return toDefinitionDto(definitionRepo.save(entity));
  }

  @Transactional
  public Map<String, Object> previewDemand(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    ensureDefaults(scope);
    String structureKey = stringOr(body.get("structureKey"), null);
    if (structureKey == null || structureKey.isBlank()) {
      throw new FeeException("VALIDATION", "structureKey is required");
    }
    Map<String, Object> structure = requireDefinition(scope, FinanceCatalog.TYPE_FEE_STRUCTURE, structureKey);
    List<Map<String, Object>> linesRaw = new ArrayList<>();
    Object rawLines = structure.get("lines");
    if (rawLines instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          @SuppressWarnings("unchecked")
          Map<String, Object> cast = (Map<String, Object>) m;
          linesRaw.add(cast);
        }
      }
    }

    List<Map<String, Object>> lines = new ArrayList<>();
    BigDecimal gross = BigDecimal.ZERO;
    for (Map<String, Object> line : linesRaw) {
      String headKey = stringOr(line.get("headKey"), "");
      BigDecimal amount = toDecimal(line.get("amount"));
      Map<String, Object> outLine = new LinkedHashMap<>();
      outLine.put("headKey", headKey);
      outLine.put("amount", amount);
      outLine.put("optional", Boolean.TRUE.equals(line.get("optional")));
      lines.add(outLine);
      gross = gross.add(amount);
    }

    BigDecimal discount = BigDecimal.ZERO;
    String concessionKey = stringOr(body.get("concessionKey"), null);
    Map<String, Object> concessionApplied = null;
    if (concessionKey != null && !concessionKey.isBlank()) {
      Map<String, Object> concession =
          requireDefinition(scope, FinanceCatalog.TYPE_CONCESSION, concessionKey);
      discount = computeConcession(concession, lines, gross);
      concessionApplied = Map.of(
          "definitionKey", concessionKey,
          "name", stringOr(concession.get("name"), concessionKey),
          "discountAmount", discount);
    }

    BigDecimal net = gross.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("structureKey", structureKey);
    out.put("structureName", stringOr(structure.get("name"), structureKey));
    out.put("studentRef", stringOr(body.get("studentRef"), null));
    out.put("classSection", stringOr(body.get("classSection"), null));
    out.put("currency", stringOr(structure.get("currency"), "INR"));
    out.put("lines", lines);
    out.put("grossAmount", gross.setScale(2, RoundingMode.HALF_UP));
    out.put("discountAmount", discount.setScale(2, RoundingMode.HALF_UP));
    out.put("netAmount", net);
    out.put("concession", concessionApplied);
    out.put("suggestedAnswers", Map.of(
        "feeHead", lines.isEmpty() ? "TUITION" : String.valueOf(lines.get(0).get("headKey")),
        "amount", net,
        "paymentMode", "CASH",
        "admissionNo", stringOr(body.get("studentRef"), ""),
        "studentName", stringOr(body.get("studentName"), "")));
    return out;
  }

  @Transactional
  public Map<String, Object> createPaymentIntent(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    if (!engines.isFeatureEnabled(scope, FinanceCatalog.FEATURE_MULTI_PAYMENT_GATEWAY)) {
      throw new FeeException(
          "FEATURE_OFF", "FEATURE_MULTI_PAYMENT_GATEWAY is off for this subscription plan.");
    }
    String idempotencyKey = stringOr(body.get("idempotencyKey"), null);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      var existing =
          transactionRepo.findByOrganizationIdAndIdempotencyKey(scope.organizationId(), idempotencyKey);
      if (existing.isPresent()) {
        return toTxnDto(existing.get());
      }
    }

    BigDecimal amount = toDecimal(body.get("amount"));
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new FeeException("VALIDATION", "amount must be > 0");
    }
    String providerKey = stringOr(body.get("providerKey"), "simulated");
    Map<String, Object> provider =
        requireDefinition(scope, FinanceCatalog.TYPE_PAYMENT_PROVIDER, providerKey);

    UUID id = UUID.randomUUID();
    String reference = "PI-" + id.toString().substring(0, 8).toUpperCase();
    FinanceTransactionEntity txn = new FinanceTransactionEntity();
    txn.setId(id);
    txn.setOrganizationId(scope.organizationId());
    txn.setBranchId(scope.branchId());
    txn.setAcademicSessionId(scope.academicSessionId());
    txn.setTransactionType("PAYMENT_INTENT");
    txn.setStatus("PENDING");
    txn.setStudentRef(stringOr(body.get("studentRef"), null));
    txn.setCurrency(stringOr(body.get("currency"), "INR"));
    txn.setGrossAmount(amount);
    txn.setNetAmount(amount);
    txn.setReferenceNo(reference);
    txn.setIdempotencyKey(idempotencyKey);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("providerKey", providerKey);
    payload.put("provider", provider);
    payload.put("mode", stringOr(body.get("mode"), "UPI"));
    payload.put("demand", body.get("demand"));
    payload.put("redirectUrl", "/admin/fee?intent=" + id);
    payload.put(
        "simulatedCheckoutUrl",
        "https://pay.example.local/checkout/" + reference + "?amount=" + amount);
    txn.setPayload(payload);
    txn.setCreatedAt(Instant.now());
    txn.setUpdatedAt(Instant.now());
    return toTxnDto(transactionRepo.save(txn));
  }

  @Transactional
  public Map<String, Object> simulateCapture(UUID intentId) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    if (!engines.isFeatureEnabled(scope, FinanceCatalog.FEATURE_MULTI_PAYMENT_GATEWAY)) {
      throw new FeeException(
          "FEATURE_OFF", "FEATURE_MULTI_PAYMENT_GATEWAY is off for this subscription plan.");
    }
    FinanceTransactionEntity intent =
        transactionRepo
            .findByIdAndOrganizationId(intentId, scope.organizationId())
            .orElseThrow(() -> new FeeException("NOT_FOUND", "Payment intent not found"));
    if (!"PAYMENT_INTENT".equals(intent.getTransactionType())) {
      throw new FeeException("VALIDATION", "Not a payment intent");
    }
    if (!"PENDING".equals(intent.getStatus())) {
      return toTxnDto(intent);
    }
    intent.setStatus("CAPTURED");
    intent.setUpdatedAt(Instant.now());
    intent.getPayload().put("capturedAt", Instant.now().toString());
    intent.getPayload().put("gatewayTxnId", "SIM-" + UUID.randomUUID().toString().substring(0, 8));

    FinanceTransactionEntity capture = new FinanceTransactionEntity();
    capture.setId(UUID.randomUUID());
    capture.setOrganizationId(scope.organizationId());
    capture.setBranchId(scope.branchId());
    capture.setAcademicSessionId(scope.academicSessionId());
    capture.setTransactionType("PAYMENT_CAPTURE");
    capture.setStatus("CAPTURED");
    capture.setStudentRef(intent.getStudentRef());
    capture.setCurrency(intent.getCurrency());
    capture.setGrossAmount(intent.getGrossAmount());
    capture.setNetAmount(intent.getNetAmount());
    capture.setReferenceNo("CAP-" + intent.getReferenceNo());
    capture.setIdempotencyKey(intent.getIdempotencyKey() == null ? null : intent.getIdempotencyKey() + ":capture");
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("intentId", intent.getId().toString());
    payload.put("providerKey", intent.getPayload().get("providerKey"));
    capture.setPayload(payload);
    capture.setCreatedAt(Instant.now());
    capture.setUpdatedAt(Instant.now());
    transactionRepo.save(capture);
    return toTxnDto(transactionRepo.save(intent));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> listTransactions() {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    return transactionRepo.findByOrganizationIdOrderByCreatedAtDesc(scope.organizationId()).stream()
        .limit(100)
        .map(this::toTxnDto)
        .toList();
  }

  @Transactional
  public void ensureDefaults(TenantScope scope) {
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), FinanceCatalog.TYPE_FEE_HEAD)) {
      for (Map<String, Object> head : FinanceCatalog.defaultHeads()) {
        saveSeed(scope, FinanceCatalog.TYPE_FEE_HEAD, head);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), FinanceCatalog.TYPE_FEE_STRUCTURE)) {
      for (Map<String, Object> s : FinanceCatalog.defaultStructures()) {
        saveSeed(scope, FinanceCatalog.TYPE_FEE_STRUCTURE, s);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), FinanceCatalog.TYPE_CONCESSION)) {
      for (Map<String, Object> c : FinanceCatalog.defaultConcessions()) {
        saveSeed(scope, FinanceCatalog.TYPE_CONCESSION, c);
      }
    }
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), FinanceCatalog.TYPE_PAYMENT_PROVIDER)) {
      for (Map<String, Object> p : FinanceCatalog.defaultProviders()) {
        saveSeed(scope, FinanceCatalog.TYPE_PAYMENT_PROVIDER, p);
      }
    }
  }

  private void saveSeed(TenantScope scope, String type, Map<String, Object> payload) {
    String key = String.valueOf(payload.get("definitionKey"));
    FinanceDefinitionEntity entity = new FinanceDefinitionEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(scope.branchId());
    entity.setAcademicSessionId(scope.academicSessionId());
    entity.setDefinitionType(type);
    entity.setDefinitionKey(key);
    entity.setStatus("ACTIVE");
    entity.setVersion(1);
    entity.setPayload(new LinkedHashMap<>(payload));
    entity.setCreatedAt(Instant.now());
    entity.setUpdatedAt(Instant.now());
    definitionRepo.save(entity);
  }

  private Map<String, Object> requireDefinition(TenantScope scope, String type, String key) {
    return definitionRepo
        .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
            scope.organizationId(), type, key, "ACTIVE")
        .map(FinanceDefinitionEntity::getPayload)
        .orElseThrow(() -> new FeeException("NOT_FOUND", type + " not found: " + key));
  }

  private BigDecimal computeConcession(
      Map<String, Object> concession, List<Map<String, Object>> lines, BigDecimal gross) {
    @SuppressWarnings("unchecked")
    Map<String, Object> benefit =
        concession.get("benefit") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : Map.of();
    String type = stringOr(benefit.get("type"), "PERCENT");
    BigDecimal value = toDecimal(benefit.get("value"));
    BigDecimal cap = toDecimal(benefit.get("capAmount"));
    @SuppressWarnings("unchecked")
    List<String> applies =
        concession.get("appliesToHeads") instanceof List<?> list
            ? list.stream().map(String::valueOf).toList()
            : List.of();

    BigDecimal base = BigDecimal.ZERO;
    if (applies.isEmpty()) {
      base = gross;
    } else {
      for (Map<String, Object> line : lines) {
        if (applies.contains(String.valueOf(line.get("headKey")))) {
          base = base.add(toDecimal(line.get("amount")));
        }
      }
    }
    BigDecimal discount;
    if ("FIXED".equalsIgnoreCase(type)) {
      discount = value;
    } else {
      discount = base.multiply(value).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
    if (cap.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(cap) > 0) {
      discount = cap;
    }
    return discount.min(gross).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  private void requireFee(TenantScope scope) {
    if (!engines.isFeatureEnabled(scope, FeeCollectionService.FEATURE_FEE)) {
      throw new FeeException("FEATURE_OFF", "FEATURE_FEE is off for this subscription plan.");
    }
  }

  private Map<String, Object> toDefinitionDto(FinanceDefinitionEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId());
    m.put("definitionType", e.getDefinitionType());
    m.put("definitionKey", e.getDefinitionKey());
    m.put("status", e.getStatus());
    m.put("version", e.getVersion());
    m.put("payload", e.getPayload());
    m.put("updatedAt", e.getUpdatedAt() == null ? null : e.getUpdatedAt().toString());
    return m;
  }

  private Map<String, Object> toTxnDto(FinanceTransactionEntity e) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", e.getId() == null ? null : e.getId().toString());
    m.put("transactionType", e.getTransactionType());
    m.put("status", e.getStatus());
    m.put("studentRef", e.getStudentRef());
    m.put("currency", e.getCurrency());
    m.put("grossAmount", e.getGrossAmount());
    m.put("netAmount", e.getNetAmount());
    m.put("referenceNo", e.getReferenceNo());
    m.put("idempotencyKey", e.getIdempotencyKey());
    m.put("payload", e.getPayload());
    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    return m;
  }

  private static BigDecimal toDecimal(Object raw) {
    if (raw == null) return BigDecimal.ZERO;
    try {
      return new BigDecimal(String.valueOf(raw));
    } catch (NumberFormatException ex) {
      return BigDecimal.ZERO;
    }
  }

  private static String stringOr(Object raw, String fallback) {
    if (raw == null) return fallback;
    String s = String.valueOf(raw).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
