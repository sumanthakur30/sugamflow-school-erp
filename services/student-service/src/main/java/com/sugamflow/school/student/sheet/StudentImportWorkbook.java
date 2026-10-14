package com.sugamflow.school.student.sheet;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds and reads the student Excel template. */
public final class StudentImportWorkbook {

  private StudentImportWorkbook() {}

  public static byte[] template() {
    try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet students = book.createSheet("Students");
      Sheet instructions = book.createSheet("Instructions");
      Sheet reference = book.createSheet("Field Reference");
      CellStyle text = book.createCellStyle();
      text.setDataFormat(book.createDataFormat().getFormat("@"));

      List<StudentSheetCatalog.Col> columns = StudentSheetCatalog.columns();
      Row header = students.createRow(0);
      Row sample = students.createRow(1);
      Map<String, String> sampleValues = sampleRow();
      for (int i = 0; i < columns.size(); i++) {
        StudentSheetCatalog.Col col = columns.get(i);
        header.createCell(i).setCellValue(col.label());
        Cell cell = sample.createCell(i);
        cell.setCellValue(sampleValues.getOrDefault(col.key(), ""));
        if (StudentSheetCatalog.isTextKey(col.key())) {
          students.setDefaultColumnStyle(i, text);
          cell.setCellStyle(text);
        }
        students.setColumnWidth(i, Math.min(40, Math.max(14, col.label().length() + 2)) * 256);
      }
      addList(students, columns, "Gender", StudentSheetCatalog.GENDERS);
      addList(students, columns, "Blood Group", StudentSheetCatalog.BLOOD_GROUPS);
      addList(students, columns, "Status", StudentSheetCatalog.STATUSES);

      writeLines(
          instructions,
          List.of(
              "Student import",
              "1. Fill the Students sheet. Do not rename the header row.",
              "2. Required for new students: Full Name, Class, and Mobile.",
              "3. Leave Admission No blank to let the school generate one.",
              "4. Update mode changes an existing student matched by Admission No, then PEN.",
              "5. Blank cells do not erase a value already stored.",
              "6. Father Name and Mother Name are saved on the student and as guardians.",
              "7. Photos are uploaded on the student profile. A Photo URL is kept only when it is an existing app attachment path.",
              "8. Aadhaar is optional. Do not share the full template outside the school office.",
              "9. The sample row uses fictional data. Delete it before importing real students."));
      Row refHeader = reference.createRow(0);
      refHeader.createCell(0).setCellValue("Column");
      refHeader.createCell(1).setCellValue("Required");
      refHeader.createCell(2).setCellValue("Notes");
      for (int i = 0; i < columns.size(); i++) {
        StudentSheetCatalog.Col col = columns.get(i);
        Row row = reference.createRow(i + 1);
        row.createCell(0).setCellValue(col.label());
        row.createCell(1).setCellValue(col.required() ? "Required for new students" : "Optional");
        row.createCell(2).setCellValue(col.hint());
      }
      book.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Could not build the student template", ex);
    }
  }

  public static List<Map<String, Object>> parse(byte[] bytes) {
    if (bytes == null || bytes.length == 0) {
      return List.of();
    }
    try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      Sheet sheet = book.getSheet("Students");
      if (sheet == null) {
        sheet = book.getNumberOfSheets() > 0 ? book.getSheetAt(0) : null;
      }
      if (sheet == null) {
        return List.of();
      }
      DataFormatter formatter = new DataFormatter();
      Row header = sheet.getRow(0);
      if (header == null) {
        return List.of();
      }
      List<String> headers = new ArrayList<>();
      for (int c = 0; c < header.getLastCellNum(); c++) {
        headers.add(formatter.formatCellValue(header.getCell(c)).trim());
      }
      List<Map<String, Object>> rows = new ArrayList<>();
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        Map<String, Object> mapped = new LinkedHashMap<>();
        boolean any = false;
        for (int c = 0; c < headers.size(); c++) {
          String name = headers.get(c);
          if (name.isEmpty()) {
            continue;
          }
          String value = cellText(row.getCell(c), formatter);
          if (!value.isEmpty()) {
            any = true;
          }
          mapped.put(name, value);
        }
        if (any) {
          rows.add(mapped);
        }
      }
      return rows;
    } catch (IOException ex) {
      throw new IllegalArgumentException("The Excel file could not be read");
    }
  }

  public static byte[] errorReport(List<Map<String, Object>> errors) {
    try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Sheet sheet = book.createSheet("Errors");
      Row header = sheet.createRow(0);
      header.createCell(0).setCellValue("Row");
      header.createCell(1).setCellValue("Error");
      header.createCell(2).setCellValue("Admission No");
      header.createCell(3).setCellValue("Full Name");
      int n = 1;
      for (Map<String, Object> error : errors) {
        Row row = sheet.createRow(n++);
        row.createCell(0).setCellValue(String.valueOf(error.getOrDefault("rowNumber", "")));
        row.createCell(1).setCellValue(String.valueOf(error.getOrDefault("errorMessage", "")));
        row.createCell(2).setCellValue(String.valueOf(error.getOrDefault("admissionNo", "")));
        row.createCell(3).setCellValue(String.valueOf(error.getOrDefault("fullName", "")));
      }
      book.write(out);
      return out.toByteArray();
    } catch (IOException ex) {
      throw new IllegalStateException("Could not build the error report", ex);
    }
  }

  public static byte[] decodeBase64(String raw) {
    if (raw == null || raw.isBlank()) {
      return new byte[0];
    }
    String payload = raw.trim();
    int comma = payload.indexOf(',');
    if (payload.startsWith("data:") && comma > 0) {
      payload = payload.substring(comma + 1);
    }
    return Base64.getDecoder().decode(payload);
  }

  private static void addList(Sheet sheet, List<StudentSheetCatalog.Col> columns, String label, List<String> values) {
    int index = -1;
    for (int i = 0; i < columns.size(); i++) {
      if (label.equals(columns.get(i).label())) {
        index = i;
        break;
      }
    }
    if (index < 0 || values.isEmpty()) {
      return;
    }
    DataValidationHelper helper = sheet.getDataValidationHelper();
    DataValidationConstraint constraint =
        helper.createExplicitListConstraint(values.toArray(String[]::new));
    CellRangeAddressList range = new CellRangeAddressList(1, 500, index, index);
    DataValidation validation = helper.createValidation(constraint, range);
    validation.setSuppressDropDownArrow(true);
    validation.setShowErrorBox(true);
    sheet.addValidationData(validation);
  }

  private static void writeLines(Sheet sheet, List<String> lines) {
    for (int i = 0; i < lines.size(); i++) {
      sheet.createRow(i).createCell(0).setCellValue(lines.get(i));
    }
    sheet.setColumnWidth(0, 120 * 256);
  }

  private static String cellText(Cell cell, DataFormatter formatter) {
    if (cell == null) {
      return "";
    }
    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      Date date = cell.getDateCellValue();
      if (date == null) {
        return "";
      }
      LocalDate local = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
      return local.toString();
    }
    return formatter.formatCellValue(cell).trim();
  }

  private static Map<String, String> sampleRow() {
    Map<String, String> row = new LinkedHashMap<>();
    row.put("admissionNo", "ADM-SAMPLE-001");
    row.put("fullName", "Asha Rao");
    row.put("dateOfBirth", "2014-05-12");
    row.put("gender", "Female");
    row.put("bloodGroup", "O+");
    row.put("status", "ACTIVE");
    row.put("admissionDate", "2026-04-01");
    row.put("classApplied", "Grade 7-A");
    row.put("classGrade", "7");
    row.put("sectionLetter", "A");
    row.put("rollNo", "12");
    row.put("mobile", "9800000001");
    row.put("email", "asha.rao@example.com");
    row.put("fatherName", "Ravi Rao");
    row.put("fatherMobile", "9800000002");
    row.put("motherName", "Meera Rao");
    row.put("motherMobile", "9800000003");
    row.put("address", "12 Sample Lane");
    row.put("city", "Ranchi");
    row.put("state", "Jharkhand");
    row.put("pinCode", "834001");
    row.put("nationality", "Indian");
    return row;
  }
}
