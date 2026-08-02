package com.sugamflow.school.cms.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(name = "cms.media.s3.enabled", havingValue = "false", matchIfMissing = true)
public class LocalMediaObjectStore implements MediaObjectStore {

  private final Path storageRoot;
  private final String publicBaseUrl;

  public LocalMediaObjectStore(
      @Value("${cms.media.storage-dir:${java.io.tmpdir}/school-cms-media}") String storageDir,
      @Value("${cms.media.public-base-url:}") String publicBaseUrl) {
    this.storageRoot = Path.of(storageDir).toAbsolutePath().normalize();
    this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.trim().replaceAll("/$", "");
  }

  @Override
  public StoredObject store(
      String organizationId,
      UUID id,
      String fileName,
      String contentType,
      InputStream data,
      long byteSize) {
    Path orgDir = storageRoot.resolve(organizationId);
    try {
      Files.createDirectories(orgDir);
      Path target = orgDir.resolve(id + "-" + fileName);
      Files.copy(data, target);
    } catch (IOException ex) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file");
    }
    String url =
        (publicBaseUrl.isBlank() ? "" : publicBaseUrl)
            + "/api/cms/public/media/"
            + id
            + "?organizationId="
            + organizationId;
    return new StoredObject(organizationId + "/" + id + "-" + fileName, url, byteSize);
  }

  @Override
  public Optional<Resource> loadLocal(String organizationId, UUID id) {
    Path orgDir = storageRoot.resolve(organizationId);
    try {
      if (!Files.isDirectory(orgDir)) {
        return Optional.empty();
      }
      try (var stream = Files.list(orgDir)) {
        return stream
            .filter(p -> p.getFileName().toString().startsWith(id + "-"))
            .findFirst()
            .map(FileSystemResource::new);
      }
    } catch (IOException ex) {
      return Optional.empty();
    }
  }

  @Override
  public void delete(String organizationId, UUID id) {
    Path orgDir = storageRoot.resolve(organizationId);
    try {
      if (!Files.isDirectory(orgDir)) {
        return;
      }
      try (var stream = Files.list(orgDir)) {
        stream
            .filter(p -> p.getFileName().toString().startsWith(id + "-"))
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException ignored) {
                  }
                });
      }
    } catch (IOException ignored) {
    }
  }

  @Override
  public boolean remoteEnabled() {
    return false;
  }
}
