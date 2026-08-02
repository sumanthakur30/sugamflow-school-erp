package com.sugamflow.school.cms.storage;

import java.io.InputStream;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * S3 / MinIO object store. Public URLs use {@code cms.media.cdn-base-url} (CloudFront / CDN) when
 * set; otherwise virtual-hosted style S3 URL.
 */
@Component
@ConditionalOnProperty(name = "cms.media.s3.enabled", havingValue = "true")
public class S3MediaObjectStore implements MediaObjectStore {

  private final S3Client s3;
  private final String bucket;
  private final String keyPrefix;
  private final String cdnBaseUrl;
  private final String region;

  public S3MediaObjectStore(
      @Value("${cms.media.s3.bucket}") String bucket,
      @Value("${cms.media.s3.region:ap-south-1}") String region,
      @Value("${cms.media.s3.key-prefix:school-cms/}") String keyPrefix,
      @Value("${cms.media.s3.endpoint:}") String endpoint,
      @Value("${cms.media.s3.access-key:}") String accessKey,
      @Value("${cms.media.s3.secret-key:}") String secretKey,
      @Value("${cms.media.cdn-base-url:}") String cdnBaseUrl) {
    this.bucket = bucket;
    this.region = region;
    this.keyPrefix = keyPrefix == null ? "" : keyPrefix.replaceAll("^/+", "");
    this.cdnBaseUrl = cdnBaseUrl == null ? "" : cdnBaseUrl.trim().replaceAll("/$", "");

    S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
    if (endpoint != null && !endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint.trim()));
      builder.forcePathStyle(true);
    }
    if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
      builder.credentialsProvider(
          StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }
    this.s3 = builder.build();
  }

  @Override
  public StoredObject store(
      String organizationId,
      UUID id,
      String fileName,
      String contentType,
      InputStream data,
      long byteSize) {
    String key = keyPrefix + organizationId + "/" + id + "-" + fileName;
    try {
      PutObjectRequest.Builder put =
          PutObjectRequest.builder().bucket(bucket).key(key).contentLength(byteSize);
      if (contentType != null && !contentType.isBlank()) {
        put.contentType(contentType);
      }
      s3.putObject(put.build(), RequestBody.fromInputStream(data, byteSize));
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload to object storage");
    }
    return new StoredObject(key, publicUrl(key), byteSize);
  }

  @Override
  public Optional<Resource> loadLocal(String organizationId, UUID id) {
    return Optional.empty();
  }

  @Override
  public void delete(String organizationId, UUID id) {
    // Best-effort: list isn't needed; callers pass known URL key via filename convention.
    String prefix = keyPrefix + organizationId + "/" + id + "-";
    try {
      s3.listObjectsV2(b -> b.bucket(bucket).prefix(prefix))
          .contents()
          .forEach(
              o ->
                  s3.deleteObject(
                      DeleteObjectRequest.builder().bucket(bucket).key(o.key()).build()));
    } catch (Exception ignored) {
    }
  }

  @Override
  public boolean remoteEnabled() {
    return true;
  }

  private String publicUrl(String key) {
    if (!cdnBaseUrl.isBlank()) {
      return cdnBaseUrl + "/" + key;
    }
    return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
  }
}
