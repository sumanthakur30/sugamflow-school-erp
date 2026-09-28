package com.sugamflow.school.admission.lead;

import com.sugamflow.school.admission.web.AdmissionException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class LeadImportParser {

  public static final int MAX_ROWS = 500;

  public static final List<String> HEADERS =
      List.of(
          "studentName",
          "fatherName",
          "motherName",
          "phone",
          "fatherPhone",
          "motherPhone",
          "address",
          "classAppliedFor",
          "scheduledAt",
          "status",
          "remark",
          "assignedTo",
          "admissionNo");

  private LeadImportParser() {}

  public record ImportedLead(
      String studentName,
      String fatherName,
      String motherName,
      String phone,
      String fatherPhone,
      String motherPhone,
      String address,
      String classAppliedFor,
      String scheduledAt,
      String status,
      String remark,
      String assignedTo,
      String admissionNo,
      int line) {}

  public static List<ImportedLead> parseCsv(String text) {
    if (text == null || text.isBlank()) {
      throw new AdmissionException("VALIDATION", "The import file is empty.");
    }
    String body = text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    try (BufferedReader reader = new BufferedReader(new StringReader(body))) {
      String headerLine = reader.readLine();
      if (headerLine == null) {
        throw new AdmissionException("VALIDATION", "The import file is empty.");
      }
      List<String> headers = splitCsv(headerLine);
      Map<String, Integer> index = headerIndex(headers);
      List<ImportedLead> rows = new ArrayList<>();
      String line;
      int lineNo = 1;
      while ((line = reader.readLine()) != null) {
        lineNo++;
        if (line.isBlank()) {
          continue;
        }
        rows.add(toLead(index, splitCsv(line), lineNo));
        if (rows.size() > MAX_ROWS) {
          throw new AdmissionException("VALIDATION", "Import is limited to " + MAX_ROWS + " leads.");
        }
      }
      return rows;
    } catch (IOException ex) {
      throw new AdmissionException("VALIDATION", "Could not read the CSV file.");
    }
  }

  public static List<ImportedLead> parseXlsx(InputStream input) {
    try (Workbook workbook = new XSSFWorkbook(input)) {
      Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
      if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
        throw new AdmissionException("VALIDATION", "The import file is empty.");
      }
      DataFormatter formatter = new DataFormatter();
      Row header = sheet.getRow(0);
      if (header == null) {
        throw new AdmissionException("VALIDATION", "The import file has no header row.");
      }
      List<String> headers = new ArrayList<>();
      for (int i = 0; i < header.getLastCellNum(); i++) {
        headers.add(cell(formatter, header.getCell(i)));
      }
      Map<String, Integer> index = headerIndex(headers);
      List<ImportedLead> rows = new ArrayList<>();
      for (int r = 1; r <= sheet.getLastRowNum(); r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          continue;
        }
        List<String> values = new ArrayList<>();
        boolean any = false;
        for (int c = 0; c < headers.size(); c++) {
          String value = cell(formatter, row.getCell(c));
          if (!value.isBlank()) {
            any = true;
          }
          values.add(value);
        }
        if (!any) {
          continue;
        }
        rows.add(toLead(index, values, r + 1));
        if (rows.size() > MAX_ROWS) {
          throw new AdmissionException("VALIDATION", "Import is limited to " + MAX_ROWS + " leads.");
        }
      }
      return rows;
    } catch (IOException ex) {
      throw new AdmissionException("VALIDATION", "Could not read the Excel file.");
    }
  }

  private static String cell(DataFormatter formatter, Cell cell) {
    if (cell == null) {
      return "";
    }
    return formatter.formatCellValue(cell).trim();
  }

  private static Map<String, Integer> headerIndex(List<String> headers) {
    Map<String, Integer> index = new LinkedHashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      String key = normalizeHeader(headers.get(i));
      if (!key.isBlank()) {
        index.put(key, i);
      }
    }
    if (!index.containsKey("studentname") || !index.containsKey("phone")) {
      throw new AdmissionException(
          "VALIDATION", "Import needs studentName and phone columns.");
    }
    return index;
  }

  static String normalizeHeader(String raw) {
    if (raw == null) {
      return "";
    }
    return raw.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("_", "");
  }

  private static ImportedLead toLead(Map<String, Integer> index, List<String> values, int line) {
    return new ImportedLead(
        value(index, values, "studentname"),
        value(index, values, "fathername"),
        value(index, values, "mothername"),
        value(index, values, "phone"),
        value(index, values, "fatherphone"),
        value(index, values, "motherphone"),
        value(index, values, "address"),
        value(index, values, "classappliedfor"),
        value(index, values, "scheduledat"),
        value(index, values, "status"),
        value(index, values, "remark"),
        value(index, values, "assignedto"),
        value(index, values, "admissionno"),
        line);
  }

  private static String value(Map<String, Integer> index, List<String> values, String key) {
    Integer at = index.get(key);
    if (at == null || at < 0 || at >= values.size()) {
      return "";
    }
    return values.get(at) == null ? "" : values.get(at).trim();
  }

  static List<String> splitCsv(String line) {
    List<String> cells = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == '"') {
        if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          current.append('"');
          i++;
        } else {
          quoted = !quoted;
        }
      } else if (ch == ',' && !quoted) {
        cells.add(current.toString().trim());
        current.setLength(0);
      } else {
        current.append(ch);
      }
    }
    cells.add(current.toString().trim());
    return cells;
  }
}
