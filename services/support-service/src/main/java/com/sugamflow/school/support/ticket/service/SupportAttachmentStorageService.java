package com.sugamflow.school.support.ticket.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sugamflow.school.support.ticket.config.SupportTicketProperties;

@Service
public class SupportAttachmentStorageService {
  private final SupportTicketProperties properties;
  private final Set<String> allowedContentTypes;

  public SupportAttachmentStorageService(SupportTicketProperties properties) throws IOException {
    this.properties = properties;
    this.allowedContentTypes =
        Set.of(properties.getAllowedContentTypes().split(",")).stream()
            .map(String::trim)
            .map(String::toLowerCase)
            .collect(Collectors.toSet());
    Files.createDirectories(rootPath());
  }

  public void validateFile(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment file is empty");
    }
    if (file.getSize() > properties.getMaxAttachmentBytes()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Attachment exceeds maximum size of " + properties.getMaxAttachmentBytes() + " bytes");
    }
    String contentType = normalizeContentType(file.getContentType());
    if (!allowedContentTypes.contains(contentType)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Attachment type not allowed: " + contentType);
    }
    String name = file.getOriginalFilename();
    if (name == null || name.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Attachment file name is required");
    }
    if (name.contains("..") || name.contains("/") || name.contains("\\")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid attachment file name");
    }
  }

  public StoredAttachment store(String organizationId, String shopId, Long ticketId, MultipartFile file)
      throws IOException {
    validateFile(file);
    String safeName = sanitizeFileName(file.getOriginalFilename());
    Path ticketDir =
        rootPath().resolve(organizationId).resolve(shopId).resolve(String.valueOf(ticketId));
    Files.createDirectories(ticketDir);
    String storedName = UUID.randomUUID() + "_" + safeName;
    Path target = ticketDir.resolve(storedName);
    file.transferTo(target);
    return new StoredAttachment(
        storedName,
        target.toString(),
        normalizeContentType(file.getContentType()),
        file.getSize());
  }

  public Path resolvePath(String storagePath) {
    Path path = Paths.get(storagePath).normalize();
    Path root = rootPath().toAbsolutePath().normalize();
    if (!path.toAbsolutePath().normalize().startsWith(root)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid attachment path");
    }
    if (!Files.exists(path)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment file not found");
    }
    return path;
  }

  private Path rootPath() {
    return Paths.get(properties.getStorageDir());
  }

  private static String sanitizeFileName(String original) {
    return original.replaceAll("[^a-zA-Z0-9._-]", "_");
  }

  private static String normalizeContentType(String contentType) {
    return contentType == null ? "application/octet-stream" : contentType.trim().toLowerCase();
  }

  public record StoredAttachment(String fileName, String storagePath, String contentType, long fileSize) {}
}
