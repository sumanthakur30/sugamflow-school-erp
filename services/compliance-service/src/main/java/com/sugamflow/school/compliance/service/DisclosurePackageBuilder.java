package com.sugamflow.school.compliance.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.integration.MasterDataClient;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentEntity;
import com.sugamflow.school.compliance.persistence.entity.InfrastructureAssetEntity;
import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentRepository;
import com.sugamflow.school.compliance.persistence.repo.InfrastructureAssetRepository;
import com.sugamflow.school.compliance.persistence.repo.SchoolComplianceProfileRepository;

/** Builds CBSE-style mandatory disclosure HTML + JSON snapshot (no dual CMS stack). */
@Component
public class DisclosurePackageBuilder {
  public static final String DEFAULT_SLUG = "mandatory-public-disclosure";
  public static final String DEFAULT_TITLE = "Mandatory Public Disclosure";

  private final SchoolComplianceProfileRepository profileRepository;
  private final InfrastructureAssetRepository infrastructureRepository;
  private final ComplianceDocumentRepository documentRepository;
  private final MasterDataClient masterDataClient;

  public DisclosurePackageBuilder(
      SchoolComplianceProfileRepository profileRepository,
      InfrastructureAssetRepository infrastructureRepository,
      ComplianceDocumentRepository documentRepository,
      MasterDataClient masterDataClient) {
    this.profileRepository = profileRepository;
    this.infrastructureRepository = infrastructureRepository;
    this.documentRepository = documentRepository;
    this.masterDataClient = masterDataClient;
  }

  public BuiltPackage build(TenantScope scope) {
    String org = scope.organizationId();
    SchoolComplianceProfileEntity profile =
        profileRepository.findByOrganizationId(org).orElse(null);
    List<InfrastructureAssetEntity> infra =
        infrastructureRepository.findByOrganizationIdAndActiveTrueOrderByCategoryAscNameAsc(org);
    List<ComplianceDocumentEntity> docs =
        documentRepository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(org);
    List<Map<String, Object>> staff = masterDataClient.listStaffProjections(scope);

    List<String> warnings = new ArrayList<>();
    if (profile == null) {
      warnings.add("School compliance profile is missing.");
    }
    if (infra.isEmpty()) {
      warnings.add("No infrastructure inventory recorded.");
    }
    if (docs.isEmpty()) {
      warnings.add("No documents in compliance vault.");
    }
    if (staff.isEmpty()) {
      warnings.add("No staff records available for teacher list.");
    }

    String schoolName =
        profile != null && profile.getSchoolName() != null && !profile.getSchoolName().isBlank()
            ? profile.getSchoolName()
            : org;

    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("organizationId", org);
    snapshot.put("schoolName", schoolName);
    snapshot.put(
        "boardCode", profile != null && profile.getBoardCode() != null ? profile.getBoardCode() : "CBSE");
    snapshot.put(
        "packKey", profile != null ? profile.getActivePackKey() : null);
    snapshot.put("generatedAt", Instant.now().toString());
    snapshot.put("generalInformation", generalInfo(profile, schoolName));
    snapshot.put("documents", documentsSnapshot(docs));
    snapshot.put("infrastructure", infrastructureSnapshot(infra));
    snapshot.put("staff", staffSnapshot(staff));
    snapshot.put(
        "feeStructureNote",
        "Fee structure is maintained in the school fee module; contact the school office for the current session schedule.");

    String bodyHtml = toHtml(schoolName, profile, docs, infra, staff);
    String boardLabel =
        profile != null && profile.getBoardCode() != null && !profile.getBoardCode().isBlank()
            ? profile.getBoardCode()
            : "Board";
    String summary =
        boardLabel
            + "-mandated public disclosure for "
            + schoolName
            + " — general information, documents, infrastructure and staff.";

    return new BuiltPackage(schoolName, summary, bodyHtml, snapshot, warnings);
  }

  private static Map<String, Object> generalInfo(
      SchoolComplianceProfileEntity profile, String schoolName) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("schoolName", schoolName);
    if (profile == null) {
      return m;
    }
    m.put("boardCode", profile.getBoardCode());
    m.put("activePackKey", profile.getActivePackKey());
    m.put("affiliationNumber", profile.getAffiliationNumber());
    m.put("schoolCode", profile.getSchoolCode());
    m.put("udisePlus", profile.getUdisePlus());
    m.put("address", profile.getAddressLine());
    m.put("city", profile.getCity());
    m.put("stateCode", profile.getStateCode());
    m.put("pincode", profile.getPincode());
    m.put("principalName", profile.getPrincipalName());
    m.put("principalEmail", profile.getPrincipalEmail());
    m.put("schoolEmail", profile.getSchoolEmail());
    m.put("schoolPhone", profile.getSchoolPhone());
    m.put("trustSocietyName", profile.getTrustSocietyName());
    return m;
  }

  private static List<Map<String, Object>> documentsSnapshot(List<ComplianceDocumentEntity> docs) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (ComplianceDocumentEntity d : docs) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("docType", d.getDocType());
      row.put("title", d.getTitle());
      row.put("referenceNo", d.getReferenceNo());
      row.put("issuedOn", d.getIssuedOn() != null ? d.getIssuedOn().toString() : null);
      row.put("expiresOn", d.getExpiresOn() != null ? d.getExpiresOn().toString() : null);
      if (d.getExternalUrl() != null && !d.getExternalUrl().isBlank()) {
        row.put("url", d.getExternalUrl());
      }
      out.add(row);
    }
    return out;
  }

  private static List<Map<String, Object>> infrastructureSnapshot(
      List<InfrastructureAssetEntity> infra) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (InfrastructureAssetEntity a : infra) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("category", a.getCategory());
      row.put("name", a.getName());
      row.put("quantity", a.getQuantity());
      row.put("capacity", a.getCapacity());
      row.put("condition", a.getConditionCode());
      out.add(row);
    }
    return out;
  }

  private static List<Map<String, Object>> staffSnapshot(List<Map<String, Object>> staff) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> s : staff) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("employeeNo", str(s.get("employeeNo")));
      row.put("fullName", str(s.get("fullName")));
      row.put("designation", str(s.get("designation")));
      row.put("department", str(s.get("department")));
      row.put("qualification", firstNonBlank(str(s.get("qualification")), str(s.get("highestQualification"))));
      out.add(row);
    }
    return out;
  }

  private static String toHtml(
      String schoolName,
      SchoolComplianceProfileEntity profile,
      List<ComplianceDocumentEntity> docs,
      List<InfrastructureAssetEntity> infra,
      List<Map<String, Object>> staff) {
    StringBuilder html = new StringBuilder();
    html.append("<article class=\"mandatory-disclosure\">");
    html.append("<p><em>Last generated for public disclosure. Source: SugamFlow Compliance.</em></p>");

    html.append("<h2>A. General Information</h2>");
    html.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%\">");
    row(html, "Name of school", schoolName);
    if (profile != null) {
      row(html, "Board", profile.getBoardCode());
      row(html, "Compliance pack", profile.getActivePackKey());
      row(html, "Affiliation number", profile.getAffiliationNumber());
      row(html, "School code", profile.getSchoolCode());
      row(html, "UDISE+", profile.getUdisePlus());
      row(html, "Address", profile.getAddressLine());
      row(html, "City / State / PIN", join(profile.getCity(), profile.getStateCode(), profile.getPincode()));
      row(html, "Principal", profile.getPrincipalName());
      row(html, "Principal email", profile.getPrincipalEmail());
      row(html, "School email", profile.getSchoolEmail());
      row(html, "School phone", profile.getSchoolPhone());
      row(html, "Trust / Society", profile.getTrustSocietyName());
    }
    html.append("</table>");

    html.append("<h2>B. Documents and Information</h2>");
    if (docs.isEmpty()) {
      html.append("<p>No documents uploaded in the compliance vault yet.</p>");
    } else {
      html.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%\">");
      html.append("<thead><tr><th>Type</th><th>Title</th><th>Reference</th><th>Expires</th><th>Link</th></tr></thead><tbody>");
      for (ComplianceDocumentEntity d : docs) {
        html.append("<tr>");
        html.append("<td>").append(esc(d.getDocType())).append("</td>");
        html.append("<td>").append(esc(d.getTitle())).append("</td>");
        html.append("<td>").append(esc(d.getReferenceNo())).append("</td>");
        html.append("<td>")
            .append(esc(d.getExpiresOn() == null ? "—" : d.getExpiresOn().toString()))
            .append("</td>");
        if (d.getExternalUrl() != null && !d.getExternalUrl().isBlank()) {
          html.append("<td><a href=\"")
              .append(escAttr(d.getExternalUrl()))
              .append("\" target=\"_blank\" rel=\"noopener\">View</a></td>");
        } else {
          html.append("<td>—</td>");
        }
        html.append("</tr>");
      }
      html.append("</tbody></table>");
    }

    html.append("<h2>C. Infrastructure</h2>");
    if (infra.isEmpty()) {
      html.append("<p>Infrastructure inventory not yet recorded.</p>");
    } else {
      html.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%\">");
      html.append("<thead><tr><th>Category</th><th>Name</th><th>Qty</th><th>Condition</th></tr></thead><tbody>");
      for (InfrastructureAssetEntity a : infra) {
        html.append("<tr>");
        html.append("<td>").append(esc(a.getCategory())).append("</td>");
        html.append("<td>").append(esc(a.getName())).append("</td>");
        html.append("<td>").append(a.getQuantity()).append("</td>");
        html.append("<td>").append(esc(a.getConditionCode())).append("</td>");
        html.append("</tr>");
      }
      html.append("</tbody></table>");
    }

    html.append("<h2>D. Staff (Teachers &amp; Employees)</h2>");
    if (staff.isEmpty()) {
      html.append("<p>Staff directory is empty.</p>");
    } else {
      html.append("<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse;width:100%\">");
      html.append("<thead><tr><th>Name</th><th>Designation</th><th>Department</th><th>Employee No</th></tr></thead><tbody>");
      for (Map<String, Object> s : staff) {
        html.append("<tr>");
        html.append("<td>").append(esc(str(s.get("fullName")))).append("</td>");
        html.append("<td>").append(esc(str(s.get("designation")))).append("</td>");
        html.append("<td>").append(esc(str(s.get("department")))).append("</td>");
        html.append("<td>").append(esc(str(s.get("employeeNo")))).append("</td>");
        html.append("</tr>");
      }
      html.append("</tbody></table>");
    }

    html.append("<h2>E. Fee Structure</h2>");
    html.append(
        "<p>Current session fee structure is maintained in the school fee module. Please contact the school office or refer to the fee schedule published by the school.</p>");

    html.append("</article>");
    return html.toString();
  }

  private static void row(StringBuilder html, String label, String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    html.append("<tr><th style=\"text-align:left;width:35%\">")
        .append(esc(label))
        .append("</th><td>")
        .append(esc(value))
        .append("</td></tr>");
  }

  private static String join(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (String p : parts) {
      if (p == null || p.isBlank()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(", ");
      }
      sb.append(p.trim());
    }
    return sb.toString();
  }

  private static String esc(String v) {
    if (v == null) {
      return "";
    }
    return v.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }

  private static String escAttr(String v) {
    return esc(v).replace("'", "&#39;");
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return "";
  }

  public record BuiltPackage(
      String schoolName,
      String summary,
      String bodyHtml,
      Map<String, Object> snapshot,
      List<String> warnings) {}
}
