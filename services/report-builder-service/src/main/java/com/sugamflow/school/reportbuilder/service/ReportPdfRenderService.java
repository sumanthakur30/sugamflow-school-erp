package com.sugamflow.school.reportbuilder.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Template-driven PDF render. Layout/elements come from report template JSON — no school-specific
 * hardcoding. Uses canvas coordinates (layout width/height) scaled onto A4.
 */
@Service
public class ReportPdfRenderService {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)\\}\\}");
  private static final float PAGE_W = PageSize.A4.getWidth();
  private static final float PAGE_H = PageSize.A4.getHeight();

  public Map<String, Object> render(Map<String, Object> template, Map<String, Object> data) {
    String templateKey = String.valueOf(template.getOrDefault("templateKey", "report"));
    byte[] pdf = toPdf(template, data != null ? data : Map.of());
    String base64 = java.util.Base64.getEncoder().encodeToString(pdf);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("templateKey", templateKey);
    out.put("contentType", "application/pdf");
    out.put("fileName", templateKey + ".pdf");
    out.put("contentBase64", base64);
    out.put("byteLength", pdf.length);
    return out;
  }

  private byte[] toPdf(Map<String, Object> template, Map<String, Object> data) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, 0, 0, 0, 0);
    try {
      PdfWriter writer = PdfWriter.getInstance(document, baos);
      document.open();
      PdfContentByte cb = writer.getDirectContent();

      float layoutW = floatOr(layout(template).get("width"), 794f);
      float layoutH = floatOr(layout(template).get("height"), 1123f);
      float scaleX = PAGE_W / Math.max(layoutW, 1f);
      float scaleY = PAGE_H / Math.max(layoutH, 1f);

      List<Map<String, Object>> elements = elements(template);
      elements.sort(Comparator.comparingInt(e -> intOr(e.get("y"), 0)));

      for (Map<String, Object> el : elements) {
        String type = String.valueOf(el.getOrDefault("type", "text")).toLowerCase();
        float x = floatOr(el.get("x"), 40f) * scaleX;
        float yTop = floatOr(el.get("y"), 40f) * scaleY;
        float w = floatOr(el.get("width"), 200f) * scaleX;
        float h = floatOr(el.get("height"), 20f) * scaleY;
        // PDF origin is bottom-left; designer uses top-left.
        float yPdf = PAGE_H - yTop;

        switch (type) {
          case "line" -> drawLine(cb, x, yPdf, w);
          case "box" -> drawBox(cb, x, yPdf - h, w, h, false);
          case "image" -> drawImage(cb, el, data, x, yPdf - h, w, h);
          case "qr" -> drawQr(cb, el, data, x, yPdf - h, w, h);
          case "heading", "text", "field" ->
              drawText(cb, el, data, x, yPdf - h * 0.25f, w, h, scaleY);
          default -> drawText(cb, el, data, x, yPdf - h * 0.25f, w, h, scaleY);
        }
      }
      document.close();
      return baos.toByteArray();
    } catch (DocumentException ex) {
      throw new IllegalStateException("PDF render failed: " + ex.getMessage(), ex);
    }
  }

  private void drawLine(PdfContentByte cb, float x, float y, float w) {
    cb.saveState();
    cb.setColorStroke(Color.DARK_GRAY);
    cb.setLineWidth(1f);
    cb.moveTo(x, y);
    cb.lineTo(x + w, y);
    cb.stroke();
    cb.restoreState();
  }

  private void drawBox(PdfContentByte cb, float x, float y, float w, float h, boolean image) {
    cb.saveState();
    cb.setColorStroke(Color.GRAY);
    cb.setLineWidth(0.8f);
    cb.rectangle(x, y, w, h);
    cb.stroke();
    if (image) {
      Font font = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);
      ColumnText.showTextAligned(
          cb,
          com.lowagie.text.Element.ALIGN_CENTER,
          new Phrase("[Image]", font),
          x + w / 2f,
          y + h / 2f - 3f,
          0);
    }
    cb.restoreState();
  }

  /**
   * Renders an image from a bound data field. Accepts raw base64, {@code data:image/...;base64,...},
   * or empty (draws a placeholder box). Template text/bind typically {@code {{student.photoBase64}}}.
   */
  private void drawImage(
      PdfContentByte cb,
      Map<String, Object> el,
      Map<String, Object> data,
      float x,
      float y,
      float w,
      float h) {
    String raw =
        el.containsKey("text")
            ? String.valueOf(el.get("text"))
            : bindValue(data, String.valueOf(el.getOrDefault("bind", "student.photoBase64")));
    if ((raw == null || raw.isBlank()) && el.get("bind") != null) {
      raw = "{{" + el.get("bind") + "}}";
    }
    String payload = substitute(raw, data);
    byte[] bytes = decodeImageBytes(payload);
    if (bytes == null || bytes.length == 0) {
      drawBox(cb, x, y, w, h, true);
      return;
    }
    try {
      Image image = Image.getInstance(bytes);
      image.setAbsolutePosition(x, y);
      image.scaleAbsolute(w, h);
      cb.addImage(image);
    } catch (Exception ex) {
      drawBox(cb, x, y, w, h, true);
    }
  }

  private static byte[] decodeImageBytes(String payload) {
    if (payload == null || payload.isBlank() || payload.contains("{{")) {
      return null;
    }
    String b64 = payload.trim();
    int comma = b64.indexOf(',');
    if (b64.regionMatches(true, 0, "data:", 0, 5) && comma > 0) {
      b64 = b64.substring(comma + 1);
    }
    try {
      return java.util.Base64.getDecoder().decode(b64);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }

  private void drawQr(
      PdfContentByte cb,
      Map<String, Object> el,
      Map<String, Object> data,
      float x,
      float y,
      float w,
      float h) {
    String raw =
        el.containsKey("text")
            ? String.valueOf(el.get("text"))
            : bindValue(data, String.valueOf(el.getOrDefault("bind", "context.verifyUrl")));
    if ((raw == null || raw.isBlank()) && el.get("bind") != null) {
      raw = "{{" + el.get("bind") + "}}";
    }
    String payload = substitute(raw, data);
    if (payload == null || payload.isBlank() || payload.contains("{{")) {
      drawBox(cb, x, y, w, h, true);
      return;
    }
    try {
      int size = Math.max(64, Math.round(Math.min(w, h)));
      Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
      hints.put(EncodeHintType.MARGIN, 1);
      BitMatrix matrix =
          new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
      BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(buffered, "png", png);
      Image image = Image.getInstance(png.toByteArray());
      image.setAbsolutePosition(x, y);
      image.scaleAbsolute(w, h);
      cb.addImage(image);
    } catch (Exception ex) {
      drawBox(cb, x, y, w, h, true);
    }
  }

  private void drawText(
      PdfContentByte cb,
      Map<String, Object> el,
      Map<String, Object> data,
      float x,
      float baseline,
      float w,
      float h,
      float scaleY) {
    String raw =
        el.containsKey("text")
            ? String.valueOf(el.get("text"))
            : bindValue(data, String.valueOf(el.getOrDefault("bind", "")));
    if ((raw == null || raw.isBlank()) && el.get("bind") != null) {
      raw = "{{" + el.get("bind") + "}}";
    }
    String text = substitute(raw, data);
    if (text == null || text.isBlank()) {
      return;
    }
    float size = floatOr(el.get("fontSize"), 11f) * Math.max(scaleY, 0.7f);
    boolean bold =
        Boolean.TRUE.equals(el.get("bold"))
            || "heading".equalsIgnoreCase(String.valueOf(el.get("type")));
    Font font =
        bold
            ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, size)
            : FontFactory.getFont(FontFactory.HELVETICA, size);
    String align = String.valueOf(el.getOrDefault("align", "left")).toLowerCase();
    int alignment =
        switch (align) {
          case "center" -> com.lowagie.text.Element.ALIGN_CENTER;
          case "right" -> com.lowagie.text.Element.ALIGN_RIGHT;
          default -> com.lowagie.text.Element.ALIGN_LEFT;
        };
    float tx =
        switch (alignment) {
          case com.lowagie.text.Element.ALIGN_CENTER -> x + w / 2f;
          case com.lowagie.text.Element.ALIGN_RIGHT -> x + w;
          default -> x;
        };
    // Wrap long text into a column when height allows more than one line.
    if (h > size * 1.8f && w > 40f) {
      ColumnText ct = new ColumnText(cb);
      ct.setSimpleColumn(new Phrase(text, font), x, baseline - h, x + w, baseline + size, size + 2f, alignment);
      try {
        ct.go();
      } catch (DocumentException ex) {
        ColumnText.showTextAligned(cb, alignment, new Phrase(text, font), tx, baseline, 0);
      }
    } else {
      ColumnText.showTextAligned(cb, alignment, new Phrase(text, font), tx, baseline, 0);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> layout(Map<String, Object> template) {
    Object raw = template.get("layout");
    if (raw instanceof Map<?, ?> m) {
      return (Map<String, Object>) m;
    }
    return Map.of("width", 794, "height", 1123);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> elements(Map<String, Object> template) {
    Object raw = template.get("elements");
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

  private static String substitute(String template, Map<String, Object> data) {
    if (template == null) {
      return "";
    }
    Matcher matcher = PLACEHOLDER.matcher(template);
    StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      String path = matcher.group(1).trim();
      String value = bindValue(data, path);
      matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : ""));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private static String bindValue(Map<String, Object> data, String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    Object cur = data;
    for (String part : path.split("\\.")) {
      if (!(cur instanceof Map<?, ?> map)) {
        return "";
      }
      cur = map.get(part);
      if (cur == null) {
        return "";
      }
    }
    return String.valueOf(cur);
  }

  private static int intOr(Object v, int d) {
    if (v instanceof Number n) {
      return n.intValue();
    }
    try {
      return v != null ? Integer.parseInt(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }

  private static float floatOr(Object v, float d) {
    if (v instanceof Number n) {
      return n.floatValue();
    }
    try {
      return v != null ? Float.parseFloat(String.valueOf(v)) : d;
    } catch (NumberFormatException ex) {
      return d;
    }
  }
}
