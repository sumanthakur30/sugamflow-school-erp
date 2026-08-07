package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.persistence.entity.CmsEvent;
import com.sugamflow.school.cms.persistence.entity.CmsGalleryItem;
import com.sugamflow.school.cms.persistence.entity.CmsNews;
import com.sugamflow.school.cms.persistence.entity.CmsPage;
import com.sugamflow.school.cms.persistence.repo.CmsEventRepository;
import com.sugamflow.school.cms.persistence.repo.CmsGalleryItemRepository;
import com.sugamflow.school.cms.persistence.repo.CmsNewsRepository;
import com.sugamflow.school.cms.persistence.repo.CmsPageRepository;
import com.sugamflow.school.cms.web.dto.EventResponse;
import com.sugamflow.school.cms.web.dto.GalleryItemResponse;
import com.sugamflow.school.cms.web.dto.GalleryUpsertRequest;
import com.sugamflow.school.cms.web.dto.NewsResponse;
import com.sugamflow.school.cms.web.dto.NewsUpsertRequest;
import com.sugamflow.school.cms.web.dto.PageResponse;
import com.sugamflow.school.cms.web.dto.PageUpsertRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsContentService {

  private static final String PUBLISHED = "PUBLISHED";
  private static final String DRAFT = "DRAFT";

  private final CmsPageRepository pageRepository;
  private final CmsNewsRepository newsRepository;
  private final CmsEventRepository eventRepository;
  private final CmsGalleryItemRepository galleryRepository;
  private final CmsMediaService mediaService;

  public CmsContentService(
      CmsPageRepository pageRepository,
      CmsNewsRepository newsRepository,
      CmsEventRepository eventRepository,
      CmsGalleryItemRepository galleryRepository,
      CmsMediaService mediaService) {
    this.pageRepository = pageRepository;
    this.newsRepository = newsRepository;
    this.eventRepository = eventRepository;
    this.galleryRepository = galleryRepository;
    this.mediaService = mediaService;
  }

  public List<PageResponse> listPublishedPages(String organizationId) {
    return pageRepository
        .findByOrganizationIdAndStatusOrderBySlugAsc(requireOrg(organizationId), PUBLISHED)
        .stream()
        .map(this::toPage)
        .toList();
  }

  public PageResponse getPublishedPage(String organizationId, String slug) {
    return pageRepository
        .findByOrganizationIdAndSlugAndStatus(requireOrg(organizationId), normalizeSlug(slug), PUBLISHED)
        .map(this::toPage)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
  }

  public List<NewsResponse> listPublishedNews(String organizationId) {
    return newsRepository
        .findByOrganizationIdAndStatusOrderByPublishedAtDesc(requireOrg(organizationId), PUBLISHED)
        .stream()
        .map(this::toNews)
        .toList();
  }

  public NewsResponse getPublishedNews(String organizationId, String slug) {
    return newsRepository
        .findByOrganizationIdAndSlugAndStatus(requireOrg(organizationId), normalizeSlug(slug), PUBLISHED)
        .map(this::toNews)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found"));
  }

  public List<EventResponse> listPublishedEvents(String organizationId) {
    return eventRepository
        .findByOrganizationIdAndStatusOrderByStartsAtAsc(requireOrg(organizationId), PUBLISHED)
        .stream()
        .map(this::toEvent)
        .toList();
  }

  public List<GalleryItemResponse> listPublishedGallery(String organizationId) {
    return galleryRepository
        .findByOrganizationIdAndStatusOrderByAlbumAscSortOrderAsc(requireOrg(organizationId), PUBLISHED)
        .stream()
        .map(this::toGallery)
        .toList();
  }

  public List<PageResponse> listAdminPages(String organizationId) {
    return pageRepository.findByOrganizationIdOrderByUpdatedAtDesc(requireOrg(organizationId)).stream()
        .map(this::toPage)
        .toList();
  }

  @Transactional
  public PageResponse createPage(String organizationId, PageUpsertRequest request) {
    String org = requireOrg(organizationId);
    mediaService.assertPageCreateAllowed(org);
    String slug = normalizeSlug(request.slug());
    pageRepository
        .findByOrganizationIdAndSlug(org, slug)
        .ifPresent(
            p -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already used");
            });
    Instant now = Instant.now();
    CmsPage page = new CmsPage();
    page.setId(UUID.randomUUID());
    page.setOrganizationId(org);
    page.setSlug(slug);
    page.setTitle(request.title().trim());
    page.setSummary(blankToNull(request.summary()));
    page.setBodyHtml(request.bodyHtml() == null ? "" : request.bodyHtml());
    page.setStatus(DRAFT);
    page.setSeoTitle(blankToNull(request.seoTitle()));
    page.setSeoDescription(blankToNull(request.seoDescription()));
    page.setCreatedAt(now);
    page.setUpdatedAt(now);
    PageResponse saved = toPage(pageRepository.save(page));
    mediaService.recordPageCreated(org);
    return saved;
  }

  @Transactional
  public PageResponse updatePage(String organizationId, UUID id, PageUpsertRequest request) {
    String org = requireOrg(organizationId);
    CmsPage page =
        pageRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
    page.setSlug(normalizeSlug(request.slug()));
    page.setTitle(request.title().trim());
    page.setSummary(blankToNull(request.summary()));
    page.setBodyHtml(request.bodyHtml() == null ? "" : request.bodyHtml());
    page.setSeoTitle(blankToNull(request.seoTitle()));
    page.setSeoDescription(blankToNull(request.seoDescription()));
    page.setUpdatedAt(Instant.now());
    return toPage(pageRepository.save(page));
  }

  @Transactional
  public PageResponse publishPage(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsPage page =
        pageRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
    page.setStatus(PUBLISHED);
    page.setPublishedAt(Instant.now());
    page.setUpdatedAt(Instant.now());
    return toPage(pageRepository.save(page));
  }

  @Transactional
  public PageResponse unpublishPage(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsPage page =
        pageRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
    page.setStatus(DRAFT);
    page.setUpdatedAt(Instant.now());
    return toPage(pageRepository.save(page));
  }

  public List<NewsResponse> listAdminNews(String organizationId) {
    return newsRepository
        .findByOrganizationIdOrderByUpdatedAtDesc(requireOrg(organizationId))
        .stream()
        .map(this::toNews)
        .toList();
  }

  @Transactional
  public NewsResponse createNews(String organizationId, NewsUpsertRequest request) {
    String org = requireOrg(organizationId);
    String slug = normalizeSlug(request.slug());
    newsRepository
        .findByOrganizationIdAndSlug(org, slug)
        .ifPresent(
            n -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already used");
            });
    Instant now = Instant.now();
    CmsNews news = new CmsNews();
    news.setId(UUID.randomUUID());
    news.setOrganizationId(org);
    news.setSlug(slug);
    applyNews(news, request);
    news.setStatus(DRAFT);
    news.setCreatedAt(now);
    news.setUpdatedAt(now);
    return toNews(newsRepository.save(news));
  }

  @Transactional
  public NewsResponse updateNews(String organizationId, UUID id, NewsUpsertRequest request) {
    String org = requireOrg(organizationId);
    CmsNews news =
        newsRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found"));
    String slug = normalizeSlug(request.slug());
    newsRepository
        .findByOrganizationIdAndSlug(org, slug)
        .filter(other -> !other.getId().equals(id))
        .ifPresent(
            n -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already used");
            });
    news.setSlug(slug);
    applyNews(news, request);
    news.setUpdatedAt(Instant.now());
    return toNews(newsRepository.save(news));
  }

  @Transactional
  public NewsResponse publishNews(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsNews news =
        newsRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found"));
    news.setStatus(PUBLISHED);
    news.setPublishedAt(Instant.now());
    news.setUpdatedAt(Instant.now());
    return toNews(newsRepository.save(news));
  }

  @Transactional
  public NewsResponse unpublishNews(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsNews news =
        newsRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found"));
    news.setStatus(DRAFT);
    news.setUpdatedAt(Instant.now());
    return toNews(newsRepository.save(news));
  }

  public List<GalleryItemResponse> listAdminGallery(String organizationId) {
    return galleryRepository
        .findByOrganizationIdOrderByAlbumAscSortOrderAsc(requireOrg(organizationId))
        .stream()
        .map(this::toGallery)
        .toList();
  }

  @Transactional
  public GalleryItemResponse createGalleryItem(String organizationId, GalleryUpsertRequest request) {
    String org = requireOrg(organizationId);
    Instant now = Instant.now();
    CmsGalleryItem item = new CmsGalleryItem();
    item.setId(UUID.randomUUID());
    item.setOrganizationId(org);
    applyGallery(item, request);
    item.setStatus(DRAFT);
    item.setCreatedAt(now);
    item.setUpdatedAt(now);
    return toGallery(galleryRepository.save(item));
  }

  @Transactional
  public GalleryItemResponse updateGalleryItem(
      String organizationId, UUID id, GalleryUpsertRequest request) {
    String org = requireOrg(organizationId);
    CmsGalleryItem item =
        galleryRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gallery item not found"));
    applyGallery(item, request);
    item.setUpdatedAt(Instant.now());
    return toGallery(galleryRepository.save(item));
  }

  @Transactional
  public GalleryItemResponse publishGalleryItem(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsGalleryItem item =
        galleryRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gallery item not found"));
    item.setStatus(PUBLISHED);
    item.setPublishedAt(Instant.now());
    item.setUpdatedAt(Instant.now());
    return toGallery(galleryRepository.save(item));
  }

  @Transactional
  public GalleryItemResponse unpublishGalleryItem(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsGalleryItem item =
        galleryRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gallery item not found"));
    item.setStatus(DRAFT);
    item.setUpdatedAt(Instant.now());
    return toGallery(galleryRepository.save(item));
  }

  @Transactional
  public void deleteGalleryItem(String organizationId, UUID id) {
    String org = requireOrg(organizationId);
    CmsGalleryItem item =
        galleryRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Gallery item not found"));
    galleryRepository.delete(item);
  }

  private void applyNews(CmsNews news, NewsUpsertRequest request) {
    news.setTitle(request.title().trim());
    news.setSummary(blankToNull(request.summary()));
    news.setBodyHtml(request.bodyHtml() == null ? "" : request.bodyHtml());
    news.setCoverImageUrl(blankToNull(request.coverImageUrl()));
  }

  private void applyGallery(CmsGalleryItem item, GalleryUpsertRequest request) {
    item.setTitle(request.title().trim());
    item.setCaption(blankToNull(request.caption()));
    item.setImageUrl(request.imageUrl().trim());
    item.setAlbum(
        request.album() == null || request.album().isBlank() ? "general" : request.album().trim());
    item.setSortOrder(request.sortOrder() == null ? 0 : Math.max(0, request.sortOrder()));
  }

  private PageResponse toPage(CmsPage p) {
    return new PageResponse(
        p.getId(),
        p.getSlug(),
        p.getTitle(),
        p.getSummary(),
        p.getBodyHtml(),
        p.getStatus(),
        p.getSeoTitle(),
        p.getSeoDescription(),
        p.getPublishedAt(),
        p.getUpdatedAt());
  }

  private NewsResponse toNews(CmsNews n) {
    return new NewsResponse(
        n.getId(),
        n.getSlug(),
        n.getTitle(),
        n.getSummary(),
        n.getBodyHtml(),
        n.getCoverImageUrl(),
        n.getPublishedAt(),
        n.getStatus());
  }

  private EventResponse toEvent(CmsEvent e) {
    return new EventResponse(
        e.getId(),
        e.getSlug(),
        e.getTitle(),
        e.getSummary(),
        e.getBodyHtml(),
        e.getLocationText(),
        e.getStartsAt(),
        e.getEndsAt());
  }

  private GalleryItemResponse toGallery(CmsGalleryItem g) {
    return new GalleryItemResponse(
        g.getId(),
        g.getTitle(),
        g.getCaption(),
        g.getImageUrl(),
        g.getAlbum(),
        g.getSortOrder(),
        g.getStatus());
  }

  private static String requireOrg(String organizationId) {
    if (organizationId == null || organizationId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "organizationId is required");
    }
    return organizationId.trim();
  }

  private static String normalizeSlug(String slug) {
    if (slug == null || slug.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slug is required");
    }
    return slug.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\-]+", "-");
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
