package com.sugamflow.school.student.sheet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class StudentImportWorkbookTest {

  @Test
  void templateHasSheetsHeadersDropdownsAndOneSampleRow() throws Exception {
    byte[] bytes = StudentImportWorkbook.template();
    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      assertEquals("Students", book.getSheetAt(0).getSheetName());
      assertEquals("Instructions", book.getSheetAt(1).getSheetName());
      assertEquals("Field Reference", book.getSheetAt(2).getSheetName());
      assertEquals("Admission No", book.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
      assertEquals("Asha Rao", book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
      assertTrue(book.getSheetAt(0).getDataValidations().size() >= 3);
      assertEquals(1, book.getSheetAt(0).getLastRowNum());
    }

    List<Map<String, Object>> rows = StudentImportWorkbook.parse(bytes);
    assertEquals(1, rows.size());
    Map<String, Object> mapped = StudentSheetCatalog.mapRow(rows.get(0), null);
    StudentSheetCatalog.normalize(mapped);
    assertEquals("Female", mapped.get("gender"));
    assertEquals("Ravi Rao", mapped.get("fatherName"));
    assertEquals("Grade 7-A", mapped.get("classApplied"));
  }
}
