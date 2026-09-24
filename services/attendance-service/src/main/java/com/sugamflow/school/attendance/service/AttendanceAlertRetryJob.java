package com.sugamflow.school.attendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically re-dispatches PENDING/FAILED parent-alert outbox rows. */
@Component
public class AttendanceAlertRetryJob {

  private static final Logger log = LoggerFactory.getLogger(AttendanceAlertRetryJob.class);

  private final AttendanceAlertService alerts;

  public AttendanceAlertRetryJob(AttendanceAlertService alerts) {
    this.alerts = alerts;
  }

  @Scheduled(
      initialDelayString = "${school.attendance.alert-retry-initial-ms:60000}",
      fixedDelayString = "${school.attendance.alert-retry-ms:300000}")
  public void run() {
    try {
      alerts.retryPending();
    } catch (Exception ex) {
      log.warn("Attendance alert retry job failed: {}", ex.getMessage());
    }
  }
}
