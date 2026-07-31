package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.settings.persistence.entity.DesignAssetEntity;
import com.sugamflow.school.settings.service.DesignAssetService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config/design-studio")
public class DesignAssetController {

  private final DesignAssetService service;

  public DesignAssetController(DesignAssetService service) {
    this.service = service;
  }

  /** Authenticated upload — JSON base64 body. */
  @PostMapping("/assets")
  public ApiResponse<Map<String, Object>> upload(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.upload(body));
  }

  /**
   * Public image bytes for login/shell branding (no JWT). Gateway must whitelist this path.
   */
  @GetMapping("/assets/{id}/content")
  public ResponseEntity<byte[]> content(@PathVariable("id") UUID id) {
    DesignAssetEntity meta = service.requirePublic(id);
    byte[] bytes = service.contentBytes(id);
    MediaType media =
        meta.getContentType() != null && !meta.getContentType().isBlank()
            ? MediaType.parseMediaType(meta.getContentType())
            : MediaType.APPLICATION_OCTET_STREAM;
    String fileName =
        meta.getFileName() != null && !meta.getFileName().isBlank()
            ? meta.getFileName()
            : "asset-" + id;
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
        .contentType(media)
        .body(bytes);
  }
}
