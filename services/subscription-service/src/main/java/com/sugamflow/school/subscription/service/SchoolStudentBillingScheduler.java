package com.sugamflow.school.subscription.service;

import com.sugamflow.school.subscription.config.SchoolBillingProperties;
import java.time.YearMonth;
import java.time.ZoneId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Bills the previous calendar month. Off unless {@code subscription.school-billing.enabled=true}.
 */
@Component
@ConditionalOnProperty(prefix = "subscription.school-billing", name = "enabled", havingValue = "true")
public class SchoolStudentBillingScheduler {

  private final SchoolStudentBillingJob job;
  private final SchoolBillingProperties properties;

  public SchoolStudentBillingScheduler(SchoolStudentBillingJob job, SchoolBillingProperties properties) {
    this.job = job;
    this.properties = properties;
  }

  @Scheduled(
      cron = "${subscription.school-billing.cron:0 30 2 1 * *}",
      zone = "${subscription.school-billing.zone:Asia/Kolkata}")
  public void runPreviousMonth() {
    YearMonth period = YearMonth.now(ZoneId.of(properties.getZone())).minusMonths(1);
    job.runPeriod(period);
  }
}
