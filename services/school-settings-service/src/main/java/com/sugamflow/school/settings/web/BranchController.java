package com.sugamflow.school.settings.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.settings.service.BranchRegistryService;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/config/branches")
public class BranchController {

  private final BranchRegistryService service;

  public BranchController(BranchRegistryService service) {
    this.service = service;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    try {
      return ApiResponse.ok(service.bootstrap());
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
    }
  }

  @GetMapping
  public ApiResponse<List<Map<String, Object>>> list() {
    return ApiResponse.ok(service.list(TenantContext.require().organizationId()));
  }

  @GetMapping("/{branchKey}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("branchKey") String branchKey) {
    Map<String, Object> branch =
        service.get(TenantContext.require().organizationId(), branchKey);
    if (branch == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found: " + branchKey);
    }
    return ApiResponse.ok(branch);
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    try {
      return ApiResponse.ok(service.create(TenantContext.require().organizationId(), body));
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
    } catch (IllegalStateException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

  @PutMapping("/{branchKey}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("branchKey") String branchKey, @RequestBody Map<String, Object> body) {
    try {
      return ApiResponse.ok(
          service.update(TenantContext.require().organizationId(), branchKey, body));
    } catch (SecurityException ex) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, ex.getMessage());
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }
}
