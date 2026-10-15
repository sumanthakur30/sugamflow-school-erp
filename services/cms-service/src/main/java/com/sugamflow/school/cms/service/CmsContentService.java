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
import com.sugamflow.school.cms.web.dto.EventUpsertRequest;
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
    return listPublishedPages(organizationId, CmsSiteScope.legacy(), 200, 0);
  }

  public List<PageResponse> listPublishedPages(
      String organizationId, CmsSiteScope scope, int limit, int offset) {
    return slice(
            pageRepository
                .findByOrganizationIdAndStatusOrderBySlugAsc(requireOrg(organizationId), PUBLISHED)
                .stream()
                .filter(p -> scope.matches(p.getSiteId()))
                .map(this::toPage)
                .toList(),
            limit,
            offset);
  }

  public PageResponse getPublishedPage(String organizationId, String slug) {
    return getPublishedPage(organizationId, slug, CmsSiteScope.legacy());
  }

  public PageResponse getPublishedPage(String organizationId, String slug, CmsSiteScope scope) {
    return pageRepository
        .findAllByOrganizationIdAndSlugAndStatus(requireOrg(organizationId), normalizeSlug(slug), PUBLISHED)
        .stream()
        .filter(p -> scope.matches(p.getSiteId()))
        .findFirst()
        .map(this::toPage)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Page not found"));
  }

  public List<NewsResponse> listPublishedNews(String organizationId) {
    return listPublishedNews(organizationId, CmsSiteScope.legacy(), 50, 0);
  }

  public List<NewsResponse> listPublishedNews(
      String organizationId, CmsSiteScope scope, int limit, int offset) {
    Instant now = Instant.now();
    return slice(
        newsRepository
            .findByOrganizationIdAndStatusOrderByPublishedAtDesc(requireOrg(organizationId), PUBLISHED)
            .stream()
            .filter(n -> scope.matches(n.getSiteId()))
            .filter(n -> n.getExpiresAt() == null || !n.getExpiresAt().isBefore(now))
            .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
            .map(this::toNews)
            .toList(),
        limit,
        offset);
  }

  public NewsResponse getPublishedNews(String organizationId, String slug) {
    return getPublishedNews(organizationId, slug, CmsSiteScope.legacy());
  }

  public NewsResponse getPublishedNews(String organizationId, String slug, CmsSiteScope scope) {
    Instant now = Instant.now();
    return newsRepository
        .findAllByOrganizationIdAndSlugAndStatus(requireOrg(organizationId), normalizeSlug(slug), PUBLISHED)
        .stream()
        .filter(n -> scope.matches(n.getSiteId()))
        .filter(n -> n.getExpiresAt() == null || !n.getExpiresAt().isBefore(now))
        .findFirst()
        .map(this::toNews)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "News not found"));
  }

  public List<EventResponse> listPublishedEvents(String organizationId) {
    return listPublishedEvents(organizationId, CmsSiteScope.legacy(), 50, 0);
  }

  public List<EventResponse> listPublishedEvents(
      String organizationId, CmsSiteScope scope, int limit, int offset) {
    Instant now = Instant.now();
    return slice(
        eventRepository
            .findByOrganizationIdAndStatusOrderByStartsAtAsc(requireOrg(organizationId), PUBLISHED)
            .stream()
            .filter(e -> scope.matches(e.getSiteId()))
            .filter(e -> e.getEndsAt() == null || !e.getEndsAt().isBefore(now))
            .map(this::toEvent)
            .toList(),
        limit,
        offset);
  }

  public List<GalleryItemResponse> listPublishedGallery(String organizationId) {
    return listPublishedGallery(organizationId, CmsSiteScope.legacy(), 24, 0);
  }

  public List<GalleryItemResponse> listPublishedGallery(
      String organizationId, CmsSiteScope scope, int limit, int offset) {
    return slice(
        galleryRepository
            .findByOrganizationIdAndStatusOrderByAlbumAscSortOrderAsc(
                requireOrg(organizationId), PUBLISHED)
            .stream()
            .filter(g -> scope.matches(g.getSiteId()))
            .map(this::toGallery)
            .toList(),
        limit,
        offset);
  }

  public List<PageResponse> listAdminPages(String organizationId) {
    return listAdminPages(organizationId, CmsSiteScope.legacy());
  }

  public List<PageResponse> listAdminPages(String organizationId, CmsSiteScope scope) {
    return pageRepository.findByOrganizationIdOrderByUpdatedAtDesc(requireOrg(organizationId)).stream()
        .filter(p -> scope.matches(p.getSiteId()))
        .map(this::toPage)
        .toList();
  }

  @Transactional
  public PageResponse createPage(String organizationId, PageUpsertRequest request) {
    return createPage(organizationId, request, CmsSiteScope.legacy());
  }

  @Transactional
  public PageResponse createPage(
      String organizationId, PageUpsertRequest request, CmsSiteScope scope) {
    String org = requireOrg(organizationId);
    mediaService.assertPageCreateAllowed(org);
    String slug = normalizeSlug(request.slug());
    assertSlugFree(
        pageRepository.findAllByOrganizationIdAndSlug(org, slug).stream()
            .filter(p -> scope.sameBucket(p.getSiteId()))
            .findAny()
            .isPresent());
    Instant now = Instant.now();
    CmsPage page = new CmsPage();
    page.setId(UUID.randomUUID());
    page.setOrganizationId(org);
    page.setSiteId(scope.siteId());
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
    return listAdminNews(organizationId, CmsSiteScope.legacy());
  }

  public List<NewsResponse> listAdminNews(String organizationId, CmsSiteScope scope) {
    return newsRepository
        .findByOrganizationIdOrderByUpdatedAtDesc(requireOrg(organizationId))
        .stream()
        .filter(n -> scope.matches(n.getSiteId()))
        .map(this::toNews)
        .toList();
  }

  public NewsResponse createNews(String organizationId, NewsUpsertRequest request) {
    return createNews(organizationId, request, CmsSiteScope.legacy());
  }

  @Transactional
  public NewsResponse createNews(
      String organizationId, NewsUpsertRequest request, CmsSiteScope scope) {
    String org = requireOrg(organizationId);
    String slug = normalizeSlug(request.slug());
    assertSlugFree(
        newsRepository.findAllByOrganizationIdAndSlug(org, slug).stream()
            .anyMatch(n -> scope.sameBucket(n.getSiteId())));
    Instant now = Instant.now();
    CmsNews news = new CmsNews();
    news.setId(UUID.randomUUID());
    news.setOrganizationId(org);
    news.setSiteId(scope.siteId());
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
    assertSlugFree(
        newsRepository.findAllByOrganizationIdAndSlug(org, slug).stream()
            .anyMatch(other -> !other.getId().equals(id) && sameSite(other.getSiteId(), news.getSiteId())));
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
    return listAdminGallery(organizationId, CmsSiteScope.legacy());
  }

  public List<GalleryItemResponse> listAdminGallery(String organizationId, CmsSiteScope scope) {
    return galleryRepository
        .findByOrganizationIdOrderByAlbumAscSortOrderAsc(requireOrg(organizationId))
        .stream()
        .filter(g -> scope.matches(g.getSiteId()))
        .map(this::toGallery)
        .toList();
  }

  public GalleryItemResponse createGalleryItem(String organizationId, GalleryUpsertRequest request) {
    return createGalleryItem(organizationId, request, CmsSiteScope.legacy());
  }

  @Transactional
  public GalleryItemResponse createGalleryItem(
      String organizationId, GalleryUpsertRequest request, CmsSiteScope scope) {
    String org = requireOrg(organizationId);
    Instant now = Instant.now();
    CmsGalleryItem item = new CmsGalleryItem();
    item.setId(UUID.randomUUID());
    item.setOrganizationId(org);
    item.setSiteId(scope.siteId());
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

  public List<EventResponse> listAdminEvents(String organizationId, CmsSiteScope scope) {
    return eventRepository
        .findByOrganizationIdOrderByStartsAtDesc(requireOrg(organizationId))
        .stream()
        .filter(e -> scope.matches(e.getSiteId()))
        .map(this::toEvent)
        .toList();
  }

  @Transactional
  public EventResponse createEvent(
      String organizationId, EventUpsertRequest request, CmsSiteScope scope) {
    String org = requireOrg(organizationId);
    String slug = normalizeSlug(request.slug());
    assertSlugFree(
        eventRepository.findAllByOrganizationIdAndSlug(org, slug).stream()
            .anyMatch(e -> scope.sameBucket(e.getSiteId())));
    Instant now = Instant.now();
    CmsEvent event = new CmsEvent();
    event.setId(UUID.randomUUID());
    event.setOrganizationId(org);
    event.setSiteId(scope.siteId());
    event.setSlug(slug);
    applyEvent(event, request);
    event.setStatus(DRAFT);
    event.setCreatedAt(now);
    event.setUpdatedAt(now);
    return toEvent(eventRepository.save(event));
  }

  @Transactional
  public EventResponse updateEvent(String organizationId, UUID id, EventUpsertRequest request) {
    String org = requireOrg(organizationId);
    CmsEvent event =
        eventRepository
            .findByIdAndOrganizationId(id, org)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    String slug = normalizeSlug(request.slug());
    assertSlugFree(
        eventRepository.findAllByOrganizationIdAndSlug(org, slug).stream()
            .anyMatch(
                other -> !other.getId().equals(id) && sameSite(other.getSiteId(), event.getSiteId())));
    event.setSlug(slug);
    applyEvent(event, request);
    event.setUpdatedAt(Instant.now());
    return toEvent(eventRepository.save(event));
  }

  @Transactional
  public EventResponse publishEvent(String organizationId, UUID id) {
    CmsEvent event = requireEvent(organizationId, id);
    event.setStatus(PUBLISHED);
    event.setPublishedAt(Instant.now());
    event.setUpdatedAt(Instant.now());
    return toEvent(eventRepository.save(event));
  }

  @Transactional
  public EventResponse unpublishEvent(String organizationId, UUID id) {
    CmsEvent event = requireEvent(organizationId, id);
    event.setStatus(DRAFT);
    event.setUpdatedAt(Instant.now());
    return toEvent(eventRepository.save(event));
  }

  private CmsEvent requireEvent(String organizationId, UUID id) {
    return eventRepository
        .findByIdAndOrganizationId(id, requireOrg(organizationId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
  }

  private void applyNews(CmsNews news, NewsUpsertRequest request) {
    news.setTitle(request.title().trim());
    news.setSummary(blankToNull(request.summary()));
    news.setBodyHtml(request.bodyHtml() == null ? "" : request.bodyHtml());
    news.setCoverImageUrl(blankToNull(request.coverImageUrl()));
    news.setCategory(blankToDefault(request.category(), "NOTICE"));
    news.setPriority(request.priority() == null ? 0 : request.priority());
    news.setAudience(blankToDefault(request.audience(), "PUBLIC"));
    news.setExpiresAt(request.expiresAt());
  }

  private void applyEvent(CmsEvent event, EventUpsertRequest request) {
    event.setTitle(request.title().trim());
    event.setSummary(blankToNull(request.summary()));
    event.setBodyHtml(request.bodyHtml() == null ? "" : request.bodyHtml());
    event.setLocationText(blankToNull(request.locationText()));
    event.setStartsAt(request.startsAt());
    event.setEndsAt(request.endsAt());
    event.setCategory(blankToDefault(request.category(), "EVENT"));
    event.setPriority(request.priority() == null ? 0 : request.priority());
    event.setAudience(blankToDefault(request.audience(), "PUBLIC"));
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
        n.getStatus(),
        n.getCategory(),
        n.getPriority(),
        n.getAudience(),
        n.getExpiresAt());
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
        e.getEndsAt(),
        e.getStatus(),
        e.getCategory(),
        e.getPriority(),
        e.getAudience());
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

  private static void assertSlugFree(boolean taken) {
    if (taken) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already used");
    }
  }

  private static boolean sameSite(UUID left, UUID right) {
    if (left == null) {
      return right == null;
    }
    return left.equals(right);
  }

  private static <T> List<T> slice(List<T> rows, int limit, int offset) {
    int safeOffset = Math.max(0, offset);
    int safeLimit = limit <= 0 ? 50 : Math.min(limit, 500);
    if (safeOffset >= rows.size()) {
      return List.of();
    }
    return rows.subList(safeOffset, Math.min(rows.size(), safeOffset + safeLimit));
  }

  private static String blankToDefault(String value, String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim().toUpperCase(Locale.ROOT);
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
