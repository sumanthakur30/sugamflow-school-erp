package com.sugamflow.school.cms.web;

import com.sugamflow.school.cms.service.CmsContentService;
import com.sugamflow.school.cms.service.CmsMediaService;
import com.sugamflow.school.cms.web.dto.MediaAssetResponse;
import com.sugamflow.school.cms.web.dto.MediaRegisterRequest;
import com.sugamflow.school.cms.web.dto.PageResponse;
import com.sugamflow.school.cms.web.dto.PageUpsertRequest;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cms/admin")
public class CmsAdminController {

  private final CmsContentService contentService;
  private final CmsMediaService mediaService;

  public CmsAdminController(CmsContentService contentService, CmsMediaService mediaService) {
    this.contentService = contentService;
    this.mediaService = mediaService;
  }

  @GetMapping("/pages")
  public ApiResponse<List<PageResponse>> listPages() {
    return ApiResponse.ok(contentService.listAdminPages(orgId()));
  }

  @PostMapping("/pages")
  public ApiResponse<PageResponse> create(@Valid @RequestBody PageUpsertRequest request) {
    return ApiResponse.ok(contentService.createPage(orgId(), request));
  }

  @PutMapping("/pages/{id}")
  public ApiResponse<PageResponse> update(
      @PathVariable("id") UUID id, @Valid @RequestBody PageUpsertRequest request) {
    return ApiResponse.ok(contentService.updatePage(orgId(), id, request));
  }

  @PostMapping("/pages/{id}/publish")
  public ApiResponse<PageResponse> publish(@PathVariable("id") UUID id) {
    return ApiResponse.ok(contentService.publishPage(orgId(), id));
  }

  @PostMapping("/pages/{id}/unpublish")
  public ApiResponse<PageResponse> unpublish(@PathVariable("id") UUID id) {
    return ApiResponse.ok(contentService.unpublishPage(orgId(), id));
  }

  @GetMapping("/media")
  public ApiResponse<List<MediaAssetResponse>> listMedia() {
    return ApiResponse.ok(mediaService.list(orgId()));
  }

  @GetMapping("/media/usage")
  public ApiResponse<Map<String, Object>> mediaUsage() {
    return ApiResponse.ok(mediaService.usage(orgId()));
  }

  @PostMapping("/media")
  public ApiResponse<MediaAssetResponse> registerMedia(
      @Valid @RequestBody MediaRegisterRequest request) {
    return ApiResponse.ok(mediaService.register(orgId(), request));
  }

  @DeleteMapping("/media/{id}")
  public ApiResponse<Map<String, Object>> deleteMedia(@PathVariable("id") UUID id) {
    mediaService.delete(orgId(), id);
    return ApiResponse.ok(Map.of("deleted", true, "id", id.toString()));
  }

  private static String orgId() {
    return TenantContext.require().organizationId();
  }
}
