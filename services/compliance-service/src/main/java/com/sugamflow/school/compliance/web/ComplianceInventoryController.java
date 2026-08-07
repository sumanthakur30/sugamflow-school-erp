package com.sugamflow.school.compliance.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.compliance.dto.ComplianceDocumentRequest;
import com.sugamflow.school.compliance.dto.ComplianceDocumentResponse;
import com.sugamflow.school.compliance.dto.DocumentVaultSummaryResponse;
import com.sugamflow.school.compliance.dto.InfrastructureAssetRequest;
import com.sugamflow.school.compliance.dto.InfrastructureAssetResponse;
import com.sugamflow.school.compliance.dto.InfrastructureSummaryResponse;
import com.sugamflow.school.compliance.service.DocumentVaultService;
import com.sugamflow.school.compliance.service.InfrastructureService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compliance")
public class ComplianceInventoryController {
  private final InfrastructureService infrastructureService;
  private final DocumentVaultService documentVaultService;

  public ComplianceInventoryController(
      InfrastructureService infrastructureService, DocumentVaultService documentVaultService) {
    this.infrastructureService = infrastructureService;
    this.documentVaultService = documentVaultService;
  }

  @GetMapping("/infrastructure")
  public ApiResponse<List<InfrastructureAssetResponse>> listInfrastructure(
      @RequestParam(required = false) String category) {
    return ApiResponse.ok(infrastructureService.list(category));
  }

  @GetMapping("/infrastructure/summary")
  public ApiResponse<InfrastructureSummaryResponse> infrastructureSummary() {
    return ApiResponse.ok(infrastructureService.summary());
  }

  @PostMapping("/infrastructure")
  public ApiResponse<InfrastructureAssetResponse> createInfrastructure(
      @Valid @RequestBody InfrastructureAssetRequest request) {
    return ApiResponse.ok(infrastructureService.create(request));
  }

  @PutMapping("/infrastructure/{id}")
  public ApiResponse<InfrastructureAssetResponse> updateInfrastructure(
      @PathVariable Long id, @Valid @RequestBody InfrastructureAssetRequest request) {
    return ApiResponse.ok(infrastructureService.update(id, request));
  }

  @DeleteMapping("/infrastructure/{id}")
  public ApiResponse<Void> deleteInfrastructure(@PathVariable Long id) {
    infrastructureService.delete(id);
    return ApiResponse.message("deleted");
  }

  @GetMapping("/documents")
  public ApiResponse<List<ComplianceDocumentResponse>> listDocuments(
      @RequestParam(required = false) String docType,
      @RequestParam(required = false) String status) {
    return ApiResponse.ok(documentVaultService.list(docType, status));
  }

  @GetMapping("/documents/summary")
  public ApiResponse<DocumentVaultSummaryResponse> documentsSummary() {
    return ApiResponse.ok(documentVaultService.summary());
  }

  @GetMapping("/documents/expiring")
  public ApiResponse<List<ComplianceDocumentResponse>> expiringDocuments(
      @RequestParam(required = false) Integer withinDays) {
    return ApiResponse.ok(documentVaultService.expiring(withinDays));
  }

  @PostMapping("/documents")
  public ApiResponse<ComplianceDocumentResponse> createDocument(
      @Valid @RequestBody ComplianceDocumentRequest request) {
    return ApiResponse.ok(documentVaultService.create(request));
  }

  @PutMapping("/documents/{id}")
  public ApiResponse<ComplianceDocumentResponse> updateDocument(
      @PathVariable Long id, @Valid @RequestBody ComplianceDocumentRequest request) {
    return ApiResponse.ok(documentVaultService.update(id, request));
  }

  @DeleteMapping("/documents/{id}")
  public ApiResponse<Void> deleteDocument(@PathVariable Long id) {
    documentVaultService.delete(id);
    return ApiResponse.message("deleted");
  }

  @PostMapping(value = "/documents/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ApiResponse<ComplianceDocumentResponse> uploadDocumentFile(
      @PathVariable Long id, @RequestPart("file") MultipartFile file) throws IOException {
    return ApiResponse.ok(documentVaultService.upload(id, file));
  }

  @GetMapping("/documents/{id}/file")
  public ResponseEntity<org.springframework.core.io.Resource> downloadDocumentFile(
      @PathVariable Long id) throws IOException {
    DocumentVaultService.FilePayload payload = documentVaultService.download(id);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + payload.fileName().replace("\"", "") + "\"")
        .contentType(MediaType.parseMediaType(payload.contentType()))
        .body(payload.resource());
  }
}
