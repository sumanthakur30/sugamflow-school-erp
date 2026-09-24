package com.sugamflow.school.compliance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;

/** Separate bean so {@code @Async} proxying works when enqueueing AI scans. */
@Service
public class AiWarnScanRunner {
  private static final Logger log = LoggerFactory.getLogger(AiWarnScanRunner.class);

  private final AiWarnScanService scanService;

  public AiWarnScanRunner(@Lazy AiWarnScanService scanService) {
    this.scanService = scanService;
  }

  @Async
  public void run(Long jobId, TenantScope scope) {
    try {
      TenantContext.set(scope);
      scanService.executeJob(jobId, scope);
    } catch (Exception ex) {
      log.warn("AI scan job {} failed: {}", jobId, ex.getMessage());
      scanService.markFailed(jobId, ex.getMessage());
    } finally {
      TenantContext.clear();
    }
  }
}
