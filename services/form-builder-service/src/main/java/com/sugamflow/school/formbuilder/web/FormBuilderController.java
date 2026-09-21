package com.sugamflow.school.formbuilder.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.formbuilder.service.FormDefinitionService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/forms")
public class FormBuilderController {
  public static final List<String> FIELD_TYPES = List.of(
      "TEXTBOX","TEXTAREA","DROPDOWN","CHECKBOX","RADIO","DATE","TIME","EMAIL","PHONE","NUMBER",
      "CURRENCY","ATTACHMENT","IMAGE","QR","BARCODE","SIGNATURE","LOCATION","COLOR_PICKER","MULTI_SELECT");

  private final FormDefinitionService service;
  public FormBuilderController(FormDefinitionService service) { this.service = service; }

  @GetMapping("/field-types")
  public ApiResponse<List<String>> fieldTypes() { return ApiResponse.ok(FIELD_TYPES); }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.list(TenantContext.require().organizationId()));
  }

  @GetMapping("/{formKey}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("formKey") String formKey) {
    return ApiResponse.ok(service.get(TenantContext.require().organizationId(), formKey));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> form) {
    String key = String.valueOf(form.getOrDefault("formKey", UUID.randomUUID()));
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), key, form));
  }

  @PutMapping("/{formKey}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("formKey") String formKey, @RequestBody Map<String, Object> form) {
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), formKey, form));
  }
}