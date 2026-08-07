package com.sugamflow.school.compliance.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.compliance.config.ComplianceProperties;
import com.sugamflow.school.compliance.dto.CampaignResponse;
import com.sugamflow.school.compliance.integration.MasterDataClient;
import com.sugamflow.school.compliance.persistence.entity.ComplianceDocumentEntity;
import com.sugamflow.school.compliance.persistence.entity.ComplianceFieldMapEntity;
import com.sugamflow.school.compliance.persistence.entity.ExportArtifactEntity;
import com.sugamflow.school.compliance.persistence.entity.InfrastructureAssetEntity;
import com.sugamflow.school.compliance.persistence.entity.SchoolComplianceProfileEntity;
import com.sugamflow.school.compliance.persistence.entity.SubmissionCampaignEntity;
import com.sugamflow.school.compliance.persistence.repo.ComplianceDocumentRepository;
import com.sugamflow.school.compliance.persistence.repo.ComplianceFieldMapRepository;
import com.sugamflow.school.compliance.persistence.repo.ExportArtifactRepository;
import com.sugamflow.school.compliance.persistence.repo.InfrastructureAssetRepository;
import com.sugamflow.school.compliance.persistence.repo.SchoolComplianceProfileRepository;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class ExportGeneratorService {
  private final MasterDataClient masterDataClient;
  private final ComplianceFieldMapRepository fieldMapRepository;
  private final SchoolComplianceProfileRepository profileRepository;
  private final InfrastructureAssetRepository infrastructureRepository;
  private final ComplianceDocumentRepository documentRepository;
  private final ExportArtifactRepository artifactRepository;
  private final ComplianceProperties properties;
  private final ObjectMapper objectMapper;

  public ExportGeneratorService(
      MasterDataClient masterDataClient,
      ComplianceFieldMapRepository fieldMapRepository,
      SchoolComplianceProfileRepository profileRepository,
      InfrastructureAssetRepository infrastructureRepository,
      ComplianceDocumentRepository documentRepository,
      ExportArtifactRepository artifactRepository,
      ComplianceProperties properties,
      ObjectMapper objectMapper)
      throws IOException {
    this.masterDataClient = masterDataClient;
    this.fieldMapRepository = fieldMapRepository;
    this.profileRepository = profileRepository;
    this.infrastructureRepository = infrastructureRepository;
    this.documentRepository = documentRepository;
    this.artifactRepository = artifactRepository;
    this.properties = properties;
    this.objectMapper = objectMapper;
    Files.createDirectories(Paths.get(properties.getExports().getStorageDir()));
  }

  @Transactional
  public List<CampaignResponse.ExportArtifactResponse> generate(
      TenantScope scope, SubmissionCampaignEntity campaign, Set<String> formats) {
    try {
      Set<String> wanted =
          formats == null || formats.isEmpty()
              ? Set.of("CSV", "JSON")
              : formats.stream().map(f -> f.trim().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());

      List<Map<String, Object>> students = masterDataClient.listStudentProjections(scope);
      List<Map<String, Object>> staff = masterDataClient.listStaffProjections(scope);
      SchoolComplianceProfileEntity profile =
          profileRepository.findByOrganizationId(scope.organizationId()).orElse(null);
      List<InfrastructureAssetEntity> infra =
          infrastructureRepository.findByOrganizationIdAndActiveTrueOrderByCategoryAscNameAsc(
              scope.organizationId());
      List<ComplianceDocumentEntity> docs =
          documentRepository.findByOrganizationIdAndActiveTrueOrderByExpiresOnAscTitleAsc(
              scope.organizationId());

      String board = campaign.getBoardCode() != null ? campaign.getBoardCode() : "CBSE";
      List<ComplianceFieldMapEntity> studentMaps =
          fieldMapRepository.findByBoardCodeAndEntityTypeAndActiveTrueOrderBySortOrderAsc(
              board, "STUDENT");
      List<ComplianceFieldMapEntity> staffMaps =
          fieldMapRepository.findByBoardCodeAndEntityTypeAndActiveTrueOrderBySortOrderAsc(
              board, "STAFF");

      Path campaignDir =
          Paths.get(properties.getExports().getStorageDir())
              .resolve(scope.organizationId())
              .resolve(String.valueOf(campaign.getId()))
              .resolve(String.valueOf(Instant.now().toEpochMilli()));
      Files.createDirectories(campaignDir);

      List<ExportArtifactEntity> created = new ArrayList<>();
      String actor = scope.userId();

      if (wanted.contains("CSV")) {
        created.add(
            writeBytes(
                scope,
                campaign,
                campaignDir,
                "STUDENTS",
                "CSV",
                "students.csv",
                "text/csv",
                toCsv(studentMaps, students).getBytes(StandardCharsets.UTF_8),
                actor));
        created.add(
            writeBytes(
                scope,
                campaign,
                campaignDir,
                "STAFF",
                "CSV",
                "staff.csv",
                "text/csv",
                toCsv(staffMaps, staff).getBytes(StandardCharsets.UTF_8),
                actor));
        created.add(
            writeBytes(
                scope,
                campaign,
                campaignDir,
                "INFRASTRUCTURE",
                "CSV",
                "infrastructure.csv",
                "text/csv",
                infraCsv(infra).getBytes(StandardCharsets.UTF_8),
                actor));
        created.add(
            writeBytes(
                scope,
                campaign,
                campaignDir,
                "DOCUMENTS",
                "CSV",
                "documents.csv",
                "text/csv",
                docsCsv(docs).getBytes(StandardCharsets.UTF_8),
                actor));
      }

      if (wanted.contains("JSON")) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("campaignId", campaign.getId());
        pack.put("boardCode", campaign.getBoardCode());
        pack.put("packKey", campaign.getPackKey());
        pack.put("title", campaign.getTitle());
        pack.put("exportedAt", Instant.now().toString());
        pack.put("organizationId", scope.organizationId());
        pack.put("profile", profileSnapshot(profile));
        pack.put("students", projectRows(studentMaps, students));
        pack.put("staff", projectRows(staffMaps, staff));
        pack.put(
            "infrastructure",
            infra.stream()
                .map(
                    a ->
                        Map.of(
                            "category", nullToEmpty(a.getCategory()),
                            "name", nullToEmpty(a.getName()),
                            "quantity", a.getQuantity(),
                            "condition", nullToEmpty(a.getConditionCode())))
                .toList());
        pack.put(
            "documents",
            docs.stream()
                .map(
                    d -> {
                      Map<String, Object> m = new LinkedHashMap<>();
                      m.put("docType", d.getDocType());
                      m.put("title", d.getTitle());
                      m.put("expiresOn", d.getExpiresOn() != null ? d.getExpiresOn().toString() : null);
                      m.put("referenceNo", d.getReferenceNo());
                      return m;
                    })
                .toList());
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(pack);
        created.add(
            writeBytes(
                scope,
                campaign,
                campaignDir,
                "PACKAGE",
                "JSON",
                "compliance-package.json",
                "application/json",
                json,
                actor));
      }

      if (created.isEmpty()) {
        throw new ComplianceException(
            "BAD_FORMAT", "No supported export formats requested (use CSV and/or JSON)", HttpStatus.BAD_REQUEST);
      }
      return created.stream().map(this::toResponse).toList();
    } catch (ComplianceException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ComplianceException(
          "EXPORT_FAILED", "Export generation failed: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  public Path resolveArtifactPath(ExportArtifactEntity artifact) {
    Path path = Paths.get(artifact.getStoragePath()).normalize();
    Path root = Paths.get(properties.getExports().getStorageDir()).toAbsolutePath().normalize();
    if (!path.toAbsolutePath().normalize().startsWith(root)) {
      throw new ComplianceException("FORBIDDEN", "Invalid artifact path", HttpStatus.FORBIDDEN);
    }
    if (!Files.exists(path)) {
      throw new ComplianceException("NOT_FOUND", "Artifact file not found", HttpStatus.NOT_FOUND);
    }
    return path;
  }

  private ExportArtifactEntity writeBytes(
      TenantScope scope,
      SubmissionCampaignEntity campaign,
      Path dir,
      String key,
      String format,
      String fileName,
      String contentType,
      byte[] bytes,
      String actor)
      throws IOException {
    Path target = dir.resolve(fileName);
    Files.write(target, bytes);
    ExportArtifactEntity entity = new ExportArtifactEntity();
    entity.setOrganizationId(scope.organizationId());
    entity.setCampaignId(campaign.getId());
    entity.setArtifactKey(key);
    entity.setFormatCode(format);
    entity.setFileName(fileName);
    entity.setStoragePath(target.toAbsolutePath().normalize().toString());
    entity.setContentType(contentType);
    entity.setFileSize(bytes.length);
    entity.setChecksumSha256(sha256(bytes));
    entity.setCreatedBy(actor);
    return artifactRepository.save(entity);
  }

  private static List<Map<String, Object>> projectRows(
      List<ComplianceFieldMapEntity> maps, List<Map<String, Object>> rows) {
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map<String, Object> row : rows) {
      Map<String, Object> projected = new LinkedHashMap<>();
      projected.put("id", str(row.get("id")));
      for (ComplianceFieldMapEntity map : maps) {
        projected.put(map.getFieldKey(), resolve(row, map.getSourcePath()));
      }
      out.add(projected);
    }
    return out;
  }

  private static String toCsv(List<ComplianceFieldMapEntity> maps, List<Map<String, Object>> rows) {
    List<String> headers = new ArrayList<>();
    headers.add("id");
    for (ComplianceFieldMapEntity map : maps) {
      headers.add(map.getFieldKey());
    }
    StringBuilder sb = new StringBuilder();
    sb.append(headers.stream().map(ExportGeneratorService::csvEscape).collect(Collectors.joining(",")));
    sb.append('\n');
    for (Map<String, Object> row : rows) {
      List<String> cols = new ArrayList<>();
      cols.add(str(row.get("id")));
      for (ComplianceFieldMapEntity map : maps) {
        cols.add(resolve(row, map.getSourcePath()));
      }
      sb.append(cols.stream().map(ExportGeneratorService::csvEscape).collect(Collectors.joining(",")));
      sb.append('\n');
    }
    return sb.toString();
  }

  private static String infraCsv(List<InfrastructureAssetEntity> rows) {
    StringBuilder sb = new StringBuilder("category,name,quantity,capacity,condition,location\n");
    for (InfrastructureAssetEntity a : rows) {
      sb.append(csvEscape(a.getCategory())).append(',')
          .append(csvEscape(a.getName())).append(',')
          .append(a.getQuantity()).append(',')
          .append(a.getCapacity() == null ? "" : a.getCapacity()).append(',')
          .append(csvEscape(a.getConditionCode())).append(',')
          .append(csvEscape(a.getLocationNote())).append('\n');
    }
    return sb.toString();
  }

  private static String docsCsv(List<ComplianceDocumentEntity> rows) {
    StringBuilder sb = new StringBuilder("docType,title,referenceNo,issuer,issuedOn,expiresOn,status\n");
    for (ComplianceDocumentEntity d : rows) {
      sb.append(csvEscape(d.getDocType())).append(',')
          .append(csvEscape(d.getTitle())).append(',')
          .append(csvEscape(d.getReferenceNo())).append(',')
          .append(csvEscape(d.getIssuer())).append(',')
          .append(d.getIssuedOn() == null ? "" : d.getIssuedOn()).append(',')
          .append(d.getExpiresOn() == null ? "" : d.getExpiresOn()).append(',')
          .append(csvEscape(d.getStatus())).append('\n');
    }
    return sb.toString();
  }

  private static Map<String, Object> profileSnapshot(SchoolComplianceProfileEntity profile) {
    if (profile == null) {
      return Map.of();
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("schoolName", profile.getSchoolName());
    m.put("affiliationNumber", profile.getAffiliationNumber());
    m.put("schoolCode", profile.getSchoolCode());
    m.put("udisePlus", profile.getUdisePlus());
    m.put("principalName", profile.getPrincipalName());
    m.put("principalMobile", profile.getPrincipalMobile());
    m.put("principalEmail", profile.getPrincipalEmail());
    m.put("boardCode", profile.getBoardCode());
    m.put("activePackKey", profile.getActivePackKey());
    return m;
  }

  private CampaignResponse.ExportArtifactResponse toResponse(ExportArtifactEntity e) {
    return new CampaignResponse.ExportArtifactResponse(
        e.getId(),
        e.getArtifactKey(),
        e.getFormatCode(),
        e.getFileName(),
        e.getContentType(),
        e.getFileSize(),
        e.getChecksumSha256(),
        e.getCreatedAt());
  }

  private static String resolve(Map<String, Object> row, String path) {
    if (path == null || row == null) {
      return "";
    }
    if (!path.contains(".")) {
      return str(row.get(path));
    }
    Object cur = row;
    for (String part : path.split("\\.")) {
      if (!(cur instanceof Map<?, ?> map)) {
        return "";
      }
      cur = map.get(part);
    }
    return str(cur);
  }

  private static String csvEscape(String value) {
    String v = value == null ? "" : value;
    if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
      return "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  private static String str(Object v) {
    return v == null ? "" : String.valueOf(v).trim();
  }

  private static String nullToEmpty(String v) {
    return v == null ? "" : v;
  }

  private static String sha256(byte[] bytes) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(bytes));
    } catch (Exception ex) {
      return null;
    }
  }
}
