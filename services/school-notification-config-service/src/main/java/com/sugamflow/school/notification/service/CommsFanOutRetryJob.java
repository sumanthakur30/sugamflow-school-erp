package com.sugamflow.school.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CommsFanOutRetryJob {

  private static final Logger log = LoggerFactory.getLogger(CommsFanOutRetryJob.class);

  private final CommsFanOutService fanOut;

  public CommsFanOutRetryJob(CommsFanOutService fanOut) {
    this.fanOut = fanOut;
  }

  @Scheduled(
      initialDelayString = "${school.notification.comms-retry-initial-ms:45000}",
      fixedDelayString = "${school.notification.comms-retry-ms:300000}")
  public void run() {
    try {
      fanOut.retryPending();
    } catch (Exception ex) {
      log.warn("Comms fan-out retry job failed: {}", ex.getMessage());
    }
  }
}
