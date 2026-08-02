package com.sugamflow.school.cms.web;

import com.sugamflow.school.cms.service.CmsContentService;
import com.sugamflow.school.cms.web.dto.EventResponse;
import com.sugamflow.school.cms.web.dto.GalleryItemResponse;
import com.sugamflow.school.cms.web.dto.NewsResponse;
import com.sugamflow.school.cms.web.dto.PageResponse;
import com.sugamflow.school.common.api.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cms/public")
public class CmsPublicController {

  private final CmsContentService contentService;

  public CmsPublicController(CmsContentService contentService) {
    this.contentService = contentService;
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
}
