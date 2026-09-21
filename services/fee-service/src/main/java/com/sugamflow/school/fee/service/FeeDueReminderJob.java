package com.sugamflow.school.fee.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FeeDueReminderJob {

  private static final Logger log = LoggerFactory.getLogger(FeeDueReminderJob.class);

  private final FeeDueReminderService reminders;

  public FeeDueReminderJob(FeeDueReminderService reminders) {
    this.reminders = reminders;
  }

  @Scheduled(
      initialDelayString = "${fee.due-reminder-initial-ms:90000}",
      fixedDelayString = "${fee.due-reminder-ms:3600000}")
  public void run() {
    try {
      reminders.retryPending();
    } catch (Exception ex) {
      log.warn("Fee due reminder retry job failed: {}", ex.getMessage());
    }
  }
}
