package com.sugamflow.school.reportbuilder.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.reportbuilder.integration.ConfigEngineClient;
import com.sugamflow.school.reportbuilder.persistence.entity.ReportTemplateEntity;
import com.sugamflow.school.reportbuilder.persistence.repo.ReportTemplateRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportTemplateService {

  public static final String FEATURE_REPORT_BUILDER = "FEATURE_REPORT_BUILDER";

  private final ReportTemplateRepository repo;
  private final ReportPdfRenderService pdfRenderService;
  private final ConfigEngineClient engines;

  public ReportTemplateService(
      ReportTemplateRepository repo,
      ReportPdfRenderService pdfRenderService,
      ConfigEngineClient engines) {
    this.repo = repo;
    this.pdfRenderService = pdfRenderService;
    this.engines = engines;
  }

  @Transactional
  public Map<String, Object> bootstrap() {
    TenantScope scope = TenantContext.require();
    boolean enabled = engines.isFeatureEnabled(scope, FEATURE_REPORT_BUILDER);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("featureEnabled", enabled);
    out.put("requiredFeatureFlag", FEATURE_REPORT_BUILDER);
    out.put("elementTypes", ReportElementCatalog.ELEMENT_TYPES);
    out.put("samplePreviewData", ReportElementCatalog.samplePreviewData());
    out.put(
        "defaultLayout",
        Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    out.put("exportFormats", List.of("PDF", "EXCEL", "CSV", "PRINT"));
    if (enabled) {
      out.put("templates", list(scope.organizationId()));
    } else {
      out.put("templates", List.of());
    }
    return out;
  }

  @Transactional
  public List<Map<String, Object>> list(String org) {
    ensureDefaults();
    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
    for (ReportTemplateEntity e : repo.findVisible(org)) {
      if (e.getOrganizationId() == null) {
        byKey.put(e.getTemplateKey(), normalizeTemplate(e.getPayload()));
      }
    }
    for (ReportTemplateEntity e : repo.findVisible(org)) {
      if (org.equals(e.getOrganizationId())) {
        byKey.put(e.getTemplateKey(), normalizeTemplate(e.getPayload()));
      }
    }
    return new ArrayList<>(byKey.values());
  }

  @Transactional
  public Map<String, Object> get(String org, String key) {
    ensureDefaults();
    var orgEntity = repo.findByOrganizationIdAndTemplateKey(org, key);
    var globalEntity = repo.findByOrganizationIdIsNullAndTemplateKey(key);
    if (orgEntity.isPresent()) {
      Map<String, Object> orgTemplate = normalizeTemplate(orgEntity.get().getPayload());
      if (hasRenderableElements(orgTemplate)) {
        return orgTemplate;
      }
    }
    return globalEntity.map(e -> normalizeTemplate(e.getPayload())).orElse(null);
  }

  @Transactional
  public Map<String, Object> save(String org, String key, Map<String, Object> body) {
    requireFeature();
    Map<String, Object> normalized = normalizeTemplate(body != null ? body : Map.of());
    normalized.put("templateKey", key);
    normalized.put("organizationId", org);
    ReportTemplateEntity e =
        repo.findByOrganizationIdAndTemplateKey(org, key).orElseGet(ReportTemplateEntity::new);
    e.setOrganizationId(org);
    e.setTemplateKey(key);
    e.setPayload(normalized);
    e.setUpdatedAt(Instant.now());
    return normalizeTemplate(repo.save(e).getPayload());
  }

  @Transactional
  public Map<String, Object> render(String org, String key, Map<String, Object> body) {
    requireFeature();
    Map<String, Object> template = get(org, key);
    if (template == null) {
      throw new IllegalArgumentException("Template not found: " + key);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> data =
        body != null && body.get("data") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : (body != null ? body : Map.of());
    if (data.isEmpty()) {
      data = ReportElementCatalog.samplePreviewData();
    }
    String format = String.valueOf(body != null ? body.getOrDefault("format", "PDF") : "PDF");
    if (!"PDF".equalsIgnoreCase(format)) {
      throw new IllegalArgumentException("Unsupported format: " + format + " (PDF only)");
    }
    return pdfRenderService.render(template, data);
  }

  @Transactional
  public Map<String, Object> previewLayout(Map<String, Object> body) {
    requireFeature();
    Map<String, Object> template = normalizeTemplate(body != null ? body : Map.of());
    @SuppressWarnings("unchecked")
    Map<String, Object> data =
        body != null && body.get("data") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : ReportElementCatalog.samplePreviewData();
    return pdfRenderService.render(template, data);
  }

  private void requireFeature() {
    TenantScope scope = TenantContext.require();
    if (!engines.isFeatureEnabled(scope, FEATURE_REPORT_BUILDER)) {
      throw new IllegalStateException(
          "FEATURE_REPORT_BUILDER is off for this subscription plan.");
    }
  }

  private Map<String, Object> normalizeTemplate(Map<String, Object> raw) {
    Map<String, Object> t = new LinkedHashMap<>(raw != null ? raw : Map.of());
    if (!t.containsKey("layout") || !(t.get("layout") instanceof Map<?, ?>)) {
      t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    }
    t.put("elements", ReportElementCatalog.normalizeElements(t.get("elements")));
    if (!t.containsKey("charts")) {
      t.put("charts", List.of());
    }
    if (!t.containsKey("filters")) {
      t.put("filters", List.of());
    }
    if (!t.containsKey("calculatedFields")) {
      t.put("calculatedFields", List.of());
    }
    if (!t.containsKey("schedule")) {
      t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    }
    return t;
  }

  @Transactional
  public void ensureDefaults() {
    if (repo.count() == 0) {
      for (String key :
          List.of(
              "certificate",
              "id_card",
              "fee_receipt",
              "salary_slip",
              "report_card",
              "transfer_certificate",
              "bonafide",
              "character_certificate",
              "admission_form",
              "library_card",
              "transport_pass",
              "hostel_id",
              "offer_letter")) {
        ReportTemplateEntity e = new ReportTemplateEntity();
        e.setOrganizationId(null);
        e.setTemplateKey(key);
        e.setPayload(template(key));
        e.setUpdatedAt(Instant.now());
        repo.save(e);
      }
      return;
    }
    ensureTemplate("offer_letter");
    ensureFeeReceiptEnriched();
    ensureTransferCertificateEnriched();
  }

  private void ensureTemplate(String key) {
    if (repo.findByOrganizationIdIsNullAndTemplateKey(key).isPresent()) {
      return;
    }
    ReportTemplateEntity e = new ReportTemplateEntity();
    e.setOrganizationId(null);
    e.setTemplateKey(key);
    e.setPayload(template(key));
    e.setUpdatedAt(Instant.now());
    repo.save(e);
  }

  private void ensureFeeReceiptEnriched() {
    if (repo.findByOrganizationIdIsNullAndTemplateKey("fee_receipt").isEmpty()) {
      ensureTemplate("fee_receipt");
    }
    Map<String, Object> canonical = feeReceiptTemplate();
    for (ReportTemplateEntity e : repo.findByTemplateKey("fee_receipt")) {
      if (!needsFeeReceiptEnrichment(e.getPayload())) {
        continue;
      }
      Map<String, Object> repaired = new LinkedHashMap<>(canonical);
      if (e.getOrganizationId() != null) {
        repaired.put("organizationId", e.getOrganizationId());
      }
      e.setPayload(repaired);
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    }
  }

  private boolean needsFeeReceiptEnrichment(Map<String, Object> payload) {
    if (payload == null) {
      return true;
    }
    Map<String, Object> normalized = normalizeTemplate(payload);
    if (!hasRenderableElements(normalized)) {
      return true;
    }
    String serialized = String.valueOf(payload);
    return !serialized.contains("{{payment.") && !serialized.contains("payment.studentName");
  }

  private static boolean hasRenderableElements(Map<String, Object> template) {
    Object raw = template.get("elements");
    return raw instanceof List<?> list && !list.isEmpty();
  }

  private Map<String, Object> template(String key) {
    if ("offer_letter".equals(key)) {
      return offerLetterTemplate();
    }
    if ("fee_receipt".equals(key)) {
      return feeReceiptTemplate();
    }
    if ("transfer_certificate".equals(key)) {
      return transferCertificateTemplate();
    }
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", key);
    t.put("name", key.replace('_', ' '));
    t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    t.put(
        "elements",
        List.of(
            Map.of(
                "type",
                "heading",
                "text",
                key.replace('_', ' ').toUpperCase(),
                "x",
                40,
                "y",
                48,
                "fontSize",
                18,
                "width",
                500,
                "height",
                28),
            Map.of(
                "type",
                "field",
                "bind",
                "student.name",
                "text",
                "{{student.name}}",
                "x",
                40,
                "y",
                100,
                "fontSize",
                12,
                "width",
                280,
                "height",
                24),
            Map.of("type", "line", "x", 40, "y", 140, "width", 500, "height", 2)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL", "WHATSAPP")));
    return normalizeTemplate(t);
  }

  private Map<String, Object> offerLetterTemplate() {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", "offer_letter");
    t.put("name", "Offer Letter");
    t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    t.put(
        "elements",
        List.of(
            element("heading", "OFFER OF ADMISSION", 40, 60, 18, 500, 28),
            element("text", "Dear {{application.fullName}},", 40, 120, 12, 500, 24),
            element(
                "text",
                "We are pleased to offer you admission to class {{application.classApplied}} for session {{context.academicSessionId}}.",
                40,
                160,
                11,
                520,
                48),
            element(
                "text",
                "Organization: {{context.organizationId}} · Branch: {{context.branchId}}",
                40,
                230,
                10,
                520,
                24),
            element(
                "text",
                "Contact on file: {{application.email}} / {{application.mobile}}",
                40,
                270,
                10,
                520,
                24),
            element(
                "text",
                "Please retain this letter for your records. Download: {{context.offerLetterUrl}}",
                40,
                310,
                10,
                520,
                40),
            element("line", "", 40, 370, 11, 520, 2),
            element("text", "Issued at {{context.issuedAt}}", 40, 390, 10, 400, 24)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    return normalizeTemplate(t);
  }

  private void ensureTransferCertificateEnriched() {
    repo.findByOrganizationIdIsNullAndTemplateKey("transfer_certificate")
        .ifPresentOrElse(
            e -> {
              String payload = String.valueOf(e.getPayload());
              if (payload.contains("tc.leavingDate") || payload.contains("{{tc.")) {
                return;
              }
              e.setPayload(transferCertificateTemplate());
              e.setUpdatedAt(Instant.now());
              repo.save(e);
            },
            () -> ensureTemplate("transfer_certificate"));
  }

  private Map<String, Object> transferCertificateTemplate() {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", "transfer_certificate");
    t.put("name", "Transfer Certificate");
    t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    t.put(
        "elements",
        List.of(
            element("heading", "TRANSFER CERTIFICATE", 40, 60, 18, 500, 28),
            element(
                "text",
                "This is to certify that {{student.name}} (Admission No: {{student.admissionNo}})",
                40,
                120,
                12,
                520,
                24),
            element(
                "text",
                "was a student of class {{student.classSection}} in session {{context.academicSessionId}}.",
                40,
                160,
                11,
                520,
                36),
            element(
                "text",
                "Leaving date: {{tc.leavingDate}} · Reason: {{tc.reason}}",
                40,
                210,
                11,
                520,
                24),
            element("text", "Remarks: {{tc.remarks}}", 40, 250, 10, 520, 48),
            element(
                "text",
                "Organization: {{context.organizationId}} · Branch: {{context.branchId}}",
                40,
                310,
                10,
                520,
                24),
            element("text", "Issued at {{tc.issuedAt}}", 40, 350, 10, 400, 24)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    return normalizeTemplate(t);
  }

  private Map<String, Object> feeReceiptTemplate() {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", "fee_receipt");
    t.put("name", "Fee Receipt");
    t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    t.put(
        "elements",
        List.of(
            element("heading", "FEE RECEIPT", 40, 60, 18, 500, 28),
            element(
                "text",
                "Received from {{payment.studentName}} ({{payment.admissionNo}})",
                40,
                120,
                12,
                520,
                24),
            element(
                "text",
                "Fee head: {{payment.feeHead}} · Amount: {{payment.amount}} · Mode: {{payment.paymentMode}}",
                40,
                160,
                11,
                520,
                24),
            element(
                "text",
                "Organization: {{context.organizationId}} · Branch: {{context.branchId}} · Session: {{context.academicSessionId}}",
                40,
                200,
                10,
                520,
                24),
            element("box", "", 40, 240, 11, 400, 80),
            element("text", "Download: {{context.feeReceiptUrl}}", 40, 340, 10, 520, 24),
            element("text", "Issued at {{context.issuedAt}}", 40, 380, 10, 400, 24)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    return normalizeTemplate(t);
  }

  private static Map<String, Object> element(
      String type, String text, int x, int y, int fontSize, int width, int height) {
    Map<String, Object> el = new LinkedHashMap<>();
    el.put("type", type);
    el.put("text", text);
    el.put("x", x);
    el.put("y", y);
    el.put("fontSize", fontSize);
    el.put("width", width);
    el.put("height", height);
    return el;
  }
}
