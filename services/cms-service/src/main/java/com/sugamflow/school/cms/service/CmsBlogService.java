package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.integration.SubscriptionEntitlementsClient;
import com.sugamflow.school.cms.persistence.entity.CmsBlogPost;
import com.sugamflow.school.cms.persistence.repo.CmsBlogPostRepository;
import com.sugamflow.school.cms.web.dto.BlogPostResponse;
import com.sugamflow.school.cms.web.dto.BlogUpsertRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CmsBlogService {

  private static final String PUBLISHED = "PUBLISHED";
  private static final String DRAFT = "DRAFT";
  private static final String FEATURE_BLOG = "FEATURE_WEBSITE_BLOG";

  private final CmsBlogPostRepository repository;
  private final SubscriptionEntitlementsClient entitlementsClient;

  public CmsBlogService(
      CmsBlogPostRepository repository, SubscriptionEntitlementsClient entitlementsClient) {
    this.repository = repository;
    this.entitlementsClient = entitlementsClient;
  }

  public List<BlogPostResponse> listPublished(String organizationId) {
    return repository
        .findByOrganizationIdAndStatusOrderByPublishedAtDesc(requireOrg(organizationId), PUBLISHED)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  public BlogPostResponse getPublished(String organizationId, String slug) {
    return repository
        .findByOrganizationIdAndSlugAndStatus(requireOrg(organizationId), normalizeSlug(slug), PUBLISHED)
        .map(this::toResponse)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
  }

  public List<BlogPostResponse> listAdmin(String organizationId) {
    requireBlogFeature(organizationId);
    return repository.findByOrganizationIdOrderByUpdatedAtDesc(organizationId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public BlogPostResponse create(String organizationId, BlogUpsertRequest request) {
    requireBlogFeature(organizationId);
    String slug = normalizeSlug(request.slug());
    repository
        .findByOrganizationIdAndSlug(organizationId, slug)
        .ifPresent(
            p -> {
              throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already used");
            });
    Instant now = Instant.now();
    CmsBlogPost post = new CmsBlogPost();
    post.setId(UUID.randomUUID());
    post.setOrganizationId(organizationId);
    post.setSlug(slug);
    apply(post, request);
    post.setStatus(DRAFT);
    post.setCreatedAt(now);
    post.setUpdatedAt(now);
    return toResponse(repository.save(post));
  }

  @Transactional
  public BlogPostResponse update(String organizationId, UUID id, BlogUpsertRequest request) {
    requireBlogFeature(organizationId);
    CmsBlogPost post =
        repository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
    post.setSlug(normalizeSlug(request.slug()));
    apply(post, request);
    post.setUpdatedAt(Instant.now());
    return toResponse(repository.save(post));
  }

  @Transactional
  public BlogPostResponse publish(String organizationId, UUID id) {
    requireBlogFeature(organizationId);
    CmsBlogPost post =
        repository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
    post.setStatus(PUBLISHED);
    post.setPublishedAt(Instant.now());
    post.setUpdatedAt(Instant.now());
    return toResponse(repository.save(post));
  }

  @Transactional
  public BlogPostResponse unpublish(String organizationId, UUID id) {
    requireBlogFeature(organizationId);
    CmsBlogPost post =
        repository
            .findByIdAndOrganizationId(id, organizationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blog post not found"));
    post.setStatus(DRAFT);
    post.setUpdatedAt(Instant.now());
    return toResponse(repository.save(post));
  }

  private void requireBlogFeature(String organizationId) {
    if (!entitlementsClient.isFeatureEnabled(organizationId, FEATURE_BLOG)) {
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED, "FEATURE_WEBSITE_BLOG is not enabled for this school");
    }
  }

  private void apply(CmsBlogPost post, BlogUpsertRequest request) {
    post.setTitle(request.title().trim());
    post.setSummary(blankToNull(request.summary()));
    post.setBodyHtml(request.bodyHtml() == null ? "" : request.bodyHtml());
    post.setCoverImageUrl(blankToNull(request.coverImageUrl()));
  }

  private BlogPostResponse toResponse(CmsBlogPost p) {
    return new BlogPostResponse(
        p.getId(),
        p.getSlug(),
        p.getTitle(),
        p.getSummary(),
        p.getBodyHtml(),
        p.getCoverImageUrl(),
        p.getStatus(),
        p.getPublishedAt(),
        p.getUpdatedAt());
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
