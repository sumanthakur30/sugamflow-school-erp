package com.sugamflow.school.common.ops;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Applies connect/read timeouts to every auto-configured {@code RestClient.Builder}. All school
 * integration clients inject that builder, so this single customizer bounds every inter-service
 * call without touching each client.
 */
@Configuration
@EnableConfigurationProperties(HttpClientProperties.class)
public class HttpClientConfig {

  @Bean
  public RestClientCustomizer schoolRestClientTimeoutCustomizer(HttpClientProperties props) {
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()))
            .withReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
    return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
  }
}
