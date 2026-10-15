package com.sugamflow.school.compliance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.sugamflow.school.compliance.config.ComplianceProperties;
import com.sugamflow.school.compliance.web.ComplianceException;

@Service
public class DocumentStorageService {
  private static final Set<String> BLOCKED_EXTENSIONS =
      Set.of("exe", "bat", "cmd", "js", "ps1", "sh", "msi", "dll", "jar", "vbs", "com", "scr");
  private static final Map<String, Set<String>> EXTENSION_TYPES =
      Map.of(
          "pdf", Set.of("application/pdf"),
          "jpg", Set.of("image/jpeg"),
          "jpeg", Set.of("image/jpeg"),
          "png", Set.of("image/png"),
          "webp", Set.of("image/webp"),
          "doc", Set.of("application/msword"),
          "docx",
              Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
          "xls", Set.of("application/vnd.ms-excel"),
          "xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

  private final ComplianceProperties properties;
  private final Set<String> allowedContentTypes;

  public DocumentStorageService(ComplianceProperties properties) throws IOException {
    this.properties = properties;
    this.allowedContentTypes =
        Set.of(properties.getDocuments().getAllowedContentTypes().split(",")).stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    Files.createDirectories(rootPath());
  }

  public StoredFile store(String organizationId, Long documentId, MultipartFile file)
      throws IOException {
    validate(file);
    String safeName = sanitize(file.getOriginalFilename());
    Path dir = rootPath().resolve(organizationId).resolve(String.valueOf(documentId));
    Files.createDirectories(dir);
    String storedName = UUID.randomUUID() + "_" + safeName;
    Path target = dir.resolve(storedName);
    file.transferTo(target);
    String checksum = sha256(target);
    return new StoredFile(
        safeName,
        target.toAbsolutePath().normalize().toString(),
        normalizeContentType(file.getContentType()),
        file.getSize(),
        checksum);
  }

  public Path resolve(String storagePath) {
    if (storagePath == null || storagePath.isBlank()) {
      throw new ComplianceException("NO_FILE", "Document has no stored file", HttpStatus.NOT_FOUND);
    }
    Path path = Paths.get(storagePath).normalize();
    Path root = rootPath().toAbsolutePath().normalize();
    if (!path.toAbsolutePath().normalize().startsWith(root)) {
      throw new ComplianceException("FORBIDDEN", "Invalid document path", HttpStatus.FORBIDDEN);
    }
    if (!Files.exists(path)) {
      throw new ComplianceException("NOT_FOUND", "Document file not found", HttpStatus.NOT_FOUND);
    }
    return path;
  }

  public void deleteQuietly(String storagePath) {
    if (storagePath == null || storagePath.isBlank()) {
      return;
    }
    try {
      Path path = resolve(storagePath);
      Files.deleteIfExists(path);
    } catch (Exception ignored) {
      // best-effort cleanup
    }
  }

  private void validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ComplianceException("BAD_FILE", "File is empty", HttpStatus.BAD_REQUEST);
    }
    if (file.getSize() > properties.getDocuments().getMaxFileBytes()) {
      throw new ComplianceException(
          "FILE_TOO_LARGE",
          "File exceeds maximum size of " + properties.getDocuments().getMaxFileBytes() + " bytes",
          HttpStatus.BAD_REQUEST);
    }
    String name = file.getOriginalFilename();
    if (name == null || name.isBlank()) {
      throw new ComplianceException("BAD_FILE", "File name is required", HttpStatus.BAD_REQUEST);
    }
    if (name.contains("..") || name.contains("/") || name.contains("\\")) {
      throw new ComplianceException("BAD_FILE", "Invalid file name", HttpStatus.BAD_REQUEST);
    }
    String ext = extension(name);
    if (BLOCKED_EXTENSIONS.contains(ext) || !EXTENSION_TYPES.containsKey(ext)) {
      throw new ComplianceException(
          "BAD_CONTENT_TYPE", "File extension is not allowed", HttpStatus.BAD_REQUEST);
    }
    String contentType = normalizeContentType(file.getContentType());
    if (!allowedContentTypes.contains(contentType) || !EXTENSION_TYPES.get(ext).contains(contentType)) {
      throw new ComplianceException(
          "BAD_CONTENT_TYPE", "File type not allowed: " + contentType, HttpStatus.BAD_REQUEST);
    }
  }

  private static String extension(String name) {
    int dot = name.lastIndexOf('.');
    if (dot < 0 || dot == name.length() - 1) {
      return "";
    }
    return name.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static String sha256(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(Files.readAllBytes(path));
      return HexFormat.of().formatHex(digest.digest());
    } catch (Exception ex) {
      throw new IOException("Unable to checksum file", ex);
    }
  }

  private Path rootPath() {
    return Paths.get(properties.getDocuments().getStorageDir());
  }

  private static String sanitize(String original) {
    return original.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String normalizeContentType(String contentType) {
    return contentType == null ? "application/octet-stream" : contentType.trim().toLowerCase();
  }

  public record StoredFile(
      String fileName, String storagePath, String contentType, long fileSize, String checksum) {}
}
