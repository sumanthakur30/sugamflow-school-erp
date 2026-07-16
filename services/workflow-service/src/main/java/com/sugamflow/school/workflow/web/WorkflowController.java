package com.sugamflow.school.workflow.web;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.workflow.service.WorkflowDefinitionService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/workflows")
public class WorkflowController {
  private final WorkflowDefinitionService service;
  public WorkflowController(WorkflowDefinitionService service){ this.service=service; }

  @GetMapping public ApiResponse<List<Map<String,Object>>> list(){
    return ApiResponse.ok(service.list(TenantContext.require().organizationId()));
  }
  @GetMapping("/{workflowKey}")
  public ApiResponse<Map<String,Object>> get(@PathVariable("workflowKey") String workflowKey){
    return ApiResponse.ok(service.get(TenantContext.require().organizationId(), workflowKey));
  }
  @PostMapping public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> body){
    String key=String.valueOf(body.getOrDefault("workflowKey", UUID.randomUUID()));
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), key, body));
  }
  @PutMapping("/{workflowKey}")
  public ApiResponse<Map<String,Object>> update(@PathVariable("workflowKey") String workflowKey, @RequestBody Map<String,Object> body){
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), workflowKey, body));
  }
}