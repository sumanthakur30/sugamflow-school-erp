package com.sugamflow.school.reportbuilder.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

/**
 * Template-driven PDF render. Layout/elements come from report template JSON. Canvas coordinates
 * scale onto the page named by {@code layout.paper}. CR80 is a landscape ID card (3.375in x
 * 2.125in). Every other paper stays A4 so existing certificates keep their page.
 */
@Service
public class ReportPdfRenderService {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^}]+)\\}\\}");
  /** ISO/IEC 7810 ID-1, landscape. 3.375in x 2.125in in PDF points. */
  static final float CR80_WIDTH = 3.375f * 72f;
  static final float CR80_HEIGHT = 2.125f * 72f;

  public Map<String, Object> render(Map<String, Object> template, Map<String, Object> data) {
    return renderMany(template, List.of(data != null ? data : Map.of()));
  }

  /** One page per data context. The template is rendered again for each record and is not modified. */
  public Map<String, Object> renderMany(Map<String, Object> template, List<Map<String, Object>> pages) {
    String templateKey = String.valueOf(template.getOrDefault("templateKey", "report"));
    List<Map<String, Object>> safe = pages == null || pages.isEmpty() ? List.of(Map.of()) : pages;
    byte[] pdf = toPdf(template, safe);
    String base64 = java.util.Base64.getEncoder().encodeToString(pdf);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("templateKey", templateKey);
    out.put("contentType", "application/pdf");
    out.put("fileName", templateKey + ".pdf");
    out.put("contentBase64", base64);
    out.put("byteLength", pdf.length);
    out.put("pageCount", safe.size());
    return out;
  }

  private byte[] toPdf(Map<String, Object> template, List<Map<String, Object>> pages) {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Rectangle page = pageSize(template);
    Document document = new Document(page, 0, 0, 0, 0);
    try {
      PdfWriter writer = PdfWriter.getInstance(document, baos);
      document.open();
      PdfContentByte cb = writer.getDirectContent();
      boolean first = true;
      for (Map<String, Object> data : pages) {
        if (!first) {
          document.newPage();
        }
        first = false;
        drawPage(cb, template, data != null ? data : Map.of(), page.getWidth(), page.getHeight());
      }
      document.close();
      return baos.toByteArray();
    } catch (DocumentException ex) {
      throw new IllegalStateException("PDF render failed: " + ex.getMessage(), ex);
    }
  }

  static Rectangle pageSize(Map<String, Object> template) {
    String paper = String.valueOf(layout(template).getOrDefault("paper", "A4"));
    if ("CR80".equalsIgnoreCase(paper)) {
      return new Rectangle(CR80_WIDTH, CR80_HEIGHT);
    }
    return PageSize.A4;
  }

  private void drawPage(
      PdfContentByte cb,
      Map<String, Object> template,
      Map<String, Object> data,
      float pageW,
      float pageH) {

      float layoutW = floatOr(layout(template).get("width"), 794f);
      float layoutH = floatOr(layout(template).get("height"), 1123f);
      float scaleX = pageW / Math.max(layoutW, 1f);
      float scaleY = pageH / Math.max(layoutH, 1f);
      float scale = Math.min(scaleX, scaleY);

      List<Map<String, Object>> elements = elements(template);
      elements.sort(
          Comparator.comparingInt((Map<String, Object> e) -> intOr(e.get("z"), 0))
              .thenComparingInt(e -> intOr(e.get("y"), 0)));

      for (Map<String, Object> el : elements) {
        String type = String.valueOf(el.getOrDefault("type", "text")).toLowerCase();
        float x = floatOr(el.get("x"), 40f) * scaleX;
        float yTop = floatOr(el.get("y"), 40f) * scaleY;
        float w = floatOr(el.get("width"), 200f) * scaleX;
        float h = floatOr(el.get("height"), 20f) * scaleY;
        // PDF origin is bottom-left; designer uses top-left.
        float yPdf = pageH - yTop;

        switch (type) {
          case "line" -> drawLine(cb, el, x, yPdf, w);
          case "box" -> drawBox(cb, el, x, yPdf - h, w, h);
          case "image" -> drawImage(cb, el, data, x, yPdf - h, w, h);
          case "qr" -> drawQr(cb, el, data, x, yPdf - h, w, h, scale);
          case "heading", "text", "field" ->
              drawText(cb, el, data, x, yPdf - h * 0.25f, w, h, scaleY);
          default -> drawText(cb, el, data, x, yPdf - h * 0.25f, w, h, scaleY);
        }
      }
  }

  private void drawLine(PdfContentByte cb, Map<String, Object> el, float x, float y, float w) {
    cb.saveState();
    cb.setColorStroke(colorOr(el.get("color"), Color.DARK_GRAY));
    cb.setLineWidth(Math.max(0.4f, floatOr(el.get("borderWidth"), 0.8f)));
    cb.moveTo(x, y);
    cb.lineTo(x + w, y);
    cb.stroke();
    cb.restoreState();
  }

  private void drawBox(PdfContentByte cb, Map<String, Object> el, float x, float y, float w, float h) {
    Color fill = colorOr(el.get("fillColor"), null);
    float radius = floatOr(el.get("borderRadius"), 0f);
    float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
    float border = floatOr(el.get("borderWidth"), fill == null ? 0.8f : 0f);
    cb.saveState();
    if (fill != null) {
      cb.setColorFill(fill);
      traceRoundRect(cb, x, y, w, h, r);
      cb.fill();
    }
    if (border > 0f) {
      cb.setLineWidth(border);
      cb.setColorStroke(colorOr(el.get("borderColor"), Color.GRAY));
      traceRoundRect(cb, x, y, w, h, r);
      cb.stroke();
    }
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

  private static void traceRoundRect(PdfContentByte cb, float x, float y, float w, float h, float r) {
    if (r > 0f) {
      cb.roundRectangle(x, y, w, h, r);
    } else {
      cb.rectangle(x, y, w, h);
    }
  }

  /**
   * Renders an image from a bound field. Accepts raw base64, {@code data:image/...;base64,...},
   * {@code {{student.photoDirectUrl}}}, or the element fallback image. Empty draws a placeholder.
   */
  private void drawImage(
      PdfContentByte cb,
      Map<String, Object> el,
      Map<String, Object> data,
      float x,
      float y,
      float w,
      float h) {
    byte[] bytes = decodeImageBytes(resolveImagePayload(el, data));
    if (bytes == null || bytes.length == 0) {
      if (el.get("fillColor") != null) {
        drawBox(cb, el, x, y, w, h);
      } else {
        drawBox(cb, x, y, w, h, true);
        strokeImageBorder(cb, el, x, y, w, h);
      }
      return;
    }
    try {
      Image image = Image.getInstance(bytes);
      String fit = String.valueOf(el.getOrDefault("objectFit", "cover")).toLowerCase();
      placeFittedImage(cb, image, x, y, w, h, fit, floatOr(el.get("borderRadius"), 0f));
      strokeImageBorder(cb, el, x, y, w, h);
    } catch (Exception ex) {
      drawBox(cb, x, y, w, h, true);
      strokeImageBorder(cb, el, x, y, w, h);
    }
  }

  private static String resolveImagePayload(Map<String, Object> el, Map<String, Object> data) {
    String raw =
        el.containsKey("text")
            ? String.valueOf(el.get("text"))
            : bindValue(data, String.valueOf(el.getOrDefault("bind", "student.photoDirectUrl")));
    if ((raw == null || raw.isBlank()) && el.get("bind") != null) {
      raw = "{{" + el.get("bind") + "}}";
    }
    String payload = substitute(raw, data);
    if (usableImage(payload)) {
      return payload;
    }
    String bind = String.valueOf(el.getOrDefault("bind", ""));
    boolean studentPhoto =
        bind.isBlank() || "null".equals(bind) || bind.contains("photo");
    if (!studentPhoto) {
      return payload;
    }
    String direct = bindValue(data, "student.photoDirectUrl");
    if (usableImage(direct)) {
      return direct;
    }
    String base64 = bindValue(data, "student.photoBase64");
    if (usableImage(base64)) {
      return base64;
    }
    Object fallback = el.get("fallbackSrc");
    if (fallback != null && usableImage(String.valueOf(fallback))) {
      return String.valueOf(fallback);
    }
    return payload;
  }

  private static boolean usableImage(String payload) {
    if (payload == null || payload.isBlank() || payload.contains("{{")) {
      return false;
    }
    String trimmed = payload.trim();
    if (trimmed.startsWith("/")) {
      return false;
    }
    return true;
  }

  private static void placeFittedImage(
      PdfContentByte cb,
      Image image,
      float x,
      float y,
      float w,
      float h,
      String fit,
      float radius)
      throws DocumentException {
    float iw = image.getWidth();
    float ih = image.getHeight();
    float scale = 1f;
    if (iw > 0f && ih > 0f) {
      float sx = w / iw;
      float sy = h / ih;
      scale = "contain".equals(fit) ? Math.min(sx, sy) : Math.max(sx, sy);
    }
    float sw = Math.max(1f, iw * scale);
    float sh = Math.max(1f, ih * scale);
    float dx = x + (w - sw) / 2f;
    float dy = y + (h - sh) / 2f;
    cb.saveState();
    float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
    if (r > 0f) {
      cb.roundRectangle(x, y, w, h, r);
    } else {
      cb.rectangle(x, y, w, h);
    }
    cb.clip();
    cb.newPath();
    image.scaleAbsolute(sw, sh);
    image.setAbsolutePosition(dx, dy);
    cb.addImage(image);
    cb.restoreState();
  }

  private static void strokeImageBorder(
      PdfContentByte cb, Map<String, Object> el, float x, float y, float w, float h) {
    float width = floatOr(el.get("borderWidth"), 0f);
    if (width <= 0f) {
      return;
    }
    cb.saveState();
    cb.setLineWidth(width);
    cb.setColorStroke(colorOr(el.get("borderColor"), Color.DARK_GRAY));
    float radius = floatOr(el.get("borderRadius"), 0f);
    float r = Math.max(0f, Math.min(radius, Math.min(w, h) / 2f));
    if (r > 0f) {
      cb.roundRectangle(x, y, w, h, r);
    } else {
      cb.rectangle(x, y, w, h);
    }
    cb.stroke();
    cb.restoreState();
  }

  private static Color colorOr(Object value, Color fallback) {
    if (value == null) {
      return fallback;
    }
    String raw = String.valueOf(value).trim();
    if (!raw.startsWith("#")) {
      return fallback;
    }
    String hex = raw.substring(1);
    if (hex.length() == 3) {
      hex =
          ""
              + hex.charAt(0)
              + hex.charAt(0)
              + hex.charAt(1)
              + hex.charAt(1)
              + hex.charAt(2)
              + hex.charAt(2);
    }
    if (hex.length() != 6) {
      return fallback;
    }
    try {
      return new Color(Integer.parseInt(hex, 16));
    } catch (NumberFormatException ex) {
      return fallback;
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
      float h,
      float scale) {
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
      float quiet = Math.max(0f, floatOr(el.get("quietZone"), 0f) * scale);
      float pad = Math.min(quiet, Math.min(w, h) / 4f);
      cb.saveState();
      cb.setColorFill(Color.WHITE);
      cb.rectangle(x, y, w, h);
      cb.fill();
      cb.restoreState();
      float qx = x + pad;
      float qy = y + pad;
      float qw = Math.max(8f, w - pad * 2f);
      float qh = Math.max(8f, h - pad * 2f);
      int size = Math.max(64, Math.round(Math.min(qw, qh)));
      Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
      hints.put(EncodeHintType.MARGIN, 1);
      hints.put(EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M);
      BitMatrix matrix =
          new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
      BufferedImage buffered = MatrixToImageWriter.toBufferedImage(matrix);
      ByteArrayOutputStream png = new ByteArrayOutputStream();
      ImageIO.write(buffered, "png", png);
      Image image = Image.getInstance(png.toByteArray());
      image.setAbsolutePosition(qx, qy);
      image.scaleAbsolute(qw, qh);
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
    Color ink = colorOr(el.get("color"), Color.BLACK);
    Font font =
        FontFactory.getFont(
            bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA,
            size,
            bold ? Font.BOLD : Font.NORMAL,
            ink);
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
    // Wrap only when the box is tall enough for two lines. Shorter boxes stay one line
    // so a label cannot drop below the card edge.
    if (h > size * 2.4f && w > 40f) {
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
    return formatBound(path, String.valueOf(cur));
  }

  /** Issued/expiry print as "16 Jul 2026". Date of birth prints as DD-MM-YYYY. */
  private static String formatBound(String path, String value) {
    if (value == null || value.isBlank()) {
      return value == null ? "" : value;
    }
    String key = path.toLowerCase(Locale.ROOT);
    boolean dob = key.endsWith("dateofbirth") || key.endsWith(".dob");
    boolean cardDate = key.endsWith("issuedat") || key.endsWith("expiresat") || key.endsWith("validuntil");
    if (!dob && !cardDate) {
      return value;
    }
    try {
      if (value.length() >= 20 && value.contains("T")) {
        Instant instant = Instant.parse(value);
        DateTimeFormatter fmt =
            dob
                ? DateTimeFormatter.ofPattern("dd-MM-yyyy").withZone(ZoneId.of("Asia/Kolkata"))
                : DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
                    .withZone(ZoneId.of("Asia/Kolkata"));
        return fmt.format(instant);
      }
      if (value.length() >= 10 && value.charAt(4) == '-' && value.charAt(7) == '-') {
        LocalDate date = LocalDate.parse(value.substring(0, 10));
        return dob
            ? date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            : date.format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH));
      }
    } catch (RuntimeException ignored) {
      return value;
    }
    return value;
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
