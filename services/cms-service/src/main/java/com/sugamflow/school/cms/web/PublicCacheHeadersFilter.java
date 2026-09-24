package com.sugamflow.school.cms.web;

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

/** CDN-friendly cache headers for public CMS GET APIs. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class PublicCacheHeadersFilter extends OncePerRequestFilter {

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    return path == null
        || !path.startsWith("/api/cms/public/")
        || !HttpMethod.GET.matches(request.getMethod());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    filterChain.doFilter(request, response);
    response.setHeader("Cache-Control", "public, max-age=60, stale-while-revalidate=300");
    response.setHeader("Vary", "Accept-Encoding");
  }
}
