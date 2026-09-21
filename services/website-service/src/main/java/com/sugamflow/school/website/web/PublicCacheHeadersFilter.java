package com.sugamflow.school.website.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** CDN-friendly cache headers for public website GET APIs. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class PublicCacheHeadersFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path == null
        || !path.startsWith("/api/website/public/")
        || !HttpMethod.GET.matches(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    filterChain.doFilter(request, response);
    String path = request.getRequestURI();
    if (path != null && path.contains("/events")) {
      return;
    }
    if (path != null && path.contains("/robots.txt")) {
      response.setHeader("Cache-Control", "public, max-age=300");
      return;
    }
    // resolve / sitemap — short CDN TTL with SWR
    response.setHeader("Cache-Control", "public, max-age=60, stale-while-revalidate=300");
    response.setHeader("Vary", "Accept-Encoding");
  }
}
