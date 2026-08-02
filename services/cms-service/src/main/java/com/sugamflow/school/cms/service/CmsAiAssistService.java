package com.sugamflow.school.cms.service;

import com.sugamflow.school.cms.integration.SubscriptionEntitlementsClient;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Website AI assist. Default: deterministic local drafts (no external call). Optional OpenAI-compatible
 * endpoint when {@code cms.ai.api-url} is set.
 */
@Service
public class CmsAiAssistService {

  private static final String FEATURE = "FEATURE_WEBSITE_AI";

  private final SubscriptionEntitlementsClient entitlementsClient;
  private final RestClient.Builder restClientBuilder;
  private final String apiUrl;
  private final String apiKey;
  private final String model;

  public CmsAiAssistService(
      SubscriptionEntitlementsClient entitlementsClient,
      RestClient.Builder restClientBuilder,
      @Value("${cms.ai.api-url:}") String apiUrl,
      @Value("${cms.ai.api-key:}") String apiKey,
      @Value("${cms.ai.model:gpt-4o-mini}") String model) {
    this.entitlementsClient = entitlementsClient;
    this.restClientBuilder = restClientBuilder;
    this.apiUrl = apiUrl == null ? "" : apiUrl.trim();
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.model = model;
  }

  public Map<String, Object> draft(String organizationId, Map<String, Object> body) {
    if (!entitlementsClient.isFeatureEnabled(organizationId, FEATURE)) {
      throw new ResponseStatusException(
          HttpStatus.PAYMENT_REQUIRED, "FEATURE_WEBSITE_AI is not enabled");
    }
    String kind = string(body.get("kind"), "page").toLowerCase(Locale.ROOT);
    String topic = string(body.get("topic"), "School update");
    String tone = string(body.get("tone"), "warm and professional");

    if (!apiUrl.isBlank() && !apiKey.isBlank()) {
      try {
        return callExternal(kind, topic, tone);
      } catch (Exception ignored) {
        // fall through to local draft
      }
    }
    return localDraft(kind, topic, tone);
  }

  private Map<String, Object> localDraft(String kind, String topic, String tone) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", kind);
    out.put("provider", "local");
    String title = topic.trim();
    if ("seo".equals(kind)) {
      out.put("seoTitle", title + " | Official School Website");
      out.put(
          "seoDescription",
          "Learn more about "
              + title
              + ". "
              + Character.toUpperCase(tone.charAt(0))
              + tone.substring(1)
              + " overview for parents and students.");
      out.put("bodyHtml", "");
    } else {
      out.put("title", title);
      out.put(
          "summary",
          "A "
              + tone
              + " introduction to "
              + title
              + " for our school community.");
      out.put(
          "bodyHtml",
          "<p>"
              + escape(title)
              + " is an important part of campus life.</p><p>This draft was generated locally for editors to refine ("
              + escape(tone)
              + ").</p><ul><li>Key highlights</li><li>How to get involved</li><li>Contact the school office</li></ul>");
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> callExternal(String kind, String topic, String tone) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put(
        "messages",
        java.util.List.of(
            Map.of(
                "role",
                "system",
                "content",
                "You help school CMS editors. Return concise HTML body and short summary. Kind="
                    + kind),
            Map.of(
                "role",
                "user",
                "content",
                "Topic: " + topic + ". Tone: " + tone + ". Produce title, summary, bodyHtml.")));
    Map<String, Object> response =
        restClientBuilder
            .build()
            .post()
            .uri(apiUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey)
            .body(payload)
            .retrieve()
            .body(Map.class);
    Map<String, Object> out = localDraft(kind, topic, tone);
    out.put("provider", "external");
    if (response != null) {
      out.put("raw", response);
    }
    return out;
  }

  private static String string(Object value, String fallback) {
    if (value == null) return fallback;
    String s = String.valueOf(value).trim();
    return s.isBlank() ? fallback : s;
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;");
  }
}
