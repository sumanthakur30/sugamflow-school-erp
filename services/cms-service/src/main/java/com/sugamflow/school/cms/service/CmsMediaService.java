package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.integration.SubscriptionEntitlementsClient;
import com.sugamflow.school.cms.persistence.entity.CmsMediaAsset;
import com.sugamflow.school.cms.persistence.repo.CmsMediaAssetRepository;
import com.sugamflow.school.cms.persistence.repo.CmsPageRepository;
import com.sugamflow.school.cms.web.dto.MediaAssetResponse;
import com.sugamflow.school.cms.web.dto.MediaRegisterRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsMediaService {

  private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;
  private static final String LIMIT_STORAGE = "website_storage_gb";
  private static final String LIMIT_PAGES = "website_pages";

  private final CmsMediaAssetRepository mediaRepository;
  private final CmsPageRepository pageRepository;
  private final SubscriptionEntitlementsClient entitlementsClient;

  public CmsMediaService(
      CmsMediaAssetRepository mediaRepository,
      CmsPageRepository pageRepository,
      SubscriptionEntitlementsClient entitlementsClient) {
    this.mediaRepository = mediaRepository;
    this.pageRepository = pageRepository;
    this.entitlementsClient = entitlementsClient;
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
    return toResponse(mediaRepository.save(asset));
  }

  @Transactional
  public void delete(String organizationId, UUID id) {
    CmsMediaAsset asset =
        mediaRepository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found"));
    mediaRepository.delete(asset);
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
