package com.sugamflow.school.cms.service;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Shared upload checks for website images and school documents. */
public final class UploadRules {

  private static final long IMAGE_LIMIT = 8L * 1024L * 1024L;
  private static final long SVG_LIMIT = 1024L * 1024L;
  private static final long DOCUMENT_LIMIT = 20L * 1024L * 1024L;
  private static final Set<String> IMAGES = Set.of("jpg", "jpeg", "png", "webp", "svg");
  private static final Set<String> DOCUMENTS = Set.of("pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png", "webp");

  private UploadRules() {}

  public static Prepared prepareImage(MultipartFile file) {
    return prepare(file, IMAGES, true);
  }

  public static Prepared prepareDocument(MultipartFile file) {
    return prepare(file, DOCUMENTS, false);
  }

  public static Prepared preparePdf(MultipartFile file) {
    Prepared prepared = prepare(file, Set.of("pdf"), false);
    byte[] bytes = prepared.bytes();
    if (bytes.length < 5
        || bytes[0] != '%'
        || bytes[1] != 'P'
        || bytes[2] != 'D'
        || bytes[3] != 'F'
        || bytes[4] != '-') {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files can be uploaded");
    }
    String provided = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
    if (!provided.isBlank()
        && !provided.equals("application/pdf")
        && !provided.equals("application/octet-stream")
        && !provided.equals("application/x-pdf")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF files can be uploaded");
    }
    return prepared;
  }

  private static Prepared prepare(MultipartFile file, Set<String> allowed, boolean image) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required");
    }
    String original = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
    String ext = extension(original);
    if (!allowed.contains(ext)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File type is not allowed");
    }
    long size = file.getSize();
    long limit = "svg".equals(ext) ? SVG_LIMIT : image ? IMAGE_LIMIT : DOCUMENT_LIMIT;
    if (!image && IMAGES.contains(ext) && !"svg".equals(ext)) {
      limit = IMAGE_LIMIT;
    }
    if (size > limit) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is too large");
    }
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read upload");
    }
    if ("svg".equals(ext)) {
      bytes = sanitizeSvg(bytes);
    }
    String safeName = original.replaceAll("[^a-zA-Z0-9._\\-]+", "_");
    return new Prepared(safeName, contentType(ext, file.getContentType()), bytes);
  }

  static byte[] sanitizeSvg(byte[] raw) {
    String text = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
    String lower = text.toLowerCase(Locale.ROOT);
    if (lower.contains("<script")
        || lower.contains("javascript:")
        || lower.contains("<foreignobject")
        || lower.contains("<iframe")
        || lower.contains("onload=")
        || lower.contains("onerror=")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SVG content is not allowed");
    }
    return text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) return "";
    return name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String contentType(String ext, String provided) {
    return switch (ext) {
      case "jpg", "jpeg" -> "image/jpeg";
      case "png" -> "image/png";
      case "webp" -> "image/webp";
      case "svg" -> "image/svg+xml";
      case "pdf" -> "application/pdf";
      case "doc" -> "application/msword";
      case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
      case "xls" -> "application/vnd.ms-excel";
      case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
      default -> provided == null || provided.isBlank() ? "application/octet-stream" : provided;
    };
  }

  public record Prepared(String fileName, String contentType, byte[] bytes) {}
}
