package com.sugamflow.school.admission.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdmissionFieldSuggestTest {

  @Test
  void anUploadSuggestsFieldsAndThePaperStaysAttachedOnlyWhenSaved() {
    String paper =
        """
        Name: Asha Kumar
        Date of birth: 2014-04-02
        Mobile: 9876543210
        Class: 7
        Father: Ravi Kumar
        """;

    Map<String, String> fields = AdmissionFieldSuggest.suggest("asha.txt", paper);

    assertEquals("Asha Kumar", fields.get("fullName"));
    assertEquals("2014-04-02", fields.get("dateOfBirth"));
    assertEquals("9876543210", fields.get("mobile"));
    assertEquals("7", fields.get("classApplied"));
    assertEquals("Ravi Kumar", fields.get("guardianName"));

    List<Map<String, Object>> saved =
        AdmissionPaper.attach(
            List.of(Map.of("type", "OFFER_LETTER", "fileName", "offer.pdf")),
            List.of(Map.of("fileName", "asha.txt", "mediaType", "text/plain", "students", "hidden")));

    assertEquals(2, saved.size());
    assertEquals("OFFER_LETTER", saved.get(0).get("type"));
    assertEquals("asha.txt", saved.get(1).get("fileName"));
    assertEquals(AdmissionPaper.TYPE, saved.get(1).get("type"));
    assertTrue(!saved.get(1).containsKey("students"));
  }
}
