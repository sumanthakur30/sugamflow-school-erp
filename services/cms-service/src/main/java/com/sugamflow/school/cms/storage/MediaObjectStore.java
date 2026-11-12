package com.sugamflow.school.cms.storage;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;

/** Pluggable media binary store (local disk or S3/MinIO). */
public interface MediaObjectStore {

  record StoredObject(String key, String publicUrl, long byteSize) {}

  StoredObject store(
      String organizationId,
      UUID id,
      String fileName,
      String contentType,
      InputStream data,
      long byteSize);

  /** Local-only load; empty when object lives on remote CDN/S3. */
  Optional<Resource> loadLocal(String organizationId, UUID id);

  void delete(String organizationId, UUID id);

  /**
   * Overwrite the object already stored for this id so the public URL stays the same.
   * When nothing is stored yet, this behaves like {@link #store}.
   */
  StoredObject replace(
      String organizationId,
      UUID id,
      String fileName,
      String contentType,
      InputStream data,
      long byteSize);

  boolean remoteEnabled();
}
