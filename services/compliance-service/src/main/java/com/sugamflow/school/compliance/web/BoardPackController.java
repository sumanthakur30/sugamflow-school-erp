package com.sugamflow.school.compliance.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.compliance.dto.BoardDefinitionResponse;
import com.sugamflow.school.compliance.dto.ComplianceTemplateRequest;
import com.sugamflow.school.compliance.dto.ComplianceTemplateResponse;
import com.sugamflow.school.compliance.dto.ComplianceTemplateUpdateRequest;
import com.sugamflow.school.compliance.dto.FieldMapResponse;
import com.sugamflow.school.compliance.dto.FieldMapUpdateRequest;
import com.sugamflow.school.compliance.dto.ValidationRuleResponse;
import com.sugamflow.school.compliance.dto.ValidationRuleUpdateRequest;
import com.sugamflow.school.compliance.service.BoardPackService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/compliance")
public class BoardPackController {
  private final BoardPackService boardPackService;

  public BoardPackController(BoardPackService boardPackService) {
    this.boardPackService = boardPackService;
  }

  @GetMapping("/boards")
  public ApiResponse<List<BoardDefinitionResponse>> boards() {
    return ApiResponse.ok(boardPackService.listBoards());
  }

  @GetMapping("/packs")
  public ApiResponse<List<ComplianceTemplateResponse>> publishedPacks() {
    return ApiResponse.ok(boardPackService.listPublishedPacks());
  }

  @GetMapping("/platform/templates")
  public ApiResponse<List<ComplianceTemplateResponse>> platformTemplates() {
    return ApiResponse.ok(boardPackService.listAllTemplates());
  }

  @PostMapping("/platform/templates")
  public ApiResponse<ComplianceTemplateResponse> createTemplate(
      @Valid @RequestBody ComplianceTemplateRequest request) {
    return ApiResponse.ok(boardPackService.createTemplate(request));
  }

  @PutMapping("/platform/templates/{id}")
  public ApiResponse<ComplianceTemplateResponse> updateTemplate(
      @PathVariable("id") Long id, @Valid @RequestBody ComplianceTemplateUpdateRequest request) {
    return ApiResponse.ok(boardPackService.updateTemplate(id, request));
  }

  @GetMapping("/platform/field-maps")
  public ApiResponse<List<FieldMapResponse>> fieldMaps(@RequestParam String boardCode) {
    return ApiResponse.ok(boardPackService.listFieldMaps(boardCode));
  }

  @PutMapping("/platform/field-maps/{id}")
  public ApiResponse<FieldMapResponse> updateFieldMap(
      @PathVariable("id") Long id, @Valid @RequestBody FieldMapUpdateRequest request) {
    return ApiResponse.ok(boardPackService.updateFieldMap(id, request));
  }

  @GetMapping("/platform/rules")
  public ApiResponse<List<ValidationRuleResponse>> rules(@RequestParam String boardCode) {
    return ApiResponse.ok(boardPackService.listRules(boardCode));
  }

  @PutMapping("/platform/rules/{id}")
  public ApiResponse<ValidationRuleResponse> updateRule(
      @PathVariable("id") Long id, @Valid @RequestBody ValidationRuleUpdateRequest request) {
    return ApiResponse.ok(boardPackService.updateRule(id, request));
  }
}
