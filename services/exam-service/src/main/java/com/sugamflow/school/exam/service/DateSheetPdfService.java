package com.sugamflow.school.exam.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sugamflow.school.exam.web.ExamException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** One class date sheet from the exams already scheduled for a section. */
@Service
public class DateSheetPdfService {

  private static final Color HEADER_BG = new Color(15, 61, 46);

  public byte[] render(Map<String, Object> pack) {
    if (pack == null) {
      throw new ExamException("NOT_FOUND", "No date sheet to render");
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> papers = (List<Map<String, Object>>) pack.get("papers");
    if (papers == null || papers.isEmpty()) {
      throw new ExamException("NOT_FOUND", "No exams are scheduled for this class");
    }

    Document doc = new Document(PageSize.A4, 42, 42, 48, 42);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(doc, out);
      doc.open();
      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, HEADER_BG);
      Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
      Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
      Font body = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);

      Paragraph title = new Paragraph("Date sheet", titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      doc.add(title);
      Paragraph meta =
          new Paragraph(
              nz(pack.get("sectionLabel"), "Class")
                  + (pack.get("termKey") == null ? "" : " · " + pack.get("termKey")),
              subFont);
      meta.setAlignment(Element.ALIGN_CENTER);
      meta.setSpacingAfter(16);
      doc.add(meta);

      PdfPTable table = new PdfPTable(new float[] {2.2f, 3f, 3f});
      table.setWidthPercentage(100);
      header(table, "Date", head);
      header(table, "Subject", head);
      header(table, "Exam", head);
      for (Map<String, Object> paper : papers) {
        cell(table, nz(paper.get("examDate"), "To be announced"), body);
        cell(table, nz(paper.get("subject"), "Subject"), body);
        cell(table, nz(paper.get("name"), "Exam"), body);
      }
      doc.add(table);
      doc.close();
      return out.toByteArray();
    } catch (ExamException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExamException("PDF", "Could not render the date sheet");
    }
  }

  private static void header(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBackgroundColor(HEADER_BG);
    cell.setPadding(6);
    table.addCell(cell);
  }

  private static void cell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setPadding(6);
    table.addCell(cell);
  }

  private static String nz(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
