package com.sugamflow.school.cms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Website AI assist. Local structured drafts always available; optional OpenAI-compatible chat
 * completions when {@code cms.ai.api-url} + key are set.
 */
@Service
public class CmsAiAssistService {

  private static final String FEATURE = "FEATURE_WEBSITE_AI";

  private final SubscriptionEntitlementsClient entitlementsClient;
  private final RestClient.Builder restClientBuilder;
  private final ObjectMapper objectMapper;
  private final String apiUrl;
  private final String apiKey;
  private final String model;

  public CmsAiAssistService(
      SubscriptionEntitlementsClient entitlementsClient,
      RestClient.Builder restClientBuilder,
      ObjectMapper objectMapper,
      @Value("${cms.ai.api-url:}") String apiUrl,
      @Value("${cms.ai.api-key:}") String apiKey,
      @Value("${cms.ai.model:gpt-4o-mini}") String model) {
    this.entitlementsClient = entitlementsClient;
    this.restClientBuilder = restClientBuilder;
    this.objectMapper = objectMapper;
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

    Map<String, Object> local = localDraft(kind, topic, tone);
    if (!apiUrl.isBlank() && !apiKey.isBlank()) {
      try {
        Map<String, Object> external = callExternal(kind, topic, tone);
        external.putIfAbsent("kind", kind);
        external.put("fallbackUsed", false);
        return external;
      } catch (Exception ex) {
        local.put("fallbackUsed", true);
        local.put("fallbackReason", ex.getMessage());
      }
    }
    return local;
  }

  private Map<String, Object> localDraft(String kind, String topic, String tone) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("kind", kind);
    out.put("provider", "local");
    String title = topic.trim();
    String toneCap =
        tone.isBlank() ? "Professional" : Character.toUpperCase(tone.charAt(0)) + tone.substring(1);
    switch (kind) {
      case "seo" -> {
        out.put("seoTitle", title + " | Official School Website");
        out.put(
            "seoDescription",
            toneCap
                + " overview of "
                + title
                + " for parents, students, and visitors. Learn programs, admissions, and campus life.");
        out.put("keywords", title.toLowerCase(Locale.ROOT) + ", school, admission, campus");
      }
      case "blog" -> {
        out.put("title", title);
        out.put("summary", toneCap + " notes on " + title + " from our campus community.");
        out.put(
            "bodyHtml",
            "<p>"
                + escape(title)
                + "</p><p>This blog draft is ready for editors to refine ("
                + escape(tone)
                + ").</p><h3>Highlights</h3><ul><li>What happened</li><li>Why it matters</li><li>How families can join</li></ul>");
      }
      case "alumni" -> {
        out.put("title", title);
        out.put("headline", toneCap + " alumni story");
        out.put(
            "bioHtml",
            "<p>"
                + escape(title)
                + " is a proud alumnus of our school.</p><p>They continue to inspire current students through mentorship and community service.</p>");
        out.put("summary", "Alumni spotlight: " + title);
      }
      default -> {
        out.put("title", title);
        out.put("summary", "A " + tone + " introduction to " + title + " for our school community.");
        out.put(
            "bodyHtml",
            "<p>"
                + escape(title)
                + " is an important part of campus life.</p><p>This draft was generated for editors to refine ("
                + escape(tone)
                + ").</p><ul><li>Key highlights</li><li>How to get involved</li><li>Contact the school office</li></ul>");
      }
    }
    return out;
  }

  private Map<String, Object> callExternal(String kind, String topic, String tone) {
    String system =
        """
        You are a school website CMS writing assistant. Respond with ONLY compact JSON keys:
        title, summary, bodyHtml, seoTitle, seoDescription, headline, keywords.
        Omit unused keys. bodyHtml must be simple HTML paragraphs/lists. Kind=%s
        """
            .formatted(kind);
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("model", model);
    payload.put(
        "messages",
        java.util.List.of(
            Map.of("role", "system", "content", system),
            Map.of(
                "role",
                "user",
                "content",
                "Topic: " + topic + ". Tone: " + tone + ". Produce JSON only.")));
    payload.put("temperature", 0.4);

    String raw =
        restClientBuilder
            .build()
            .post()
            .uri(apiUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + apiKey)
            .body(payload)
            .retrieve()
            .body(String.class);

    Map<String, Object> out = localDraft(kind, topic, tone);
    out.put("provider", "external");
    if (raw == null || raw.isBlank()) {
      return out;
    }
    try {
      JsonNode root = objectMapper.readTree(raw);
      String content = extractContent(root);
      if (content != null && !content.isBlank()) {
        String json = extractJsonObject(content);
        JsonNode parsed = objectMapper.readTree(json);
        putIfText(out, parsed, "title");
        putIfText(out, parsed, "summary");
        putIfText(out, parsed, "bodyHtml");
        putIfText(out, parsed, "seoTitle");
        putIfText(out, parsed, "seoDescription");
        putIfText(out, parsed, "headline");
        putIfText(out, parsed, "keywords");
      }
    } catch (Exception ex) {
      out.put("parseWarning", ex.getMessage());
    }
    return out;
  }

  private static String extractContent(JsonNode root) {
    JsonNode choices = root.path("choices");
    if (choices.isArray() && !choices.isEmpty()) {
      return choices.get(0).path("message").path("content").asText(null);
    }
    return root.path("content").asText(null);
  }

  private static String extractJsonObject(String content) {
    String trimmed = content.trim();
    if (trimmed.startsWith("```")) {
      int start = trimmed.indexOf('{');
      int end = trimmed.lastIndexOf('}');
      if (start >= 0 && end > start) {
        return trimmed.substring(start, end + 1);
      }
    }
    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return trimmed;
  }

  private static void putIfText(Map<String, Object> out, JsonNode node, String field) {
    JsonNode v = node.get(field);
    if (v != null && v.isTextual() && !v.asText().isBlank()) {
      out.put(field, v.asText());
    }
  }

  private static String string(Object value, String fallback) {
    if (value == null) return fallback;
    String s = String.valueOf(value).trim();
    return s.isBlank() ? fallback : s;
  }

  private static String escape(String value) {
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
