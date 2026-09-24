package com.sugamflow.school.subscription.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Backfill ACTIVE-forever lifecycle rows for tenants created before V5. */
@Component
@Order(200)
public class SubscriptionLifecycleBackfill implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleBackfill.class);

  private final SubscriptionLifecycleService lifecycleService;

  public SubscriptionLifecycleBackfill(SubscriptionLifecycleService lifecycleService) {
    this.lifecycleService = lifecycleService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    int created = lifecycleService.backfillMissing();
    if (created > 0) {
      log.info("Backfilled ACTIVE-forever lifecycle for {} tenant(s)", created);
    }
  }
}
