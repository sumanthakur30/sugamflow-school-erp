package com.sugamflow.school.fee.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.fee.config.FeeProperties;
import com.sugamflow.school.fee.finance.FinanceCatalog;
import com.sugamflow.school.fee.integration.ConfigEngineClient;
import com.sugamflow.school.fee.integration.StudentProfileClient;
import com.sugamflow.school.fee.payment.GatewayOrderResult;
import com.sugamflow.school.fee.payment.PaymentGatewayAdapter;
import com.sugamflow.school.fee.payment.PaymentGatewayRegistry;
import com.sugamflow.school.fee.payment.RazorpayPaymentAdapter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

  private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

  private final FinanceDefinitionRepository definitionRepo;
  private final FinanceTransactionRepository transactionRepo;
  private final ConfigEngineClient engines;
  private final FeeProperties properties;
  private final PaymentGatewayRegistry gatewayRegistry;
  private final FeeCollectionService feeCollections;
  private final StudentProfileClient studentProfiles;
  private final ObjectMapper objectMapper;

  public FinanceService(
      FinanceDefinitionRepository definitionRepo,
      FinanceTransactionRepository transactionRepo,
      ConfigEngineClient engines,
      FeeProperties properties,
      PaymentGatewayRegistry gatewayRegistry,
      @Lazy FeeCollectionService feeCollections,
      StudentProfileClient studentProfiles,
      ObjectMapper objectMapper) {
    this.definitionRepo = definitionRepo;
    this.transactionRepo = transactionRepo;
    this.engines = engines;
    this.properties = properties;
    this.gatewayRegistry = gatewayRegistry;
    this.feeCollections = feeCollections;
    this.studentProfiles = studentProfiles;
    this.objectMapper = objectMapper;
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
    out.put("paymentMode", properties.getPayment().getMode());
    out.put("availableAdapters", gatewayRegistry.availableAdapters());
    out.put("heads", listDefinitions(FinanceCatalog.TYPE_FEE_HEAD));
    out.put("structures", listDefinitions(FinanceCatalog.TYPE_FEE_STRUCTURE));
    out.put("concessions", listDefinitions(FinanceCatalog.TYPE_CONCESSION));
    out.put("lateFeePolicies", listDefinitions(FinanceCatalog.TYPE_LATE_FEE_POLICY));
    out.put("providers", listDefinitions(FinanceCatalog.TYPE_PAYMENT_PROVIDER));
    out.put("feeSettings", feeModuleSettings(scope));
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
    key = key.trim();
    if (!FinanceCatalog.TYPE_FEE_STRUCTURE.equals(type)) {
      key = key.toUpperCase().replace(' ', '_');
    } else {
      key = key.replace(' ', '_');
    }
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
    return buildDemand(body, false);
  }

  /** Persist FEE_DEMAND txn and optionally open a pending fee collection. */
  @Transactional
  public Map<String, Object> generateDemand(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    Map<String, Object> demand = buildDemand(body, true);
    String periodKey =
        stringOr(body.get("periodKey"), stringOr(demand.get("periodKey"), "CURRENT"));
    String studentRef = stringOr(demand.get("studentRef"), "");
    String structureKey = stringOr(demand.get("structureKey"), "");
    String idem =
        stringOr(
            body.get("idempotencyKey"),
            "DEMAND|" + scope.organizationId() + "|" + studentRef + "|" + structureKey + "|" + periodKey);

    var existing = transactionRepo.findByOrganizationIdAndIdempotencyKey(scope.organizationId(), idem);
    if (existing.isPresent()) {
      Map<String, Object> out = toTxnDto(existing.get());
      out.put("demand", demand);
      out.put("idempotentReplay", true);
      return out;
    }

    FinanceTransactionEntity txn = new FinanceTransactionEntity();
    txn.setId(UUID.randomUUID());
    txn.setOrganizationId(scope.organizationId());
    txn.setBranchId(scope.branchId());
    txn.setAcademicSessionId(scope.academicSessionId());
    txn.setTransactionType("FEE_DEMAND");
    txn.setStatus("OPEN");
    txn.setStudentRef(studentRef.isBlank() ? null : studentRef);
    txn.setCurrency(stringOr(demand.get("currency"), "INR"));
    txn.setGrossAmount(toDecimal(demand.get("grossAmount")));
    txn.setNetAmount(toDecimal(demand.get("netAmount")));
    txn.setReferenceNo("DM-" + txn.getId().toString().substring(0, 8).toUpperCase());
    txn.setIdempotencyKey(idem);
    Map<String, Object> payload = new LinkedHashMap<>(demand);
    payload.put("periodKey", periodKey);
    txn.setPayload(payload);
    txn.setCreatedAt(Instant.now());
    txn.setUpdatedAt(Instant.now());

    boolean createCollection = !Boolean.FALSE.equals(body.get("createCollection"));
    Map<String, Object> collection = null;
    if (createCollection && !studentRef.isBlank()) {
      @SuppressWarnings("unchecked")
      Map<String, Object> answers =
          demand.get("suggestedAnswers") instanceof Map<?, ?> m
              ? new LinkedHashMap<>((Map<String, Object>) m)
              : new LinkedHashMap<>();
      answers.putIfAbsent("admissionNo", studentRef);
      answers.putIfAbsent("studentName", stringOr(demand.get("studentName"), ""));
      answers.put("demandReference", txn.getReferenceNo());
      answers.put("periodKey", periodKey);
      answers.put("structureKey", structureKey);
      answers.putIfAbsent("feeMonth", periodKey);
      answers.putIfAbsent("paymentMode", "CASH");
      answers.putIfAbsent("amount", demand.get("netAmount"));
      Object suggested = demand.get("suggestedAnswers");
      if (suggested instanceof Map<?, ?> sa) {
        answers.putIfAbsent("feeHead", stringOr(sa.get("feeHead"), "TUITION"));
      } else {
        answers.putIfAbsent("feeHead", "TUITION");
      }
      answers.put("discountAmount", demand.get("discountAmount"));
      answers.put("gstAmount", demand.get("gstAmount"));
      answers.put("lateFeeAmount", demand.get("lateFeeAmount"));
      try {
        collection = feeCollections.submitIsolated(Map.of("answers", answers));
        if (collection != null && collection.get("id") != null) {
          txn.setSourceCollectionId(UUID.fromString(String.valueOf(collection.get("id"))));
          payload.put("collectionId", collection.get("id"));
          txn.setPayload(payload);
        }
      } catch (Exception ex) {
        // Do not mark outer txn rollback — demand should still persist.
        log.warn("Demand persisted but collection create failed: {}", ex.getMessage());
        payload.put("collectionError", ex.getMessage());
        txn.setPayload(payload);
        collection = null;
      }
    }

    Map<String, Object> out = toTxnDto(transactionRepo.save(txn));
    out.put("demand", demand);
    out.put("collection", collection);
    return out;
  }

  @Transactional
  public Map<String, Object> generateBulkDemands(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    ensureDefaults(scope);
    Map<String, Object> settings = feeModuleSettings(scope);
    int batchSize = intOr(settings.get("bulkDemandBatchSize"), 200);

    List<Map<String, Object>> targets = new ArrayList<>();
    Object rawRefs = body.get("studentRefs");
    if (rawRefs instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) {
          @SuppressWarnings("unchecked")
          Map<String, Object> cast = (Map<String, Object>) m;
          targets.add(cast);
        } else if (item != null) {
          targets.add(Map.of("studentRef", String.valueOf(item)));
        }
      }
    }
    String classSection = stringOr(body.get("classSection"), null);
    if (targets.isEmpty() && classSection != null && !classSection.isBlank()) {
      List<Map<String, Object>> rows =
          studentProfiles.searchDirectory(
              scope,
              Map.of(
                  "classSection", classSection,
                  "status", "ACTIVE",
                  "size", String.valueOf(batchSize)));
      for (Map<String, Object> row : rows) {
        Map<String, Object> t = new LinkedHashMap<>();
        t.put("studentRef", stringOr(row.get("admissionNo"), ""));
        String name = stringOr(row.get("fullName"), "");
        if (name.isBlank() && row.get("answers") instanceof Map<?, ?> a) {
          name = stringOr(a.get("fullName"), "");
        }
        t.put("studentName", name);
        t.put("classSection", stringOr(row.get("classSection"), classSection));
        if (row.get("answers") instanceof Map<?, ?> ans) {
          t.put("answers", ans);
        }
        if (!stringOr(t.get("studentRef"), "").isBlank()) {
          targets.add(t);
        }
      }
    }
    if (targets.isEmpty()) {
      throw new FeeException("VALIDATION", "Provide studentRefs[] or classSection for bulk demand");
    }
    if (targets.size() > batchSize) {
      targets = targets.subList(0, batchSize);
    }

    List<Map<String, Object>> created = new ArrayList<>();
    List<Map<String, Object>> errors = new ArrayList<>();
    for (Map<String, Object> t : targets) {
      Map<String, Object> req = new LinkedHashMap<>(body);
      req.put("studentRef", t.get("studentRef"));
      req.put("studentName", t.get("studentName"));
      req.put("classSection", stringOr(t.get("classSection"), classSection));
      if (t.get("answers") != null) {
        req.put("studentAnswers", t.get("answers"));
      }
      req.remove("studentRefs");
      try {
        created.add(generateDemand(req));
      } catch (Exception ex) {
        errors.add(
            Map.of(
                "studentRef", stringOr(t.get("studentRef"), ""),
                "error", ex.getMessage() == null ? "failed" : ex.getMessage()));
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requested", targets.size());
    out.put("created", created.size());
    out.put("failed", errors.size());
    out.put("items", created);
    out.put("errors", errors);
    return out;
  }

  @Transactional
  public Map<String, Object> waive(Map<String, Object> body) {
    return postAdjustment("FEE_WAIVE", body);
  }

  @Transactional
  public Map<String, Object> refund(Map<String, Object> body) {
    return postAdjustment("FEE_REFUND", body);
  }

  private Map<String, Object> postAdjustment(String type, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    BigDecimal amount = toDecimal(body.get("amount"));
    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
      throw new FeeException("VALIDATION", "amount must be > 0");
    }
    String reason = stringOr(body.get("reason"), null);
    if (reason == null || reason.isBlank()) {
      throw new FeeException("VALIDATION", "reason is required");
    }
    String headKey = stringOr(body.get("headKey"), "MISC");
    try {
      Map<String, Object> head = requireDefinition(scope, FinanceCatalog.TYPE_FEE_HEAD, headKey);
      if ("FEE_REFUND".equals(type) && Boolean.FALSE.equals(head.get("refundable"))) {
        throw new FeeException("VALIDATION", "Head " + headKey + " is not refundable");
      }
    } catch (FeeException ex) {
      if ("NOT_FOUND".equals(ex.getCode()) && "FEE_REFUND".equals(type)) {
        throw ex;
      }
      // waive may target LATE_FEE even if head missing from older orgs
    }

    UUID collectionId = parseUuid(body.get("collectionId"));
    FinanceTransactionEntity txn = new FinanceTransactionEntity();
    txn.setId(UUID.randomUUID());
    txn.setOrganizationId(scope.organizationId());
    txn.setBranchId(scope.branchId());
    txn.setAcademicSessionId(scope.academicSessionId());
    txn.setTransactionType(type);
    txn.setStatus("POSTED");
    txn.setStudentRef(stringOr(body.get("studentRef"), null));
    txn.setCurrency(stringOr(body.get("currency"), "INR"));
    txn.setGrossAmount(amount);
    txn.setNetAmount(amount);
    txn.setReferenceNo(
        ("FEE_WAIVE".equals(type) ? "WV-" : "RF-")
            + txn.getId().toString().substring(0, 8).toUpperCase());
    txn.setSourceCollectionId(collectionId);
    txn.setIdempotencyKey(stringOr(body.get("idempotencyKey"), null));
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("headKey", headKey);
    payload.put("reason", reason);
    payload.put("amount", amount);
    payload.put("postedBy", scope.userId());
    txn.setPayload(payload);
    txn.setCreatedAt(Instant.now());
    txn.setUpdatedAt(Instant.now());

    if (collectionId != null) {
      try {
        Map<String, Object> detail = feeCollections.get(collectionId);
        if (detail != null && detail.get("answers") instanceof Map<?, ?> raw) {
          @SuppressWarnings("unchecked")
          Map<String, Object> answers = new LinkedHashMap<>((Map<String, Object>) raw);
          if ("FEE_WAIVE".equals(type)) {
            BigDecimal prev = toDecimal(answers.get("waivedAmount"));
            answers.put("waivedAmount", prev.add(amount));
            answers.put("waiveReason", reason);
          } else {
            BigDecimal prev = toDecimal(answers.get("refundAmount"));
            answers.put("refundAmount", prev.add(amount));
            answers.put("refundReason", reason);
          }
          // Best-effort annotate via act is not available; store on txn only if get is read-only.
          payload.put("collectionAnswersSnapshot", answers);
          txn.setPayload(payload);
        }
      } catch (Exception ex) {
        log.debug("Could not annotate collection for {}: {}", type, ex.getMessage());
      }
    }
    return toTxnDto(transactionRepo.save(txn));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> buildDemand(Map<String, Object> body, boolean forPersist) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    ensureDefaults(scope);
    Map<String, Object> settings = feeModuleSettings(scope);

    String structureKey = stringOr(body.get("structureKey"), null);
    if (structureKey == null || structureKey.isBlank()) {
      structureKey = stringOr(settings.get("defaultStructureKey"), "grade_8_annual");
    }
    Map<String, Object> structure =
        requireDefinition(scope, FinanceCatalog.TYPE_FEE_STRUCTURE, structureKey);

    String studentRef = stringOr(body.get("studentRef"), "");
    String studentName = stringOr(body.get("studentName"), "");
    String classSection = stringOr(body.get("classSection"), "");
    Map<String, Object> studentAnswers = new LinkedHashMap<>();
    if (body.get("studentAnswers") instanceof Map<?, ?> m) {
      studentAnswers.putAll((Map<String, Object>) m);
    } else if (!studentRef.isBlank()) {
      Map<String, Object> profile = studentProfiles.byAdmissionNo(scope, studentRef);
      if (profile.get("answers") instanceof Map<?, ?> a) {
        studentAnswers.putAll((Map<String, Object>) a);
      }
      if (studentName.isBlank()) {
        studentName = stringOr(studentAnswers.get("fullName"), stringOr(profile.get("fullName"), ""));
      }
      if (classSection.isBlank()) {
        classSection =
            stringOr(
                studentAnswers.get("classApplied"),
                stringOr(studentAnswers.get("classSection"), stringOr(profile.get("classSection"), "")));
      }
    }

    Map<String, Object> headIndex = headIndex(scope);
    List<Map<String, Object>> lines = new ArrayList<>();
    BigDecimal gross = BigDecimal.ZERO;
    Object rawLines = structure.get("lines");
    if (rawLines instanceof List<?> list) {
      for (Object item : list) {
        if (!(item instanceof Map<?, ?> m)) continue;
        Map<String, Object> line = (Map<String, Object>) m;
        String headKey = stringOr(line.get("headKey"), "");
        if (headKey.isBlank()) continue;
        BigDecimal amount = toDecimal(line.get("amount"));
        Map<String, Object> head = (Map<String, Object>) headIndex.getOrDefault(headKey, Map.of());
        Map<String, Object> outLine = enrichLine(headKey, amount, line, head, settings);
        lines.add(outLine);
        gross = gross.add(amount);
      }
    }

    // Hostel / transport add-ons from ops flags or explicit amounts.
    if (bool(settings.get("includeHostelInDemand"), true)) {
      BigDecimal hostelFee =
          firstPositive(
              toDecimal(body.get("hostelMonthlyFee")),
              truthy(studentAnswers.get("hostel"))
                  ? toDecimal(settings.get("defaultHostelMonthlyFee"))
                  : BigDecimal.ZERO);
      if (hostelFee.compareTo(BigDecimal.ZERO) > 0 && !hasHead(lines, stringOr(settings.get("hostelHeadKey"), "HOSTEL"))) {
        String headKey = stringOr(settings.get("hostelHeadKey"), "HOSTEL");
        Map<String, Object> head = (Map<String, Object>) headIndex.getOrDefault(headKey, Map.of());
        Map<String, Object> outLine =
            enrichLine(headKey, hostelFee, Map.of("frequency", "M", "source", "hostel"), head, settings);
        lines.add(outLine);
        gross = gross.add(hostelFee);
      }
    }
    if (bool(settings.get("includeTransportInDemand"), true)) {
      BigDecimal transportFare =
          firstPositive(
              toDecimal(body.get("transportFare")),
              truthy(studentAnswers.get("transport"))
                  ? toDecimal(settings.get("defaultTransportFare"))
                  : BigDecimal.ZERO);
      if (transportFare.compareTo(BigDecimal.ZERO) > 0
          && !hasHead(lines, stringOr(settings.get("transportHeadKey"), "TRANSPORT"))) {
        String headKey = stringOr(settings.get("transportHeadKey"), "TRANSPORT");
        Map<String, Object> head = (Map<String, Object>) headIndex.getOrDefault(headKey, Map.of());
        Map<String, Object> outLine =
            enrichLine(
                headKey, transportFare, Map.of("frequency", "M", "source", "transport"), head, settings);
        lines.add(outLine);
        gross = gross.add(transportFare);
      }
    }

    BigDecimal gstAmount = BigDecimal.ZERO;
    if (bool(settings.get("gstEnabled"), true)) {
      for (Map<String, Object> line : lines) {
        gstAmount = gstAmount.add(toDecimal(line.get("gstAmount")));
      }
    }

    // Concession / scholarship
    BigDecimal discount = BigDecimal.ZERO;
    String concessionKey = stringOr(body.get("concessionKey"), null);
    Map<String, Object> concessionApplied = null;
    if ((concessionKey == null || concessionKey.isBlank())
        && bool(settings.get("autoApplyScholarship"), true)
        && truthy(studentAnswers.get("scholarship"))) {
      concessionKey = stringOr(settings.get("scholarshipConcessionKey"), "scholarship_50");
    }
    if (concessionKey != null && !concessionKey.isBlank()) {
      try {
        Map<String, Object> concession =
            requireDefinition(scope, FinanceCatalog.TYPE_CONCESSION, concessionKey);
        discount = computeConcession(concession, lines, gross);
        concessionApplied =
            Map.of(
                "definitionKey",
                concessionKey,
                "name",
                stringOr(concession.get("name"), concessionKey),
                "discountAmount",
                discount);
      } catch (FeeException ex) {
        if (forPersist) throw ex;
        log.debug("Concession {} skipped: {}", concessionKey, ex.getMessage());
      }
    }

    BigDecimal taxableBase = gross.subtract(discount).max(BigDecimal.ZERO);
    // Re-scale GST proportionally after discount when GST is enabled.
    if (bool(settings.get("gstEnabled"), true) && gross.compareTo(BigDecimal.ZERO) > 0 && discount.compareTo(BigDecimal.ZERO) > 0) {
      BigDecimal factor =
          taxableBase.divide(gross, 6, RoundingMode.HALF_UP);
      gstAmount = gstAmount.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal lateFee = BigDecimal.ZERO;
    Map<String, Object> lateFeeApplied = null;
    int pendingDays = intOr(body.get("pendingDays"), 0);
    if (bool(settings.get("lateFeeEnabled"), true) && pendingDays > 0) {
      String policyKey = stringOr(body.get("lateFeePolicyKey"), stringOr(settings.get("lateFeePolicyKey"), "late_per_day_10"));
      try {
        Map<String, Object> policy =
            requireDefinition(scope, FinanceCatalog.TYPE_LATE_FEE_POLICY, policyKey);
        lateFee = computeLateFee(policy, pendingDays, taxableBase);
        if (lateFee.compareTo(BigDecimal.ZERO) > 0) {
          lateFeeApplied =
              Map.of(
                  "definitionKey",
                  policyKey,
                  "name",
                  stringOr(policy.get("name"), policyKey),
                  "pendingDays",
                  pendingDays,
                  "lateFeeAmount",
                  lateFee);
          Map<String, Object> head =
              (Map<String, Object>) headIndex.getOrDefault("LATE_FEE", Map.of());
          lines.add(
              enrichLine(
                  "LATE_FEE",
                  lateFee,
                  Map.of("frequency", "OT", "source", "lateFee"),
                  head,
                  settings));
        }
      } catch (FeeException ex) {
        log.debug("Late fee policy skipped: {}", ex.getMessage());
      }
    }

    BigDecimal net =
        taxableBase
            .add(gstAmount)
            .add(lateFee)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP);

    String periodKey =
        stringOr(
            body.get("periodKey"),
            stringOr(scope.academicSessionId(), "CURRENT") + "-DEMAND");

    Map<String, Object> out = new LinkedHashMap<>();
    out.put("structureKey", structureKey);
    out.put("structureName", stringOr(structure.get("name"), structureKey));
    out.put("studentRef", studentRef.isBlank() ? null : studentRef);
    out.put("studentName", studentName.isBlank() ? null : studentName);
    out.put("classSection", classSection.isBlank() ? null : classSection);
    out.put("periodKey", periodKey);
    out.put("currency", stringOr(structure.get("currency"), stringOr(settings.get("defaultCurrency"), "INR")));
    out.put("lines", lines);
    out.put("grossAmount", gross.setScale(2, RoundingMode.HALF_UP));
    out.put("discountAmount", discount.setScale(2, RoundingMode.HALF_UP));
    out.put("gstAmount", gstAmount.setScale(2, RoundingMode.HALF_UP));
    out.put("lateFeeAmount", lateFee.setScale(2, RoundingMode.HALF_UP));
    out.put("netAmount", net);
    out.put("concession", concessionApplied);
    out.put("lateFee", lateFeeApplied);
    out.put(
        "suggestedAnswers",
        Map.of(
            "feeHead",
            lines.isEmpty() ? "TUITION" : String.valueOf(lines.get(0).get("headKey")),
            "amount",
            net,
            "paymentMode",
            "CASH",
            "admissionNo",
            studentRef,
            "studentName",
            studentName,
            "discountAmount",
            discount,
            "gstAmount",
            gstAmount,
            "lateFeeAmount",
            lateFee,
            "periodKey",
            periodKey,
            "structureKey",
            structureKey));
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
    PaymentGatewayAdapter adapter = gatewayRegistry.resolve(provider);

    UUID id = UUID.randomUUID();
    String reference = "PI-" + id.toString().substring(0, 8).toUpperCase();
    UUID collectionId = parseUuid(body.get("collectionId"));

    Map<String, Object> notes = new LinkedHashMap<>();
    notes.put("organizationId", scope.organizationId());
    notes.put("intentReference", reference);
    notes.put("studentRef", stringOr(body.get("studentRef"), ""));
    if (collectionId != null) {
      notes.put("collectionId", collectionId.toString());
    }

    GatewayOrderResult order =
        adapter.createOrder(
            reference,
            amount,
            stringOr(body.get("currency"), "INR"),
            stringOr(body.get("studentRef"), null),
            notes);

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
    txn.setSourceCollectionId(collectionId);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("providerKey", providerKey);
    payload.put("provider", provider);
    payload.put("adapter", order.adapter());
    payload.put("mode", stringOr(body.get("mode"), "UPI"));
    payload.put("demand", body.get("demand"));
    payload.put("gatewayOrderId", order.gatewayOrderId());
    payload.put("checkoutMode", order.checkoutMode());
    payload.put("checkout", order.checkout());
    if (order.publicKey() != null) {
      payload.put("publicKey", order.publicKey());
    }
    if (order.providerMeta() != null) {
      payload.putAll(order.providerMeta());
    }
    payload.put(
        "redirectUrl",
        collectionId != null
            ? "/parent/fees?intent=" + id
            : "/admin/finance?intent=" + id);
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
    String adapter = stringOr(intent.getPayload().get("adapter"), "SIMULATED");
    if (!"SIMULATED".equalsIgnoreCase(adapter)
        && !"simulate".equalsIgnoreCase(properties.getPayment().getMode())) {
      throw new FeeException(
          "VALIDATION", "simulate-capture is only allowed for SIMULATED intents (or fee.payment.mode=simulate)");
    }
    return captureIntent(
        intent, "SIM-" + UUID.randomUUID().toString().substring(0, 8), "simulate-capture");
  }

  /**
   * Client-side confirm after Razorpay checkout success (webhook remains authoritative). Safe to
   * call repeatedly.
   */
  @Transactional
  public Map<String, Object> confirmCapture(UUID intentId, Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    requireFee(scope);
    FinanceTransactionEntity intent =
        transactionRepo
            .findByIdAndOrganizationId(intentId, scope.organizationId())
            .orElseThrow(() -> new FeeException("NOT_FOUND", "Payment intent not found"));
    String gatewayTxnId =
        stringOr(body == null ? null : body.get("gatewayTxnId"), stringOr(body == null ? null : body.get("razorpayPaymentId"), null));
    if (gatewayTxnId == null || gatewayTxnId.isBlank()) {
      gatewayTxnId = "CONF-" + UUID.randomUUID().toString().substring(0, 8);
    }
    return captureIntent(intent, gatewayTxnId, "client-confirm");
  }

  /** Razorpay / simulated webhook entry — no JWT tenant required (org resolved from order). */
  @Transactional
  public Map<String, Object> handleWebhook(
      String adapterKey, String rawBody, String signatureHeader) {
    PaymentGatewayAdapter adapter = gatewayRegistry.byKey(adapterKey);
    if (!adapter.verifyWebhook(rawBody, signatureHeader)) {
      throw new FeeException("WEBHOOK_INVALID", "Invalid webhook signature");
    }
    Map<String, Object> body;
    try {
      body = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
    } catch (Exception ex) {
      throw new FeeException("WEBHOOK_INVALID", "Webhook body is not valid JSON");
    }
    if (adapter instanceof RazorpayPaymentAdapter rzp && !rzp.isCaptureEvent(body)) {
      return Map.of("ignored", true, "event", body.get("event"));
    }
    String orderId = adapter.extractOrderId(body);
    if (orderId == null || orderId.isBlank()) {
      throw new FeeException("WEBHOOK_INVALID", "Webhook missing gateway order id");
    }
    FinanceTransactionEntity intent =
        transactionRepo
            .findPaymentIntentByGatewayOrderId(orderId)
            .orElseThrow(() -> new FeeException("NOT_FOUND", "No payment intent for order " + orderId));

    TenantScope previous = TenantContext.get().orElse(null);
    try {
      TenantContext.set(
          new TenantScope(
              intent.getOrganizationId(),
              intent.getBranchId(),
              intent.getAcademicSessionId(),
              "gateway",
              "SYSTEM"));
      String paymentId = adapter.extractPaymentId(body);
      if (paymentId == null || paymentId.isBlank()) {
        paymentId = "WH-" + UUID.randomUUID().toString().substring(0, 8);
      }
      return captureIntent(intent, paymentId, "webhook");
    } finally {
      if (previous != null) {
        TenantContext.set(previous);
      } else {
        TenantContext.clear();
      }
    }
  }

  private Map<String, Object> captureIntent(
      FinanceTransactionEntity intent, String gatewayTxnId, String source) {
    if (!"PAYMENT_INTENT".equals(intent.getTransactionType())) {
      throw new FeeException("VALIDATION", "Not a payment intent");
    }
    if (!"PENDING".equals(intent.getStatus())) {
      return toTxnDto(intent);
    }

    intent.setStatus("CAPTURED");
    intent.setUpdatedAt(Instant.now());
    intent.getPayload().put("capturedAt", Instant.now().toString());
    intent.getPayload().put("gatewayTxnId", gatewayTxnId);
    intent.getPayload().put("captureSource", source);

    String captureIdem =
        intent.getIdempotencyKey() == null ? null : intent.getIdempotencyKey() + ":capture";
    if (captureIdem != null) {
      var existingCapture =
          transactionRepo.findByOrganizationIdAndIdempotencyKey(
              intent.getOrganizationId(), captureIdem);
      if (existingCapture.isPresent()) {
        return toTxnDto(transactionRepo.save(intent));
      }
    }

    FinanceTransactionEntity capture = new FinanceTransactionEntity();
    capture.setId(UUID.randomUUID());
    capture.setOrganizationId(intent.getOrganizationId());
    capture.setBranchId(intent.getBranchId());
    capture.setAcademicSessionId(intent.getAcademicSessionId());
    capture.setTransactionType("PAYMENT_CAPTURE");
    capture.setStatus("CAPTURED");
    capture.setStudentRef(intent.getStudentRef());
    capture.setCurrency(intent.getCurrency());
    capture.setGrossAmount(intent.getGrossAmount());
    capture.setNetAmount(intent.getNetAmount());
    capture.setReferenceNo("CAP-" + intent.getReferenceNo());
    capture.setIdempotencyKey(captureIdem);
    capture.setSourceCollectionId(intent.getSourceCollectionId());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("intentId", intent.getId().toString());
    payload.put("providerKey", intent.getPayload().get("providerKey"));
    payload.put("adapter", intent.getPayload().get("adapter"));
    payload.put("gatewayTxnId", gatewayTxnId);
    payload.put("captureSource", source);
    capture.setPayload(payload);
    capture.setCreatedAt(Instant.now());
    capture.setUpdatedAt(Instant.now());
    transactionRepo.save(capture);

    FinanceTransactionEntity saved = transactionRepo.save(intent);
    settleCollection(saved, gatewayTxnId);
    return toTxnDto(saved);
  }

  private void settleCollection(FinanceTransactionEntity intent, String gatewayTxnId) {
    if (intent.getSourceCollectionId() == null) {
      return;
    }
    try {
      feeCollections.markPaidFromGateway(
          intent.getSourceCollectionId(),
          gatewayTxnId,
          intent.getReferenceNo(),
          stringOr(intent.getPayload().get("providerKey"), null));
    } catch (Exception ex) {
      log.warn(
          "Could not settle fee collection {} after capture: {}",
          intent.getSourceCollectionId(),
          ex.getMessage());
    }
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
    seedMissing(
        scope, FinanceCatalog.TYPE_FEE_HEAD, FinanceCatalog.defaultHeads());
    seedMissing(
        scope, FinanceCatalog.TYPE_FEE_STRUCTURE, FinanceCatalog.defaultStructures());
    seedMissing(
        scope, FinanceCatalog.TYPE_CONCESSION, FinanceCatalog.defaultConcessions());
    seedMissing(
        scope, FinanceCatalog.TYPE_LATE_FEE_POLICY, FinanceCatalog.defaultLateFeePolicies());
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(
        scope.organizationId(), FinanceCatalog.TYPE_PAYMENT_PROVIDER)) {
      for (Map<String, Object> p : FinanceCatalog.defaultProviders()) {
        saveSeed(scope, FinanceCatalog.TYPE_PAYMENT_PROVIDER, p);
      }
    } else {
      boolean hasRazorpay =
          definitionRepo
              .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                  scope.organizationId(),
                  FinanceCatalog.TYPE_PAYMENT_PROVIDER,
                  "razorpay",
                  "ACTIVE")
              .isPresent();
      if (!hasRazorpay) {
        FinanceCatalog.defaultProviders().stream()
            .filter(p -> "razorpay".equalsIgnoreCase(String.valueOf(p.get("definitionKey"))))
            .forEach(p -> saveSeed(scope, FinanceCatalog.TYPE_PAYMENT_PROVIDER, p));
      }
    }
  }

  private void seedMissing(TenantScope scope, String type, List<Map<String, Object>> defaults) {
    if (!definitionRepo.existsByOrganizationIdAndDefinitionType(scope.organizationId(), type)) {
      for (Map<String, Object> item : defaults) {
        saveSeed(scope, type, item);
      }
      return;
    }
    for (Map<String, Object> item : defaults) {
      String key = String.valueOf(item.get("definitionKey"));
      var existing =
          definitionRepo
              .findFirstByOrganizationIdAndDefinitionTypeAndDefinitionKeyAndStatusOrderByVersionDesc(
                  scope.organizationId(), type, key, "ACTIVE");
      if (existing.isEmpty()) {
        saveSeed(scope, type, item);
        continue;
      }
      // Refresh sample annual structure once so Phase C lines (ANNUAL/COMPUTER/…) appear.
      if (FinanceCatalog.TYPE_FEE_STRUCTURE.equals(type)
          && "grade_8_annual".equalsIgnoreCase(key)) {
        String payload = String.valueOf(existing.get().getPayload());
        if (!payload.contains("ANNUAL") || !payload.contains("\"frequency\"")) {
          saveDefinition(type, item);
        }
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

  @SuppressWarnings("unchecked")
  private Map<String, Object> feeModuleSettings(TenantScope scope) {
    Map<String, Object> module = engines.getModuleSettings(scope, "fee");
    Object settings = module != null ? module.get("settings") : null;
    if (settings instanceof Map<?, ?> m) {
      return new LinkedHashMap<>((Map<String, Object>) m);
    }
    return new LinkedHashMap<>();
  }

  private Map<String, Object> headIndex(TenantScope scope) {
    Map<String, Object> index = new LinkedHashMap<>();
    for (Map<String, Object> dto : listDefinitions(FinanceCatalog.TYPE_FEE_HEAD)) {
      Object payload = dto.get("payload");
      if (payload instanceof Map<?, ?> m) {
        @SuppressWarnings("unchecked")
        Map<String, Object> cast = (Map<String, Object>) m;
        index.put(stringOr(cast.get("definitionKey"), stringOr(dto.get("definitionKey"), "")), cast);
      }
    }
    return index;
  }

  private Map<String, Object> enrichLine(
      String headKey,
      BigDecimal amount,
      Map<String, Object> line,
      Map<String, Object> head,
      Map<String, Object> settings) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("headKey", headKey);
    out.put("label", stringOr(head.get("label"), headKey));
    out.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
    out.put(
        "frequency",
        stringOr(line.get("frequency"), stringOr(head.get("frequency"), FinanceCatalog.FREQ_ONE_TIME)));
    out.put("optional", Boolean.TRUE.equals(line.get("optional")));
    if (line.get("source") != null) {
      out.put("source", line.get("source"));
    }
    double gstRate =
        doubleOr(line.get("gstRate"), doubleOr(head.get("gstRate"), doubleOr(settings.get("defaultGstRate"), 0)));
    boolean taxable =
        bool(line.get("taxable"), bool(head.get("taxable"), gstRate > 0))
            && bool(settings.get("gstEnabled"), true);
    BigDecimal gst = BigDecimal.ZERO;
    if (taxable && gstRate > 0) {
      gst =
          amount
              .multiply(BigDecimal.valueOf(gstRate))
              .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
      // Split equally CGST/SGST for intra-state school billing default.
      BigDecimal half = gst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
      out.put("cgst", half);
      out.put("sgst", gst.subtract(half));
    }
    out.put("gstRate", gstRate);
    out.put("gstAmount", gst);
    out.put("taxable", taxable);
    return out;
  }

  private BigDecimal computeLateFee(Map<String, Object> policy, int pendingDays, BigDecimal base) {
    if (!bool(policy.get("enabled"), true)) {
      return BigDecimal.ZERO;
    }
    int grace = intOr(policy.get("graceDays"), 0);
    int chargeable = Math.max(0, pendingDays - grace);
    if (chargeable <= 0 && toDecimal(policy.get("flatAmount")).compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    BigDecimal perDay = toDecimal(policy.get("perDayAmount"));
    BigDecimal flat = toDecimal(policy.get("flatAmount"));
    BigDecimal fee = flat.add(perDay.multiply(BigDecimal.valueOf(chargeable)));
    BigDecimal cap = toDecimal(policy.get("capAmount"));
    if (cap.compareTo(BigDecimal.ZERO) > 0 && fee.compareTo(cap) > 0) {
      fee = cap;
    }
    return fee.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
  }

  private static boolean hasHead(List<Map<String, Object>> lines, String headKey) {
    for (Map<String, Object> line : lines) {
      if (headKey.equalsIgnoreCase(String.valueOf(line.get("headKey")))) {
        return true;
      }
    }
    return false;
  }

  private static BigDecimal firstPositive(BigDecimal a, BigDecimal b) {
    if (a != null && a.compareTo(BigDecimal.ZERO) > 0) return a;
    if (b != null && b.compareTo(BigDecimal.ZERO) > 0) return b;
    return BigDecimal.ZERO;
  }

  private static boolean truthy(Object v) {
    if (v == null) return false;
    if (v instanceof Boolean b) return b;
    String s = String.valueOf(v).trim();
    return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
  }

  private static boolean bool(Object v, boolean def) {
    if (v == null) return def;
    if (v instanceof Boolean b) return b;
    return Boolean.parseBoolean(String.valueOf(v));
  }

  private static int intOr(Object v, int def) {
    if (v instanceof Number n) return n.intValue();
    try {
      return v != null ? Integer.parseInt(String.valueOf(v).trim()) : def;
    } catch (NumberFormatException ex) {
      return def;
    }
  }

  private static double doubleOr(Object v, double def) {
    if (v instanceof Number n) return n.doubleValue();
    try {
      return v != null ? Double.parseDouble(String.valueOf(v).trim()) : def;
    } catch (NumberFormatException ex) {
      return def;
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
    m.put(
        "sourceCollectionId",
        e.getSourceCollectionId() == null ? null : e.getSourceCollectionId().toString());
    m.put("payload", e.getPayload());
    if (e.getPayload() != null) {
      m.put("checkoutMode", e.getPayload().get("checkoutMode"));
      m.put("checkout", e.getPayload().get("checkout"));
      m.put("adapter", e.getPayload().get("adapter"));
      m.put("gatewayOrderId", e.getPayload().get("gatewayOrderId"));
      m.put("publicKey", e.getPayload().get("publicKey"));
    }
    m.put("createdAt", e.getCreatedAt() == null ? null : e.getCreatedAt().toString());
    return m;
  }

  private static UUID parseUuid(Object raw) {
    if (raw == null) {
      return null;
    }
    String s = String.valueOf(raw).trim();
    if (s.isEmpty() || "null".equalsIgnoreCase(s)) {
      return null;
    }
    try {
      return UUID.fromString(s);
    } catch (IllegalArgumentException ex) {
      throw new FeeException("VALIDATION", "Invalid collectionId: " + s);
    }
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
