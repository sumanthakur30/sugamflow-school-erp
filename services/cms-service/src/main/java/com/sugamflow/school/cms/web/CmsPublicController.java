package com.sugamflow.school.cms.web;

import com.sugamflow.school.cms.service.CmsBlogService;
import com.sugamflow.school.cms.service.CmsContentService;
import com.sugamflow.school.cms.service.CmsMediaService;
import com.sugamflow.school.cms.web.dto.BlogPostResponse;
import com.sugamflow.school.cms.web.dto.EventResponse;
import com.sugamflow.school.cms.web.dto.GalleryItemResponse;
import com.sugamflow.school.cms.web.dto.NewsResponse;
import com.sugamflow.school.cms.web.dto.PageResponse;
import com.sugamflow.school.common.api.ApiResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cms/public")
public class CmsPublicController {

  private final CmsContentService contentService;
  private final CmsBlogService blogService;
  private final CmsMediaService mediaService;

  public CmsPublicController(
      CmsContentService contentService, CmsBlogService blogService, CmsMediaService mediaService) {
    this.contentService = contentService;
    this.blogService = blogService;
    this.mediaService = mediaService;
  }

  @GetMapping("/pages")
  public ApiResponse<List<PageResponse>> pages(
      @RequestParam("organizationId") String organizationId) {
    return ApiResponse.ok(contentService.listPublishedPages(organizationId));
  }

  @GetMapping("/pages/{slug}")
  public ApiResponse<PageResponse> page(
      @RequestParam("organizationId") String organizationId, @PathVariable("slug") String slug) {
    return ApiResponse.ok(contentService.getPublishedPage(organizationId, slug));
  }

  @GetMapping("/news")
  public ApiResponse<List<NewsResponse>> news(
      @RequestParam("organizationId") String organizationId) {
    return ApiResponse.ok(contentService.listPublishedNews(organizationId));
  }

  @GetMapping("/news/{slug}")
  public ApiResponse<NewsResponse> newsItem(
      @RequestParam("organizationId") String organizationId, @PathVariable("slug") String slug) {
    return ApiResponse.ok(contentService.getPublishedNews(organizationId, slug));
  }

  @GetMapping("/events")
  public ApiResponse<List<EventResponse>> events(
      @RequestParam("organizationId") String organizationId) {
    return ApiResponse.ok(contentService.listPublishedEvents(organizationId));
  }

  @GetMapping("/gallery")
  public ApiResponse<List<GalleryItemResponse>> gallery(
      @RequestParam("organizationId") String organizationId) {
    return ApiResponse.ok(contentService.listPublishedGallery(organizationId));
  }

  @GetMapping("/blog")
  public ApiResponse<List<BlogPostResponse>> blog(
      @RequestParam("organizationId") String organizationId) {
    return ApiResponse.ok(blogService.listPublished(organizationId));
  }

  @GetMapping("/blog/{slug}")
  public ApiResponse<BlogPostResponse> blogPost(
      @RequestParam("organizationId") String organizationId, @PathVariable("slug") String slug) {
    return ApiResponse.ok(blogService.getPublished(organizationId, slug));
  }

  @GetMapping("/media/{id}")
  public ResponseEntity<Resource> mediaFile(
      @RequestParam("organizationId") String organizationId, @PathVariable("id") UUID id) {
    Resource resource = mediaService.loadFile(organizationId, id);
    MediaType type = mediaService.mediaTypeFor(organizationId, id);
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .contentType(type)
        .body(resource);
  }
}
