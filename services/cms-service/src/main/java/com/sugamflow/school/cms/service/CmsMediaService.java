package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.integration.SubscriptionEntitlementsClient;
import com.sugamflow.school.cms.persistence.entity.CmsMediaAsset;
import com.sugamflow.school.cms.persistence.repo.CmsMediaAssetRepository;
import com.sugamflow.school.cms.persistence.repo.CmsPageRepository;
import com.sugamflow.school.cms.storage.MediaObjectStore;
import com.sugamflow.school.cms.web.dto.MediaAssetResponse;
import com.sugamflow.school.cms.web.dto.MediaRegisterRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

  public CmsMediaService(
      CmsMediaAssetRepository mediaRepository,
      CmsPageRepository pageRepository,
      SubscriptionEntitlementsClient entitlementsClient,
      MediaObjectStore objectStore) {
    this.mediaRepository = mediaRepository;
    this.pageRepository = pageRepository;
    this.entitlementsClient = entitlementsClient;
    this.objectStore = objectStore;
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
    return payload;
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
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    }
    long byteSize = file.getSize();
    assertStorageAllowed(organizationId, byteSize);

    UUID id = UUID.randomUUID();
    String original =
        file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
    String safeName = original.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    MediaObjectStore.StoredObject stored;
    try {
      stored =
          objectStore.store(
              organizationId,
              id,
              safeName,
              file.getContentType(),
              file.getInputStream(),
              byteSize);
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read upload");
    }

    CmsMediaAsset asset = new CmsMediaAsset();
    asset.setId(id);
    asset.setOrganizationId(organizationId);
    asset.setFileName(safeName);
    asset.setContentType(file.getContentType());
    asset.setUrl(stored.publicUrl());
    asset.setByteSize(byteSize);
    asset.setCreatedAt(Instant.now());
    MediaAssetResponse saved = toResponse(mediaRepository.save(asset));
    entitlementsClient.incrementUsage(organizationId, LIMIT_STORAGE, 1, "cms-media-upload");
    return saved;
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

  /** When S3/CDN is enabled, return the stored public URL for redirect. */
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
    CmsMediaAsset asset =
        mediaRepository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
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
        a.getCreatedAt());
  }

  private static double roundGb(long bytes) {
    return Math.round((bytes / (double) BYTES_PER_GB) * 1000.0) / 1000.0;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
