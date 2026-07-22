package com.sugamflow.school.fee.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.common.api.PageResult;
import com.sugamflow.school.fee.service.IncomeService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fee/incomes")
public class IncomeController {

  private final IncomeService service;

  public IncomeController(IncomeService service) {
    this.service = service;
  }

  @GetMapping
  public ApiResponse<PageResult<Map<String, Object>>> list(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return ApiResponse.ok(service.list(page, size));
  }

  @GetMapping("/sources")
  public ApiResponse<List<String>> sources() {
    return ApiResponse.ok(service.sources());
  }

  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> get(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.get(id));
  }

  @PostMapping
  public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.create(body));
  }

  @PutMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.update(id, body));
  }

  @DeleteMapping("/{id}")
  public ApiResponse<Map<String, Object>> voidIncome(@PathVariable("id") UUID id) {
    return ApiResponse.ok(service.voidIncome(id));
  }
}
