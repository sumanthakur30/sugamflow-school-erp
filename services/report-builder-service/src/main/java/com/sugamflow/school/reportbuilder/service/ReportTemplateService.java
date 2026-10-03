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
  private final ReportTabularExportService tabularExportService;
  private final ConfigEngineClient engines;

  public ReportTemplateService(
      ReportTemplateRepository repo,
      ReportPdfRenderService pdfRenderService,
      ReportTabularExportService tabularExportService,
      ConfigEngineClient engines) {
    this.repo = repo;
    this.pdfRenderService = pdfRenderService;
    this.tabularExportService = tabularExportService;
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
    return renderDocument(org, key, body);
  }

  /** Parent ID card and transfer certificate. Caller already checked the child. */
  @Transactional
  public Map<String, Object> renderIssued(String org, String key, Map<String, Object> body) {
    return renderDocument(org, key, body);
  }

  private Map<String, Object> renderDocument(String org, String key, Map<String, Object> body) {
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
    String fmt = format.trim().toUpperCase();
    boolean registerKey =
        key != null
            && (key.endsWith("_register")
                || "student_directory".equals(key)
                || "student_profile".equals(key));
    if (tabularExportService.isTabular(data)
        && (registerKey || !"PDF".equals(fmt) || "PRINT".equals(fmt))) {
      // Prefer explicit title from template name when missing.
      if (!data.containsKey("title") || String.valueOf(data.get("title")).isBlank()) {
        data = new LinkedHashMap<>(data);
        data.put("title", String.valueOf(template.getOrDefault("name", key)));
      }
      return tabularExportService.export(fmt, data);
    }
    if (!"PDF".equals(fmt) && !"PRINT".equals(fmt)) {
      if (tabularExportService.isTabular(data)) {
        return tabularExportService.export(fmt, data);
      }
      throw new IllegalArgumentException(
          "Unsupported format: " + format + " (provide data.columns + data.rows for EXCEL/CSV)");
    }
    List<Map<String, Object>> records = recordPages(body);
    if (records != null) {
      return pdfRenderService.renderMany(template, records);
    }
    return pdfRenderService.render(template, data);
  }

  @Transactional
  public Map<String, Object> previewLayout(Map<String, Object> body) {
    requireFeature();
    Map<String, Object> template = normalizeTemplate(body != null ? body : Map.of());
    template.remove("records");
    template.remove("data");
    template.remove("format");
    List<Map<String, Object>> records = recordPages(body);
    if (records != null) {
      return pdfRenderService.renderMany(template, records);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> data =
        body != null && body.get("data") instanceof Map<?, ?> m
            ? (Map<String, Object>) m
            : ReportElementCatalog.samplePreviewData();
    return pdfRenderService.render(template, data);
  }

  /** Runtime pages. Empty when the caller did not send a records list. Capped so one request cannot load the whole school. */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> recordPages(Map<String, Object> body) {
    if (body == null || !(body.get("records") instanceof List<?> list) || list.isEmpty()) {
      return null;
    }
    List<Map<String, Object>> pages = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> map) {
        pages.add(new LinkedHashMap<>((Map<String, Object>) map));
      }
      if (pages.size() >= 80) {
        break;
      }
    }
    return pages.isEmpty() ? null : pages;
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
              "admit_card",
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
    ensureTemplate("attendance_register");
    ensureTemplate("admission_register");
    ensureTemplate("student_directory");
    ensureTemplate("student_profile");
    ensureFeeReceiptEnriched();
    ensureTransferCertificateEnriched();
    ensureStudentDocumentTemplates();
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
    if ("id_card".equals(key)) {
      return idCardTemplate();
    }
    if ("admit_card".equals(key)) {
      return admitCardTemplate();
    }
    if ("attendance_register".equals(key)
        || "admission_register".equals(key)
        || "student_directory".equals(key)
        || "student_profile".equals(key)) {
      return tabularTemplate(key);
    }
    if ("bonafide".equals(key)) {
      return certificateTemplate(
          "bonafide",
          "BONAFIDE CERTIFICATE",
          "This is to certify that {{student.name}} (Admission No: {{student.admissionNo}}) is a bona fide student of class {{student.classSection}}.");
    }
    if ("character_certificate".equals(key)) {
      return certificateTemplate(
          "character_certificate",
          "CHARACTER CERTIFICATE",
          "This is to certify that {{student.name}} (Admission No: {{student.admissionNo}}) of class {{student.classSection}} bears a good moral character.");
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

  private void ensureStudentDocumentTemplates() {
    refreshIdCardTemplates(idCardTemplate());
    upsertCanonicalTemplate("admit_card", admitCardTemplate(), "ADMIT CARD");
    upsertCanonicalTemplate("transfer_certificate", transferCertificateTemplate(), "TRANSFER CERTIFICATE");
    upsertCanonicalTemplate(
        "bonafide",
        certificateTemplate(
            "bonafide",
            "BONAFIDE CERTIFICATE",
            "This is to certify that {{student.name}} (Admission No: {{student.admissionNo}}) is a bona fide student of class {{student.classSection}}."),
        "context.verifyUrl");
    upsertCanonicalTemplate(
        "character_certificate",
        certificateTemplate(
            "character_certificate",
            "CHARACTER CERTIFICATE",
            "This is to certify that {{student.name}} (Admission No: {{student.admissionNo}}) of class {{student.classSection}} bears a good moral character."),
        "context.verifyUrl");
  }

  /** Replace seeded ID cards that are still the old A4 block. Leave a saved CR80 design alone. */
  private void refreshIdCardTemplates(Map<String, Object> canonical) {
    boolean hasGlobal = false;
    for (ReportTemplateEntity row : repo.findByTemplateKey("id_card")) {
      if (row.getOrganizationId() == null) {
        hasGlobal = true;
      }
      String payload = String.valueOf(row.getPayload());
      if (payload.contains("cr80-v4")) {
        continue;
      }
      Map<String, Object> copy = new LinkedHashMap<>(canonical);
      if (row.getOrganizationId() != null) {
        copy.put("organizationId", row.getOrganizationId());
      }
      row.setPayload(copy);
      row.setUpdatedAt(Instant.now());
      repo.save(row);
    }
    if (!hasGlobal) {
      ReportTemplateEntity created = new ReportTemplateEntity();
      created.setOrganizationId(null);
      created.setTemplateKey("id_card");
      created.setPayload(canonical);
      created.setUpdatedAt(Instant.now());
      repo.save(created);
    }
  }

  private void upsertCanonicalTemplate(
      String key, Map<String, Object> canonical, String marker) {
    repo.findByOrganizationIdIsNullAndTemplateKey(key)
        .ifPresentOrElse(
            e -> {
              String payload = String.valueOf(e.getPayload());
              // Refresh when QR/verify marker missing, or id_card still lacks photo slot.
              boolean hasQr = payload.contains(marker) && payload.contains("\"type\":\"qr\"");
              boolean needsPhoto =
                  "id_card".equals(key)
                      && (!payload.contains("photoBase64") || !payload.contains("penNumber"));
              if (hasQr && !needsPhoto) {
                if ("id_card".equals(key) && !payload.contains("photoDirectUrl")) {
                  rewriteIdCardPhotoBind(e);
                }
                return;
              }
              e.setPayload(canonical);
              e.setUpdatedAt(Instant.now());
              repo.save(e);
            },
            () -> {
              ReportTemplateEntity e = new ReportTemplateEntity();
              e.setOrganizationId(null);
              e.setTemplateKey(key);
              e.setPayload(canonical);
              e.setUpdatedAt(Instant.now());
              repo.save(e);
            });
  }

  @SuppressWarnings("unchecked")
  private void rewriteIdCardPhotoBind(ReportTemplateEntity entity) {
    Map<String, Object> payload = entity.getPayload();
    if (payload == null) {
      return;
    }
    Object raw = payload.get("elements");
    if (!(raw instanceof List<?> list)) {
      return;
    }
    List<Object> elements = new ArrayList<>();
    boolean changed = false;
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> source)) {
        elements.add(item);
        continue;
      }
      Map<String, Object> el = new LinkedHashMap<>((Map<String, Object>) source);
      String text = String.valueOf(el.getOrDefault("text", ""));
      String bind = String.valueOf(el.getOrDefault("bind", ""));
      if (text.contains("student.photoBase64") || "student.photoBase64".equals(bind)) {
        el.put("text", "{{student.photoDirectUrl}}");
        el.put("bind", "student.photoDirectUrl");
        el.putIfAbsent("objectFit", "cover");
        changed = true;
      }
      elements.add(el);
    }
    if (!changed) {
      return;
    }
    Map<String, Object> copy = new LinkedHashMap<>(payload);
    copy.put("elements", elements);
    entity.setPayload(copy);
    entity.setUpdatedAt(Instant.now());
    repo.save(entity);
  }

  /**
   * CR80 landscape card. Canvas is 324×204 CSS px (3.375in × 2.125in at 96dpi). The PDF page is
   * the same size in points, so print at 100% matches a physical ID card.
   */
  private Map<String, Object> idCardTemplate() {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", "id_card");
    t.put("name", "Student ID Card");
    t.put("layoutVersion", "cr80-v4");
    t.put("layout", Map.of("width", 324, "height", 204, "units", "px", "paper", "CR80"));
    t.put(
        "elements",
        List.of(
            styled(element("box", "", 0, 0, 0, 324, 204), "z", 0, "fillColor", "#FFFFFF", "borderWidth", 0),
            styled(element("box", "", 0, 0, 0, 324, 36), "z", 1, "fillColor", "#0E3A5D", "borderWidth", 0),
            styled(
                element("image", "{{context.logoUrl}}", 6, 5, 8, 26, 26),
                "z", 2,
                "bind", "context.logoUrl",
                "fillColor", "#FFFFFF",
                "borderRadius", 4,
                "borderWidth", 0,
                "objectFit", "cover"),
            styled(
                element("heading", "{{context.organizationName}}", 38, 5, 10, 278, 14),
                "z", 2, "bold", true, "color", "#FFFFFF"),
            styled(
                element(
                    "text",
                    "{{context.branchName}}  ·  {{context.sessionLabel}}",
                    38,
                    20,
                    7,
                    278,
                    12),
                "z", 2, "color", "#E2E8F0"),
            styled(
                element("image", "{{student.photoDirectUrl}}", 8, 42, 8, 48, 64),
                "z", 2,
                "bind", "student.photoDirectUrl",
                "objectFit", "cover",
                "borderWidth", 1,
                "borderColor", "#CBD5E1",
                "borderRadius", 4),
            styled(
                element("text", "{{student.name}}", 62, 40, 11, 142, 13),
                "z", 2, "bold", true, "color", "#0F172A"),
            styled(
                element("text", "Class  {{student.classSection}}", 62, 54, 7, 142, 11),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "{{student.rollLine}}", 62, 65, 7, 142, 11),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "DOB  {{student.dob}}", 62, 76, 7, 142, 11),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "Blood Group", 62, 88, 7, 68, 12),
                "z", 2, "color", "#1E293B"),
            styled(
                element("box", "", 132, 87, 0, 30, 14),
                "z", 2,
                "fillColor", "#9F1239",
                "borderWidth", 0,
                "borderRadius", 3),
            styled(
                element("text", "{{student.bloodGroup}}", 132, 88, 7, 30, 12),
                "z", 3, "bold", true, "align", "center", "color", "#FFFFFF"),
            styled(
                element("text", "Mobile  {{student.mobileNo}}", 62, 103, 7, 142, 11),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "Emergency  {{student.emergencyNo}}", 62, 115, 7, 142, 12),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "APAAR  {{student.apaarId}}", 210, 42, 7, 106, 11),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "PEN  {{student.penNumber}}", 210, 54, 7, 106, 11),
                "z", 2, "color", "#1E293B"),
            styled(
                element("box", "", 210, 68, 0, 106, 80),
                "z", 2,
                "fillColor", "#FFFFFF",
                "borderWidth", 1,
                "borderColor", "#E2E8F0",
                "borderRadius", 6),
            styled(
                element("qr", "{{context.verifyUrl}}", 228, 74, 8, 56, 56),
                "z", 3, "bind", "context.verifyUrl", "quietZone", 6),
            styled(
                element("text", "Scan to verify", 210, 132, 7, 106, 12),
                "z", 3, "align", "center", "color", "#1E293B"),
            styled(element("box", "", 0, 156, 0, 324, 48), "z", 1, "fillColor", "#F4F7FB", "borderWidth", 0),
            styled(
                element("text", "Transport  {{student.transportMode}}", 8, 162, 7, 140, 12),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "Issued  {{context.issuedAt}}", 8, 176, 7, 140, 12),
                "z", 2, "color", "#1E293B"),
            styled(
                element("text", "Valid  {{context.expiresAt}}", 8, 188, 7, 140, 12),
                "z", 2, "color", "#1E293B"),
            styled(element("line", "", 168, 176, 6, 70, 2), "z", 2, "color", "#334155", "borderWidth", 0.8),
            styled(
                element("text", "Principal", 156, 186, 6, 94, 12),
                "z", 2, "align", "center", "color", "#334155"),
            styled(
                element("box", "", 286, 164, 0, 26, 26),
                "z", 2,
                "fillColor", "#FFFFFF",
                "borderWidth", 1,
                "borderColor", "#94A3B8",
                "borderRadius", 13),
            styled(
                element("text", "Seal", 286, 171, 6, 26, 12),
                "z", 3, "align", "center", "color", "#334155"),
            styled(
                element("box", "", 1, 1, 0, 322, 202),
                "z", 9,
                "borderWidth", 1.25,
                "borderColor", "#C5D0DC",
                "borderRadius", 10)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    return normalizeTemplate(t);
  }

  private Map<String, Object> tabularTemplate(String key) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", key);
    t.put("name", key.replace('_', ' '));
    t.put("layoutMode", "TABULAR");
    t.put("layout", Map.of("width", 1123, "height", 794, "units", "px", "paper", "A4-landscape"));
    t.put(
        "elements",
        List.of(
            element("heading", key.replace('_', ' ').toUpperCase(), 40, 40, 16, 600, 28),
            element("text", "Tabular register — supply data.columns + data.rows", 40, 80, 10, 600, 24)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    return normalizeTemplate(t);
  }

  private Map<String, Object> certificateTemplate(String key, String title, String body) {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", key);
    t.put("name", key.replace('_', ' '));
    t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    t.put(
        "elements",
        List.of(
            element("heading", title, 40, 60, 18, 500, 28),
            element("text", body, 40, 120, 12, 520, 60),
            element(
                "text",
                "This certificate is issued on request. Reference: {{document.referenceNo}}",
                40,
                200,
                11,
                520,
                36),
            element(
                "text",
                "Organization: {{context.organizationId}} · Branch: {{context.branchId}} · Session: {{context.academicSessionId}}",
                40,
                260,
                10,
                520,
                36),
            element("qr", "{{context.verifyUrl}}", 40, 320, 11, 120, 120),
            element("text", "Scan QR to verify authenticity", 180, 360, 10, 300, 24),
            element("text", "Issued at {{context.issuedAt}}", 40, 470, 10, 400, 24)));
    t.put("charts", List.of());
    t.put("filters", List.of());
    t.put("calculatedFields", List.of());
    t.put("schedule", Map.of("enabled", false, "channels", List.of("EMAIL")));
    return normalizeTemplate(t);
  }

  private Map<String, Object> admitCardTemplate() {
    Map<String, Object> t = new LinkedHashMap<>();
    t.put("templateKey", "admit_card");
    t.put("name", "Admit Card");
    t.put("layout", Map.of("width", 794, "height", 1123, "units", "px", "paper", "A4"));
    t.put(
        "elements",
        List.of(
            element("heading", "ADMIT CARD", 40, 48, 18, 420, 28),
            element("text", "Name: {{student.name}}", 40, 110, 12, 420, 24),
            element("text", "Admission No: {{student.admissionNo}}", 40, 140, 12, 420, 24),
            element("text", "Class: {{student.classSection}}", 40, 170, 12, 420, 24),
            element("text", "Exam: {{exam.name}}", 40, 210, 12, 420, 24),
            element("text", "Roll No: {{student.rollNo}}", 40, 240, 12, 420, 24),
            element("text", "Session: {{context.academicSessionId}}", 40, 280, 11, 420, 24),
            element("text", "Bring this card to the examination hall.", 40, 330, 11, 480, 24),
            element("qr", "{{context.verifyUrl}}", 40, 380, 11, 100, 100)));
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

  private static Map<String, Object> styled(Map<String, Object> el, Object... pairs) {
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      el.put(String.valueOf(pairs[i]), pairs[i + 1]);
    }
    return el;
  }
}
