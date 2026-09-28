package com.sugamflow.school.admission.lead;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sugamflow.school.admission.web.AdmissionException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LeadDeskRulesTest {

  @Test
  void parsesStatusAliases() {
    assertEquals(LeadStatus.NOT_INTERESTED, LeadStatus.parse("not interested"));
    assertEquals(LeadStatus.CALL_BACK, LeadStatus.parse("callback"));
    assertEquals(LeadStatus.BLACKLISTED, LeadStatus.parse("Dropped"));
    assertEquals(LeadStatus.ADMISSION_CREATED, LeadStatus.parse("admitted"));
    assertEquals(LeadStatus.INTERESTED, LeadStatus.parse(" "));
  }

  @Test
  void rejectsUnknownStatus() {
    assertThrows(AdmissionException.class, () -> LeadStatus.parse("maybe"));
  }

  @Test
  void readsQuotedCsv() {
    String csv =
        """
        studentName,fatherName,phone,status,remark
        "Aman Jain","Kishan, Saroj",9875486857,interested,"call later"
        """;
    var rows = LeadImportParser.parseCsv(csv);
    assertEquals(1, rows.size());
    assertEquals("Aman Jain", rows.get(0).studentName());
    assertEquals("Kishan, Saroj", rows.get(0).fatherName());
    assertEquals("call later", rows.get(0).remark());
  }

  @Test
  void parsesSchoolLocalTime() {
    Instant instant = LeadDeskService.parseWhen("2026-09-09T08:12");
    assertTrue(instant.toString().startsWith("2026-09-09T02:42"));
  }
}
