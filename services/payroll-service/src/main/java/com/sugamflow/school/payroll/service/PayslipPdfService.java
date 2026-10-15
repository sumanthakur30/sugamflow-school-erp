package com.sugamflow.school.payroll.service;

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
import com.sugamflow.school.payroll.web.PayrollException;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Issued payslip from the amounts already stored on an approved payroll run. */
@Service
public class PayslipPdfService {

  private static final Color HEADER_BG = new Color(15, 61, 46);

  public byte[] render(Map<String, Object> answers) {
    if (answers == null || answers.isEmpty()) {
      throw new PayrollException("NOT_FOUND", "No payslip amounts to print");
    }
    Document doc = new Document(PageSize.A4, 42, 42, 48, 42);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      PdfWriter.getInstance(doc, out);
      doc.open();
      Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, HEADER_BG);
      Font body = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
      Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

      Paragraph title = new Paragraph("Payslip", titleFont);
      title.setAlignment(Element.ALIGN_CENTER);
      title.setSpacingAfter(8);
      doc.add(title);
      Paragraph who =
          new Paragraph(
              nz(answers.get("employeeName"), "Staff")
                  + " · "
                  + nz(answers.get("employeeId"), "")
                  + " · "
                  + nz(answers.get("month"), "")
                  + " "
                  + nz(answers.get("year"), ""),
              body);
      who.setAlignment(Element.ALIGN_CENTER);
      who.setSpacingAfter(16);
      doc.add(who);

      PdfPTable table = new PdfPTable(new float[] {2f, 1f});
      table.setWidthPercentage(100);
      header(table, "Component", head);
      header(table, "Amount", head);
      row(table, "Basic pay", answers.get("basicPay"), body);
      row(table, "Allowances", answers.get("allowances"), body);
      row(table, "Gross pay", answers.get("grossPay"), body);
      row(table, "Deductions", answers.get("deductions"), body);
      row(table, "Net pay", answers.get("netPay"), body);
      if (answers.get("approvedLeaveDays") != null) {
        row(table, "Approved leave days", answers.get("approvedLeaveDays"), body);
      }
      doc.add(table);

      Paragraph issued =
          new Paragraph("Issued " + nz(answers.get("payslipIssuedAt"), ""), body);
      issued.setSpacingBefore(16);
      doc.add(issued);
      doc.close();
      return out.toByteArray();
    } catch (PayrollException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new PayrollException("PDF", "Could not render the payslip");
    }
  }

  private static void header(PdfPTable table, String text, Font font) {
    PdfPCell cell = new PdfPCell(new Phrase(text, font));
    cell.setBackgroundColor(HEADER_BG);
    cell.setPadding(6);
    table.addCell(cell);
  }

  private static void row(PdfPTable table, String label, Object amount, Font font) {
    PdfPCell name = new PdfPCell(new Phrase(label, font));
    name.setPadding(6);
    table.addCell(name);
    PdfPCell value = new PdfPCell(new Phrase(nz(amount, "0"), font));
    value.setPadding(6);
    value.setHorizontalAlignment(Element.ALIGN_RIGHT);
    table.addCell(value);
  }

  private static String nz(Object value, String fallback) {
    if (value == null) {
      return fallback;
    }
    String s = String.valueOf(value).trim();
    return s.isEmpty() || "null".equalsIgnoreCase(s) ? fallback : s;
  }
}
