package com.sugamflow.school.settings.service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.persistence.entity.DesignAssetEntity;
import com.sugamflow.school.settings.persistence.repo.DesignAssetRepository;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Design Studio branding images (logos, splash, favicon, login background). JSON base64 upload —
 * same pattern as student photo vault; no MultipartFile stack.
 */
@Service
public class DesignAssetService {

  public static final String TYPE_SCHOOL_LOGO = "SCHOOL_LOGO";
  public static final String TYPE_LOGIN_LOGO = "LOGIN_LOGO";
  public static final String TYPE_FAVICON = "FAVICON";
  public static final String TYPE_MOBILE_SPLASH = "MOBILE_SPLASH";
  public static final String TYPE_WATERMARK = "WATERMARK";
  public static final String TYPE_LOGIN_BG = "LOGIN_BG";

  private static final Set<String> ASSET_TYPES =
      Set.of(
          TYPE_SCHOOL_LOGO,
          TYPE_LOGIN_LOGO,
          TYPE_FAVICON,
          TYPE_MOBILE_SPLASH,
          TYPE_WATERMARK,
          TYPE_LOGIN_BG);

  private static final Set<String> IMAGE_MIME =
      Set.of(
          "image/jpeg",
          "image/jpg",
          "image/png",
          "image/webp",
          "image/svg+xml",
          "image/x-icon",
          "image/vnd.microsoft.icon");

  private static final int MAX_KB_DEFAULT = 512;
  private static final int MAX_KB_BG = 1536;

  private final DesignAssetRepository repository;

  public DesignAssetService(DesignAssetRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public Map<String, Object> upload(Map<String, Object> body) {
    TenantScope scope = TenantContext.require();
    String type = normalizeType(String.valueOf(body.getOrDefault("type", "")));
    if (!ASSET_TYPES.contains(type)) {
      throw badRequest(
          "Unsupported asset type. Use SCHOOL_LOGO, LOGIN_LOGO, FAVICON, MOBILE_SPLASH, WATERMARK, or LOGIN_BG.");
    }

    String contentType =
        String.valueOf(body.getOrDefault("contentType", "application/octet-stream"))
            .trim()
            .toLowerCase(Locale.ROOT);
    String fileName =
        String.valueOf(body.getOrDefault("fileName", type.toLowerCase(Locale.ROOT))).trim();
    String b64 = String.valueOf(body.getOrDefault("contentBase64", "")).trim();
    if (b64.contains(",")) {
      b64 = b64.substring(b64.indexOf(',') + 1);
    }
    if (b64.isBlank()) {
      throw badRequest("contentBase64 is required");
    }

    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(b64);
    } catch (IllegalArgumentException ex) {
      throw badRequest("Invalid base64 content");
    }

    int maxKb = TYPE_LOGIN_BG.equals(type) || TYPE_MOBILE_SPLASH.equals(type) ? MAX_KB_BG : MAX_KB_DEFAULT;
    if (bytes.length > maxKb * 1024L) {
      throw badRequest("File exceeds maximum size of " + maxKb + " KB");
    }
    if (!IMAGE_MIME.contains(contentType) && !looksLikeImageName(fileName)) {
      throw badRequest("Image must be JPG, PNG, WEBP, SVG, or ICO");
    }
    if ("image/jpg".equals(contentType)) {
      contentType = "image/jpeg";
    }

    String branch =
        scope.branchId() != null && !scope.branchId().isBlank() ? scope.branchId() : "main";

    // One active asset per type per campus
    repository.deleteByOrganizationIdAndBranchIdAndAssetType(
        scope.organizationId(), branch, type);

    DesignAssetEntity entity = new DesignAssetEntity();
    entity.setId(UUID.randomUUID());
    entity.setOrganizationId(scope.organizationId());
    entity.setBranchId(branch);
    entity.setAssetType(type);
    entity.setFileName(fileName);
    entity.setContentType(contentType);
    entity.setContentBase64(b64);
    entity.setByteLength(bytes.length);
    entity.setCreatedBy(scope.userId());
    entity.setCreatedAt(Instant.now());
    DesignAssetEntity saved = repository.save(entity);
    return toDto(saved);
  }

  /** Public content fetch for login/shell — UUID is the access key. */
  @Transactional(readOnly = true)
  public DesignAssetEntity requirePublic(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
  }

  @Transactional(readOnly = true)
  public byte[] contentBytes(UUID id) {
    DesignAssetEntity entity = requirePublic(id);
    try {
      return Base64.getDecoder().decode(entity.getContentBase64());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Corrupt asset content");
    }
  }

  public static String brandingFieldFor(String assetType) {
    return switch (assetType) {
      case TYPE_SCHOOL_LOGO -> "schoolLogo";
      case TYPE_LOGIN_LOGO -> "loginLogo";
      case TYPE_FAVICON -> "favicon";
      case TYPE_MOBILE_SPLASH -> "mobileSplash";
      case TYPE_WATERMARK -> "watermark";
      case TYPE_LOGIN_BG -> "backgroundImage";
      default -> null;
    };
  }

  private Map<String, Object> toDto(DesignAssetEntity e) {
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", e.getId().toString());
    dto.put("organizationId", e.getOrganizationId());
    dto.put("branchId", e.getBranchId());
    dto.put("assetType", e.getAssetType());
    dto.put("fileName", e.getFileName());
    dto.put("contentType", e.getContentType());
    dto.put("byteLength", e.getByteLength());
    dto.put("url", contentUrl(e.getId()));
    dto.put("brandingField", brandingFieldFor(e.getAssetType()));
    dto.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
    return dto;
  }

  public static String contentUrl(UUID id) {
    return "/api/config/design-studio/assets/" + id + "/content";
  }

  private static String normalizeType(String raw) {
    return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
  }

  private static boolean looksLikeImageName(String fileName) {
    String lower = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
    return lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".webp")
        || lower.endsWith(".svg")
        || lower.endsWith(".ico");
  }

  private static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
