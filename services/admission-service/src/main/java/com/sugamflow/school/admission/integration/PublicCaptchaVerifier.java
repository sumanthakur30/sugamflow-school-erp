package com.sugamflow.school.admission.integration;

import com.sugamflow.school.admission.config.AdmissionProperties;
import com.sugamflow.school.admission.web.AdmissionException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/** Optional reCAPTCHA verification for public website admission. */
@Component
public class PublicCaptchaVerifier {

  private static final Logger log = LoggerFactory.getLogger(PublicCaptchaVerifier.class);

  private final AdmissionProperties properties;
  private final RestClient.Builder restClientBuilder;

  public PublicCaptchaVerifier(
      AdmissionProperties properties, RestClient.Builder restClientBuilder) {
    this.properties = properties;
    this.restClientBuilder = restClientBuilder;
  }

  public void verifyIfRequired(String captchaToken) {
    AdmissionProperties.PublicCaptcha cfg = properties.getPublicCaptcha();
    if (!cfg.isEnabled()) {
      return;
    }
    if (captchaToken == null || captchaToken.isBlank()) {
      throw new AdmissionException("CAPTCHA_REQUIRED", "captchaToken is required");
    }
    String secret = cfg.getSecret();
    if (secret == null || secret.isBlank()) {
      // Enabled without Google secret: accept any non-blank token (staging / honeypot mode).
      return;
    }
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("secret", secret);
      form.add("response", captchaToken.trim());
      @SuppressWarnings("unchecked")
      Map<String, Object> body =
          restClientBuilder
              .build()
              .post()
              .uri(cfg.getVerifyUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);
      if (body == null || !Boolean.TRUE.equals(body.get("success"))) {
        throw new AdmissionException("CAPTCHA_FAILED", "Captcha verification failed");
      }
    } catch (AdmissionException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("Captcha verify error: {}", ex.getMessage());
      throw new AdmissionException("CAPTCHA_FAILED", "Captcha verification failed");
    }
  }
}
