package com.sugamflow.school.common.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Timeouts applied to every {@code RestClient.Builder} in a school service. Without these, an
 * unresponsive downstream dependency can hold a request thread indefinitely and exhaust the pool.
 */
@ConfigurationProperties(prefix = "school.http")
public class HttpClientProperties {

  /** TCP connect timeout for inter-service calls (ms). */
  private long connectTimeoutMs = 3000;

  /** Read/response timeout for inter-service calls (ms). */
  private long readTimeoutMs = 10000;

  public long getConnectTimeoutMs() {
    return connectTimeoutMs;
  }

  public void setConnectTimeoutMs(long connectTimeoutMs) {
    this.connectTimeoutMs = connectTimeoutMs;
  }

  public long getReadTimeoutMs() {
    return readTimeoutMs;
  }

  public void setReadTimeoutMs(long readTimeoutMs) {
    this.readTimeoutMs = readTimeoutMs;
  }
}
