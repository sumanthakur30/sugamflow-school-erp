package com.sugamflow.school.common.ops;

import java.io.IOException;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;

/**
 * Loads {@code school-ops.defaults.properties} from school-common into every school service
 * environment. Fat jars do not auto-merge dependency {@code application.properties}, so this EPP
 * is the reliable way to ship shared ops defaults (actuator probes, timeouts, logging pattern).
 *
 * <p>Registered at lowest precedence so application / profile / env overrides always win.
 */
public class SchoolOpsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  public static final String SOURCE_NAME = "schoolOpsDefaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    MutablePropertySources sources = environment.getPropertySources();
    if (sources.contains(SOURCE_NAME)) {
      return;
    }
    try {
      ClassLoader cl = SchoolOpsEnvironmentPostProcessor.class.getClassLoader();
      ClassPathResource resource = new ClassPathResource("school-ops.defaults.properties", cl);
      if (!resource.exists()) {
        return;
      }
      ResourcePropertySource loaded = new ResourcePropertySource(SOURCE_NAME, resource);
      // Insert with low precedence but still above servletConfig/random — application.properties
      // in each service also ships the same keys so fat-jar deployments are guaranteed covered.
      if (sources.contains("defaultProperties")) {
        sources.addBefore("defaultProperties", loaded);
      } else {
        sources.addLast(loaded);
      }
    } catch (IOException ex) {
      MapPropertySource fallback =
          new OriginTrackedMapPropertySource(
              SOURCE_NAME,
              Map.of(
                  "school.http.connect-timeout-ms", "3000",
                  "school.http.read-timeout-ms", "10000",
                  "management.endpoints.web.exposure.include", "health,info,prometheus,metrics",
                  "management.endpoint.health.probes.enabled", "true"));
      sources.addLast(fallback);
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
