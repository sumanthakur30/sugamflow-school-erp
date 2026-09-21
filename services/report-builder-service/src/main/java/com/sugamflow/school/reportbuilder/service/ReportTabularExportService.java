package com.sugamflow.school.reportbuilder.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * Tabular exports for registers / directories — columns+rows driven, no school-specific layouts.
 */
@Service
public class ReportTabularExportService {

  public Map<String, Object> export(String format, Map<String, Object> data) {
    String fmt = format == null ? "PDF" : format.trim().toUpperCase();
    List<Map<String, Object>> columns = columns(data);
    List<Map<String, Object>> rows = rows(data);
    String title = stringOr(data.get("title"), "Register");
    String subtitle = stringOr(data.get("subtitle"), "");
    return switch (fmt) {
      case "CSV" -> encode("text/csv", title + ".csv", toCsv(columns, rows));
      case "EXCEL", "XLSX" -> encode(
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
          title.replaceAll("\\s+", "-").toLowerCase() + ".xlsx",
          toXlsx(title, columns, rows));
      case "PDF", "PRINT" -> encode("application/pdf", title.replaceAll("\\s+", "-").toLowerCase() + ".pdf", toPdf(title, subtitle, columns, rows));
      default -> throw new IllegalArgumentException("Unsupported tabular format: " + format);
    };
  }

  public boolean isTabular(Map<String, Object> data) {
    return data != null && data.get("columns") instanceof List<?> && data.get("rows") instanceof List<?>;
  }

  private static Map<String, Object> encode(String contentType, String fileName, byte[] bytes) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("contentType", contentType);
    out.put("fileName", fileName);
    out.put("contentBase64", java.util.Base64.getEncoder().encodeToString(bytes));
    out.put("byteLength", bytes.length);
    return out;
  }

  private static byte[] toCsv(List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < columns.size(); i++) {
      if (i > 0) sb.append(',');
      sb.append(csv(columns.get(i).get("label")));
    }
    sb.append('\n');
    for (Map<String, Object> row : rows) {
      for (int i = 0; i < columns.size(); i++) {
        if (i > 0) sb.append(',');
        String key = stringOr(columns.get(i).get("key"), "");
        sb.append(csv(row.get(key)));
      }
      sb.append('\n');
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] toXlsx(String title, List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
    try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      Sheet sheet = wb.createSheet(safeSheet(title));
      CellStyle header = wb.createCellStyle();
      org.apache.poi.ss.usermodel.Font font = wb.createFont();
      font.setBold(true);
      header.setFont(font);
      Row headerRow = sheet.createRow(0);
      for (int i = 0; i < columns.size(); i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(stringOr(columns.get(i).get("label"), stringOr(columns.get(i).get("key"), "")));
        cell.setCellStyle(header);
      }
      int r = 1;
      for (Map<String, Object> row : rows) {
        Row excelRow = sheet.createRow(r++);
        for (int i = 0; i < columns.size(); i++) {
          String key = stringOr(columns.get(i).get("key"), "");
          excelRow.createCell(i).setCellValue(stringOr(row.get(key), ""));
        }
      }
      for (int i = 0; i < columns.size(); i++) {
        sheet.autoSizeColumn(i);
      }
      wb.write(baos);
      return baos.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("Excel export failed: " + ex.getMessage(), ex);
    }
  }

  private static byte[] toPdf(
      String title, String subtitle, List<Map<String, Object>> columns, List<Map<String, Object>> rows) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
    try {
      PdfWriter.getInstance(document, baos);
      document.open();
      Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
      Font body = FontFactory.getFont(FontFactory.HELVETICA, 9);
      Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
      document.add(new Paragraph(title, heading));
      if (!subtitle.isBlank()) {
        document.add(new Paragraph(subtitle, small));
      }
      document.add(new Paragraph(" "));
      if (columns.isEmpty()) {
        document.add(new Paragraph("No columns configured.", body));
        document.close();
        return baos.toByteArray();
      }
      PdfPTable table = new PdfPTable(columns.size());
      table.setWidthPercentage(100f);
      for (Map<String, Object> col : columns) {
        PdfPCell cell =
            new PdfPCell(
                new Phrase(stringOr(col.get("label"), stringOr(col.get("key"), "")), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8)));
        cell.setBackgroundColor(new Color(230, 236, 241));
        cell.setPadding(4f);
        table.addCell(cell);
      }
      for (Map<String, Object> row : rows) {
        for (Map<String, Object> col : columns) {
          String key = stringOr(col.get("key"), "");
          PdfPCell cell = new PdfPCell(new Phrase(stringOr(row.get(key), ""), body));
          cell.setPadding(3f);
          table.addCell(cell);
        }
      }
      document.add(table);
      document.close();
      return baos.toByteArray();
    } catch (DocumentException ex) {
      throw new IllegalStateException("Register PDF failed: " + ex.getMessage(), ex);
    }
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> columns(Map<String, Object> data) {
    Object raw = data.get("columns");
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        out.add((Map<String, Object>) m);
      } else if (item != null) {
        out.add(Map.of("key", String.valueOf(item), "label", String.valueOf(item)));
      }
    }
    return out;
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> data) {
    Object raw = data.get("rows");
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Object item : list) {
      if (item instanceof Map<?, ?> m) {
        out.add((Map<String, Object>) m);
      }
    }
    return out;
  }

  private static String safeSheet(String title) {
    String s = title == null ? "Sheet1" : title.replaceAll("[\\\\/?*\\[\\]]", " ").trim();
    if (s.isBlank()) s = "Sheet1";
    return s.length() > 31 ? s.substring(0, 31) : s;
  }

  private static String csv(Object v) {
    String s = stringOr(v, "");
    if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
      return "\"" + s.replace("\"", "\"\"") + "\"";
    }
    return s;
  }

  private static String stringOr(Object v, String fallback) {
    if (v == null) return fallback;
    String s = String.valueOf(v).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
