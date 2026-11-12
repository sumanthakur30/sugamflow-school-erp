package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.integration.SubscriptionEntitlementsClient;
import com.sugamflow.school.cms.persistence.entity.CmsMediaAsset;
import com.sugamflow.school.cms.persistence.repo.CmsMediaAssetRepository;
import com.sugamflow.school.cms.persistence.repo.CmsPageRepository;
import com.sugamflow.school.cms.storage.MediaObjectStore;
import com.sugamflow.school.cms.web.dto.MediaAssetResponse;
import com.sugamflow.school.cms.web.dto.MediaRegisterRequest;
import com.sugamflow.school.cms.web.dto.MediaUpdateRequest;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsMediaService {

  private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;
  private static final String LIMIT_STORAGE = "website_storage_gb";
  private static final String LIMIT_PAGES = "website_pages";

  private final CmsMediaAssetRepository mediaRepository;
  private final CmsPageRepository pageRepository;
  private final SubscriptionEntitlementsClient entitlementsClient;
  private final MediaObjectStore objectStore;
  private final JdbcTemplate jdbc;
  private final RestClient.Builder restClientBuilder;
  private final String websiteBaseUrl;

  public CmsMediaService(
      CmsMediaAssetRepository mediaRepository,
      CmsPageRepository pageRepository,
      SubscriptionEntitlementsClient entitlementsClient,
      MediaObjectStore objectStore,
      JdbcTemplate jdbc,
      RestClient.Builder restClientBuilder,
      @Value("${cms.integrations.website-base-url:http://localhost:8200}") String websiteBaseUrl) {
    this.mediaRepository = mediaRepository;
    this.pageRepository = pageRepository;
    this.entitlementsClient = entitlementsClient;
    this.objectStore = objectStore;
    this.jdbc = jdbc;
    this.restClientBuilder = restClientBuilder;
    this.websiteBaseUrl = websiteBaseUrl.replaceAll("/$", "");
  }

  public List<MediaAssetResponse> list(String organizationId) {
    return mediaRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
        .map(this::toResponse)
        .toList();
  }

  public Map<String, Object> usage(String organizationId) {
    long usedBytes = mediaRepository.sumBytesByOrganizationId(organizationId);
    long pageCount = pageRepository.countByOrganizationId(organizationId);
    Map<String, Long> limits = entitlementsClient.limits(organizationId);
    Long storageGb = limits.get(LIMIT_STORAGE);
    Long pagesLimit = limits.get(LIMIT_PAGES);

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("organizationId", organizationId);
    payload.put("usedBytes", usedBytes);
    payload.put("usedGb", roundGb(usedBytes));
    payload.put("storageLimitGb", storageGb);
    payload.put("pageCount", pageCount);
    payload.put("pageLimit", pagesLimit);
    payload.put("assetCount", mediaRepository.countByOrganizationId(organizationId));
    payload.put("storageBackend", objectStore.remoteEnabled() ? "s3" : "local");
    payload.put(
        "recent",
        mediaRepository.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
            .limit(5)
            .map(this::toResponse)
            .toList());
    return payload;
  }

  public List<String> references(String organizationId, UUID id) {
    CmsMediaAsset asset = require(organizationId, id);
    return findReferences(organizationId, asset);
  }

  @Transactional
  public MediaAssetResponse register(String organizationId, MediaRegisterRequest request) {
    long byteSize = request.byteSize() == null ? 0L : request.byteSize();
    assertStorageAllowed(organizationId, byteSize);

    CmsMediaAsset asset = new CmsMediaAsset();
    asset.setId(UUID.randomUUID());
    asset.setOrganizationId(organizationId);
    asset.setFileName(request.fileName().trim());
    asset.setContentType(blankToNull(request.contentType()));
    asset.setUrl(request.url().trim());
    asset.setByteSize(byteSize);
    asset.setCreatedAt(Instant.now());
    MediaAssetResponse saved = toResponse(mediaRepository.save(asset));
    if (byteSize > 0) {
      entitlementsClient.incrementUsage(organizationId, LIMIT_STORAGE, 1, "cms-media-register");
    }
    return saved;
  }

  @Transactional
  public MediaAssetResponse upload(String organizationId, MultipartFile file) {
    UploadRules.Prepared prepared = UploadRules.prepareImage(file);
    assertStorageAllowed(organizationId, prepared.bytes().length);

    UUID id = UUID.randomUUID();
    MediaObjectStore.StoredObject stored =
        objectStore.store(
            organizationId,
            id,
            prepared.fileName(),
            prepared.contentType(),
            new ByteArrayInputStream(prepared.bytes()),
            prepared.bytes().length);

    CmsMediaAsset asset = new CmsMediaAsset();
    asset.setId(id);
    asset.setOrganizationId(organizationId);
    asset.setFileName(prepared.fileName());
    asset.setContentType(prepared.contentType());
    asset.setUrl(stored.publicUrl());
    asset.setByteSize((long) prepared.bytes().length);
    asset.setTitle(prepared.fileName());
    asset.setCreatedAt(Instant.now());
    MediaAssetResponse saved = toResponse(mediaRepository.save(asset));
    entitlementsClient.incrementUsage(organizationId, LIMIT_STORAGE, 1, "cms-media-upload");
    return saved;
  }

  @Transactional
  public MediaAssetResponse replace(String organizationId, UUID id, MultipartFile file) {
    CmsMediaAsset asset = require(organizationId, id);
    UploadRules.Prepared prepared = UploadRules.prepareImage(file);
    long delta = prepared.bytes().length - (asset.getByteSize() == null ? 0L : asset.getByteSize());
    if (delta > 0) {
      assertStorageAllowed(organizationId, delta);
    }
    MediaObjectStore.StoredObject stored =
        objectStore.replace(
            organizationId,
            id,
            prepared.fileName(),
            prepared.contentType(),
            new ByteArrayInputStream(prepared.bytes()),
            prepared.bytes().length);
    asset.setFileName(prepared.fileName());
    asset.setContentType(prepared.contentType());
    asset.setUrl(stored.publicUrl());
    asset.setByteSize((long) prepared.bytes().length);
    return toResponse(mediaRepository.save(asset));
  }

  /** Stores a validated file, reusing the same id and public URL when {@code existingId} is set. */
  @Transactional
  public MediaAssetResponse savePrepared(String organizationId, UUID existingId, UploadRules.Prepared prepared) {
    if (existingId == null) {
      assertStorageAllowed(organizationId, prepared.bytes().length);
      UUID id = UUID.randomUUID();
      MediaObjectStore.StoredObject stored =
          objectStore.store(
              organizationId,
              id,
              prepared.fileName(),
              prepared.contentType(),
              new ByteArrayInputStream(prepared.bytes()),
              prepared.bytes().length);
      CmsMediaAsset asset = new CmsMediaAsset();
      asset.setId(id);
      asset.setOrganizationId(organizationId);
      asset.setFileName(prepared.fileName());
      asset.setContentType(prepared.contentType());
      asset.setUrl(stored.publicUrl());
      asset.setByteSize((long) prepared.bytes().length);
      asset.setTitle(prepared.fileName());
      asset.setCreatedAt(Instant.now());
      MediaAssetResponse saved = toResponse(mediaRepository.save(asset));
      entitlementsClient.incrementUsage(organizationId, LIMIT_STORAGE, 1, "cms-media-upload");
      return saved;
    }
    CmsMediaAsset asset = require(organizationId, existingId);
    long delta = prepared.bytes().length - (asset.getByteSize() == null ? 0L : asset.getByteSize());
    if (delta > 0) {
      assertStorageAllowed(organizationId, delta);
    }
    MediaObjectStore.StoredObject stored =
        objectStore.replace(
            organizationId,
            existingId,
            prepared.fileName(),
            prepared.contentType(),
            new ByteArrayInputStream(prepared.bytes()),
            prepared.bytes().length);
    asset.setFileName(prepared.fileName());
    asset.setContentType(prepared.contentType());
    asset.setUrl(stored.publicUrl());
    asset.setByteSize((long) prepared.bytes().length);
    return toResponse(mediaRepository.save(asset));
  }

  @Transactional
  public MediaAssetResponse update(String organizationId, UUID id, MediaUpdateRequest request) {
    CmsMediaAsset asset = require(organizationId, id);
    if (request != null) {
      asset.setTitle(blankToNull(request.title()));
      asset.setDescription(blankToNull(request.description()));
      asset.setAltText(blankToNull(request.altText()));
      asset.setCategory(blankToNull(request.category()));
    }
    return toResponse(mediaRepository.save(asset));
  }

  public Resource loadFile(String organizationId, UUID id) {
    return objectStore
        .loadLocal(organizationId, id)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    objectStore.remoteEnabled()
                        ? "Media is on CDN; use asset url"
                        : "Media file missing"));
  }

  public String remoteUrlOrNull(String organizationId, UUID id) {
    if (!objectStore.remoteEnabled()) {
      return null;
    }
    return mediaRepository
        .findByIdAndOrganizationId(id, organizationId)
        .map(CmsMediaAsset::getUrl)
        .orElse(null);
  }

  public MediaType mediaTypeFor(String organizationId, UUID id) {
    return mediaRepository
        .findByIdAndOrganizationId(id, organizationId)
        .map(CmsMediaAsset::getContentType)
        .filter(t -> t != null && !t.isBlank())
        .map(MediaType::parseMediaType)
        .orElse(MediaType.APPLICATION_OCTET_STREAM);
  }

  @Transactional
  public void delete(String organizationId, UUID id) {
    CmsMediaAsset asset = require(organizationId, id);
    List<String> used = findReferences(organizationId, asset);
    if (!used.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "Image is still used: " + String.join(", ", used));
    }
    mediaRepository.delete(asset);
    objectStore.delete(organizationId, id);
  }

  public void assertPageCreateAllowed(String organizationId) {
    Map<String, Long> limits = entitlementsClient.limits(organizationId);
    Long pageLimit = limits.get(LIMIT_PAGES);
    if (pageLimit == null || pageLimit < 0) {
      return;
    }
    long current = pageRepository.countByOrganizationId(organizationId);
    if (current >= pageLimit) {
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED,
          "Website page limit reached (" + pageLimit + "). Upgrade plan or archive pages.");
    }
  }

  public void recordPageCreated(String organizationId) {
    entitlementsClient.incrementUsage(organizationId, LIMIT_PAGES, 1, "cms-page-create");
  }

  private CmsMediaAsset require(String organizationId, UUID id) {
    return mediaRepository
        .findByIdAndOrganizationId(id, organizationId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
  }

  private List<String> findReferences(String organizationId, CmsMediaAsset asset) {
    String needle = "%" + escapeLike(asset.getId().toString()) + "%";
    String urlNeedle =
        asset.getUrl() == null || asset.getUrl().isBlank()
            ? needle
            : "%" + escapeLike(asset.getUrl()) + "%";
    List<String> hits = new ArrayList<>();
    count(hits, "page", organizationId, needle, urlNeedle, "cms_page", "body_html", "sections_json");
    count(hits, "gallery", organizationId, needle, urlNeedle, "cms_gallery_item", "image_url", "video_url");
    count(hits, "document", organizationId, needle, urlNeedle, "cms_document", "file_url", "file_name");
    count(hits, "news", organizationId, needle, urlNeedle, "cms_news", "cover_image_url", "body_html");
    count(hits, "profile", organizationId, needle, urlNeedle, "cms_profile", "photo_url", "full_name");
    if (referencedOnWebsite(organizationId, asset)) {
      hits.add("homepage");
    }
    return hits;
  }

  private void count(
      List<String> hits,
      String label,
      String organizationId,
      String idNeedle,
      String urlNeedle,
      String table,
      String columnA,
      String columnB) {
    String sql =
        "select count(*) from "
            + table
            + " where organization_id = ? and ("
            + "coalesce("
            + columnA
            + ",'') ilike ? escape '\\' or coalesce("
            + columnB
            + ",'') ilike ? escape '\\' or coalesce("
            + columnA
            + ",'') ilike ? escape '\\')";
    Integer found = jdbc.queryForObject(sql, Integer.class, organizationId, idNeedle, idNeedle, urlNeedle);
    if (found != null && found > 0) {
      hits.add(label);
    }
  }

  private boolean referencedOnWebsite(String organizationId, CmsMediaAsset asset) {
    try {
      Map<String, Object> body =
          restClientBuilder
              .build()
              .get()
              .uri(
                  websiteBaseUrl
                      + "/api/website/public/media-referenced?organizationId={org}&needle={needle}",
                  organizationId,
                  asset.getId().toString())
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      if (body == null) {
        return false;
      }
      Object data = body.get("data");
      if (data instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("referenced"))) {
        return true;
      }
      Map<String, Object> urlBody =
          restClientBuilder
              .build()
              .get()
              .uri(
                  websiteBaseUrl
                      + "/api/website/public/media-referenced?organizationId={org}&needle={needle}",
                  organizationId,
                  asset.getUrl() == null ? "" : asset.getUrl())
              .retrieve()
              .body(new ParameterizedTypeReference<Map<String, Object>>() {});
      Object urlData = urlBody == null ? null : urlBody.get("data");
      return urlData instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("referenced"));
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "Could not check homepage image use. Retry when website-service is available.");
    }
  }

  private void assertStorageAllowed(String organizationId, long additionalBytes) {
    Map<String, Long> limits = entitlementsClient.limits(organizationId);
    Long storageGb = limits.get(LIMIT_STORAGE);
    if (storageGb == null || storageGb < 0) {
      return;
    }
    long capBytes = storageGb * BYTES_PER_GB;
    long used = mediaRepository.sumBytesByOrganizationId(organizationId);
    if (used + additionalBytes > capBytes) {
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED,
          "Website storage quota exceeded ("
              + storageGb
              + " GB). Remove media or upgrade plan.");
    }
  }

  private MediaAssetResponse toResponse(CmsMediaAsset a) {
    return new MediaAssetResponse(
        a.getId(),
        a.getFileName(),
        a.getContentType(),
        a.getUrl(),
        a.getByteSize(),
        a.getCreatedAt(),
        a.getTitle(),
        a.getDescription(),
        a.getAltText(),
        a.getCategory());
  }

  private static double roundGb(long bytes) {
    return Math.round((bytes / (double) BYTES_PER_GB) * 1000.0) / 1000.0;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }
}
