package com.sugamflow.school.academic.web;

import com.sugamflow.school.academic.service.AcademicStructureService;
import com.sugamflow.school.academic.service.TeacherScopeService;
import com.sugamflow.school.common.api.ApiResponse;
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
@RequestMapping("/api/academic")
public class AcademicController {

  private final AcademicStructureService service;
  private final TeacherScopeService teacherScope;

  public AcademicController(AcademicStructureService service, TeacherScopeService teacherScope) {
    this.service = service;
    this.teacherScope = teacherScope;
  }

  @GetMapping("/bootstrap")
  public ApiResponse<Map<String, Object>> bootstrap() {
    return ApiResponse.ok(service.bootstrap());
  }

  // ---- classes --------------------------------------------------------------

  @GetMapping("/classes")
  public ApiResponse<List<Map<String, Object>>> listClasses() {
    return ApiResponse.ok(service.listClasses());
  }

  @PostMapping("/classes")
  public ApiResponse<Map<String, Object>> createClass(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createClass(body));
  }

  @PutMapping("/classes/{id}")
  public ApiResponse<Map<String, Object>> updateClass(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateClass(id, body));
  }

  @DeleteMapping("/classes/{id}")
  public ApiResponse<Map<String, Object>> deleteClass(@PathVariable("id") UUID id) {
    service.deleteClass(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  // ---- sections -------------------------------------------------------------

  @GetMapping("/sections")
  public ApiResponse<List<Map<String, Object>>> listSections(
      @RequestParam(name = "classId", required = false) UUID classId) {
    return ApiResponse.ok(service.listSections(classId));
  }

  @PostMapping("/sections")
  public ApiResponse<Map<String, Object>> createSection(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createSection(body));
  }

  @PutMapping("/sections/{id}")
  public ApiResponse<Map<String, Object>> updateSection(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateSection(id, body));
  }

  @DeleteMapping("/sections/{id}")
  public ApiResponse<Map<String, Object>> deleteSection(@PathVariable("id") UUID id) {
    service.deleteSection(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  // ---- subjects -------------------------------------------------------------

  @GetMapping("/subjects")
  public ApiResponse<List<Map<String, Object>>> listSubjects() {
    return ApiResponse.ok(service.listSubjects());
  }

  @PostMapping("/subjects")
  public ApiResponse<Map<String, Object>> createSubject(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createSubject(body));
  }

  @PutMapping("/subjects/{id}")
  public ApiResponse<Map<String, Object>> updateSubject(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateSubject(id, body));
  }

  @DeleteMapping("/subjects/{id}")
  public ApiResponse<Map<String, Object>> deleteSubject(@PathVariable("id") UUID id) {
    service.deleteSubject(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  // ---- teaching assignments -------------------------------------------------

  @GetMapping("/assignments")
  public ApiResponse<List<Map<String, Object>>> listAssignments(
      @RequestParam(name = "sectionId", required = false) UUID sectionId) {
    return ApiResponse.ok(service.listAssignments(sectionId));
  }

  @PostMapping("/assignments")
  public ApiResponse<Map<String, Object>> createAssignment(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createAssignment(body));
  }

  @PutMapping("/assignments/{id}")
  public ApiResponse<Map<String, Object>> updateAssignment(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updateAssignment(id, body));
  }

  @DeleteMapping("/assignments/{id}")
  public ApiResponse<Map<String, Object>> deleteAssignment(@PathVariable("id") UUID id) {
    service.deleteAssignment(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  // ---- teacher scope (consumed by RBAC) -------------------------------------

  @GetMapping("/teacher-scope")
  public ApiResponse<Map<String, Object>> teacherScope(
      @RequestParam(name = "username", required = false) String username) {
    return ApiResponse.ok(teacherScope.resolve(username));
  }
}
