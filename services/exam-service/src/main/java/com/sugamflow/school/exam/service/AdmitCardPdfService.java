package com.sugamflow.school.exam.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sugamflow.school.exam.web.ExamException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** One admit card page per student, listing the class exams already on the timetable of papers. */
@Service
public class AdmitCardPdfService {

  private static final Color HEADER_BG = new Color(15, 61, 46);

  public byte[] render(Map<String, Object> pack, String schoolName) {
    if (pack == null) {
      throw new ExamException("NOT_FOUND", "No admit card to render");
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> students = (List<Map<String, Object>>) pack.get("students");
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> papers = (List<Map<String, Object>>) pack.get("papers");
    if (students == null || students.isEmpty()) {
      throw new ExamException("NOT_FOUND", "No students on this admit card");
    }

    Document doc = new Document(PageSize.A4, 42, 42, 48, 42);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(doc, out);
      doc.open();
      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, HEADER_BG);
      Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
      Font body = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
      boolean first = true;
      for (Map<String, Object> student : students) {
        if (!first) {
          doc.newPage();
        }
        first = false;
        Paragraph title = new Paragraph(nz(schoolName, "School"), titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        Paragraph sub =
            new Paragraph(
                "Admit Card"
                    + (pack.get("termKey") == null ? "" : " — " + pack.get("termKey"))
                    + "  ·  "
                    + nz(str(pack.get("sectionLabel")), ""),
                subFont);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(16f);
        doc.add(sub);
        doc.add(new Paragraph("Student: " + nz(str(student.get("studentName")), "-"), body));
        doc.add(new Paragraph("Admission: " + nz(str(student.get("admissionNo")), "-"), body));
        Paragraph klass =
            new Paragraph("Class: " + nz(str(student.get("classSection")), "-"), body);
        klass.setSpacingAfter(14f);
        doc.add(klass);

        PdfPTable table = new PdfPTable(new float[] {2.2f, 2f, 1.4f});
        table.setWidthPercentage(100);
        table.addCell("Exam");
        table.addCell("Subject");
        table.addCell("Date");
        if (papers != null) {
          for (Map<String, Object> paper : papers) {
            table.addCell(nz(str(paper.get("name")), "-"));
            table.addCell(nz(str(paper.get("subject")), "-"));
            table.addCell(nz(str(paper.get("examDate")), "To be announced"));
          }
        }
        doc.add(table);
        Paragraph note =
            new Paragraph(
                "Bring this admit card to each paper listed above. Seating is as announced by the class teacher.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY));
        note.setSpacingBefore(18f);
        doc.add(note);
      }
      doc.close();
      return out.toByteArray();
    } catch (ExamException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new ExamException("PDF_ERROR", "Failed to render admit card PDF: " + ex.getMessage());
    }
  }

  private static String str(Object v) {
    return v == null ? null : String.valueOf(v);
  }

  private static String nz(String v, String fallback) {
    return v == null || v.isBlank() || "null".equalsIgnoreCase(v) ? fallback : v;
  }
}
