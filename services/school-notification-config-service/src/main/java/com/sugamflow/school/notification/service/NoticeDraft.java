package com.sugamflow.school.notification.service;

import java.util.LinkedHashMap;
import java.util.Map;

/** A notice the office can edit. The student table is not part of this text. */
public final class NoticeDraft {

  private NoticeDraft() {}

  public static Map<String, Object> write(String language, String occasion) {
    boolean hindi = "hi".equalsIgnoreCase(language);
    String day =
        occasion == null || occasion.isBlank()
            ? (hindi ? "घोषित दिन" : "the announced day")
            : occasion.trim();
    Map<String, Object> draft = new LinkedHashMap<>();
    draft.put("language", hindi ? "hi" : "en");
    draft.put("title", hindi ? "विद्यालय सूचना" : "School notice");
    draft.put(
        "body",
        hindi
            ? "विद्यालय " + day + " को बंद रहेगा। अभिभावक कृपया ध्यान दें।"
            : "The school will remain closed on " + day + ". Guardians are requested to note this.");
    return draft;
  }
}
