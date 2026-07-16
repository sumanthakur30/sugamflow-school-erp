package com.sugamflow.school.ruleengine.web;
import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.ruleengine.service.BusinessRuleService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/rules")
public class RuleEngineController {
  private final BusinessRuleService service;
  public RuleEngineController(BusinessRuleService service){ this.service=service; }

  @GetMapping public ApiResponse<List<Map<String,Object>>> list(){
    return ApiResponse.ok(service.list(TenantContext.require().organizationId()));
  }
  @GetMapping("/{ruleId}")
  public ApiResponse<Map<String,Object>> get(@PathVariable("ruleId") String ruleId){
    return ApiResponse.ok(service.get(TenantContext.require().organizationId(), ruleId));
  }
  @PostMapping public ApiResponse<Map<String,Object>> create(@RequestBody Map<String,Object> rule){
    String id=String.valueOf(rule.getOrDefault("id", UUID.randomUUID()));
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), id, rule));
  }
  @PutMapping("/{ruleId}")
  public ApiResponse<Map<String,Object>> update(@PathVariable("ruleId") String ruleId, @RequestBody Map<String,Object> rule){
    return ApiResponse.ok(service.save(TenantContext.require().organizationId(), ruleId, rule));
  }
  @PostMapping("/evaluate")
  public ApiResponse<Map<String,Object>> evaluate(@RequestBody Map<String,Object> context){
    return ApiResponse.ok(service.evaluate(TenantContext.require().organizationId(), context));
  }
}