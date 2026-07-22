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

/** Renders a single-student term report card as a PDF using OpenPDF. */
@Service
public class ReportCardPdfService {

  private static final Color HEADER_BG = new Color(15, 61, 46);
  private static final Color ROW_ALT = new Color(244, 247, 245);

  /**
   * @param card the output of {@link ReportCardService#studentTermReport} (has {@code student}) or a
   *     card produced for a single student.
   * @param schoolName tenant/org display name for the masthead.
   */
  public byte[] render(Map<String, Object> card, String schoolName) {
    if (card == null) {
      throw new ExamException("NOT_FOUND", "No report card to render");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> student = (Map<String, Object>) card.get("student");
    if (student == null) {
      throw new ExamException("VALIDATION", "Report card has no student payload");
    }

    Document doc = new Document(PageSize.A4, 42, 42, 48, 42);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(doc, out);
      doc.open();

      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, HEADER_BG);
      Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
      Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
      Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);

      Paragraph title = new Paragraph(nz(schoolName, "School"), titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      doc.add(title);

      Paragraph sub =
          new Paragraph(
              "Report Card — " + nz(str(card.get("termKey")), "Term") + "  ·  "
                  + nz(str(card.get("sectionLabel")), ""),
              subFont);
      sub.setAlignment(Element.ALIGN_CENTER);
      sub.setSpacingAfter(14f);
      doc.add(sub);

      PdfPTable info = new PdfPTable(new float[] {1f, 2f, 1f, 1.4f});
      info.setWidthPercentage(100);
      infoCell(info, "Student", labelFont);
      infoCell(info, nz(str(student.get("studentName")), "-"), valueFont);
      infoCell(info, "Admission", labelFont);
      infoCell(info, nz(str(student.get("admissionNo")), "-"), valueFont);
      infoCell(info, "Class", labelFont);
      infoCell(info, nz(str(student.get("classSection")), "-"), valueFont);
      infoCell(info, "Result", labelFont);
      infoCell(info, nz(str(student.get("result")), "-"), valueFont);
      info.setSpacingAfter(16f);
      doc.add(info);

      PdfPTable table = new PdfPTable(new float[] {4f, 1.6f, 1.6f, 1.6f, 1.4f});
      table.setWidthPercentage(100);
      headerCell(table, "Subject");
      headerCell(table, "Marks");
      headerCell(table, "Max");
      headerCell(table, "%");
      headerCell(table, "Grade");

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> subjects = (List<Map<String, Object>>) student.get("subjects");
      boolean alt = false;
      if (subjects != null) {
        for (Map<String, Object> s : subjects) {
          Color bg = alt ? ROW_ALT : Color.WHITE;
          alt = !alt;
          bodyCell(table, nz(str(s.get("name")), "-"), Element.ALIGN_LEFT, bg, valueFont);
          bodyCell(table, nz(str(s.get("marksObtained")), "-"), Element.ALIGN_CENTER, bg, valueFont);
          bodyCell(table, nz(str(s.get("maxMarks")), "-"), Element.ALIGN_CENTER, bg, valueFont);
          bodyCell(table, subjectPct(s), Element.ALIGN_CENTER, bg, valueFont);
          bodyCell(table, nz(str(s.get("grade")), "-"), Element.ALIGN_CENTER, bg, valueFont);
        }
      }

      Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, HEADER_BG);
      bodyCell(table, "Total", Element.ALIGN_LEFT, ROW_ALT, totalFont);
      bodyCell(table, nz(str(student.get("totalObtained")), "-"), Element.ALIGN_CENTER, ROW_ALT, totalFont);
      bodyCell(table, nz(str(student.get("totalMax")), "-"), Element.ALIGN_CENTER, ROW_ALT, totalFont);
      bodyCell(table, pctText(student.get("percentage")), Element.ALIGN_CENTER, ROW_ALT, totalFont);
      bodyCell(table, nz(str(student.get("overallGrade")), "-"), Element.ALIGN_CENTER, ROW_ALT, totalFont);
      doc.add(table);

      Paragraph summary =
          new Paragraph(
              "Overall: "
                  + pctText(student.get("percentage"))
                  + "  ·  Grade "
                  + nz(str(student.get("overallGrade")), "-")
                  + "  ·  "
                  + nz(str(student.get("result")), "-"),
              FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, HEADER_BG));
      summary.setSpacingBefore(18f);
      doc.add(summary);

      @SuppressWarnings("unchecked")
      Map<String, Object> insights = (Map<String, Object>) card.get("insights");
      if (insights != null) {
        addInsights(doc, insights);
      }

      Paragraph note =
          new Paragraph(
              "This is a system-generated report card based on published examination marks.",
              FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY));
      note.setSpacingBefore(28f);
      doc.add(note);

      doc.close();
      return out.toByteArray();
    } catch (ExamException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExamException("PDF_ERROR", "Failed to render report card PDF: " + ex.getMessage());
    }
  }

  private static void headerCell(PdfPTable table, String text) {
    PdfPCell cell =
        new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
    cell.setBackgroundColor(HEADER_BG);
    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
    cell.setPadding(6f);
    table.addCell(cell);
  }

  private static void bodyCell(PdfPTable table, String text, int align, Color bg, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBackgroundColor(bg);
    cell.setHorizontalAlignment(align);
    cell.setPadding(5f);
    table.addCell(cell);
  }

  private static void infoCell(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBorder(0);
    cell.setPadding(3f);
    table.addCell(cell);
  }

  private static void addInsights(Document doc, Map<String, Object> insights) throws Exception {
    Font heading = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, HEADER_BG);
    Font body = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
    Font item = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);

    Paragraph title = new Paragraph("Performance insights", heading);
    title.setSpacingBefore(16f);
    title.setSpacingAfter(5f);
    doc.add(title);

    @SuppressWarnings("unchecked")
    Map<String, Object> summary = (Map<String, Object>) insights.get("summary");
    if (summary != null && summary.get("overallMessage") != null) {
      Paragraph message = new Paragraph(String.valueOf(summary.get("overallMessage")), body);
      message.setSpacingAfter(7f);
      doc.add(message);
    }

    addInsightItems(doc, "Strengths", insights.get("strengths"), item);
    addInsightItems(doc, "Focus areas", insights.get("focusAreas"), item);

    Object actionsRaw = insights.get("recommendedActions");
    if (actionsRaw instanceof List<?> actions && !actions.isEmpty()) {
      doc.add(new Paragraph("Recommended next steps", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, HEADER_BG)));
      for (Object action : actions) {
        doc.add(new Paragraph("• " + action, item));
      }
    }

    if (insights.get("disclaimer") != null) {
      Paragraph disclaimer =
          new Paragraph(
              String.valueOf(insights.get("disclaimer")),
              FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY));
      disclaimer.setSpacingBefore(6f);
      doc.add(disclaimer);
    }
  }

  private static void addInsightItems(
      Document doc, String label, Object rawItems, Font font) throws Exception {
    if (!(rawItems instanceof List<?> items) || items.isEmpty()) return;
    doc.add(
        new Paragraph(
            label, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, HEADER_BG)));
    for (Object raw : items) {
      if (!(raw instanceof Map<?, ?> map)) continue;
      String subject = nz(str(map.get("subject")), "Subject");
      String percentage = pctText(map.get("percentage"));
      String detail = nz(str(map.get("recommendation")), "");
      doc.add(new Paragraph("• " + subject + " (" + percentage + "): " + detail, font));
    }
  }

  private static String subjectPct(Map<String, Object> s) {
    Object obtained = s.get("marksObtained");
    Object max = s.get("maxMarks");
    if (obtained == null || max == null) {
      return "-";
    }
    try {
      double o = Double.parseDouble(String.valueOf(obtained));
      double m = Double.parseDouble(String.valueOf(max));
      if (m <= 0) {
        return "-";
      }
      return String.format("%.1f", o / m * 100);
    } catch (NumberFormatException ex) {
      return "-";
    }
  }

  private static String pctText(Object pct) {
    if (pct == null) {
      return "-";
    }
    try {
      return String.format("%.2f%%", Double.parseDouble(String.valueOf(pct)));
    } catch (NumberFormatException ex) {
      return String.valueOf(pct);
    }
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static String nz(String v, String fallback) {
    return v == null || v.isBlank() ? fallback : v;
  }
}
