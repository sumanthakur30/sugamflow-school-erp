package com.sugamflow.school.academic.web;

import com.sugamflow.school.academic.service.TimetableService;
import com.sugamflow.school.academic.service.TimetableGenerationService;
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
@RequestMapping("/api/academic/timetable")
public class TimetableController {

  private final TimetableService service;
  private final TimetableGenerationService generation;

  public TimetableController(TimetableService service, TimetableGenerationService generation) {
    this.service = service;
    this.generation = generation;
  }

  // ---- periods --------------------------------------------------------------

  @GetMapping("/periods")
  public ApiResponse<List<Map<String, Object>>> listPeriods() {
    return ApiResponse.ok(service.listPeriods());
  }

  @PostMapping("/periods")
  public ApiResponse<Map<String, Object>> createPeriod(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.createPeriod(body));
  }

  @PutMapping("/periods/{id}")
  public ApiResponse<Map<String, Object>> updatePeriod(
      @PathVariable("id") UUID id, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.updatePeriod(id, body));
  }

  @DeleteMapping("/periods/{id}")
  public ApiResponse<Map<String, Object>> deletePeriod(@PathVariable("id") UUID id) {
    service.deletePeriod(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  // ---- slots ----------------------------------------------------------------

  @GetMapping("/slots")
  public ApiResponse<List<Map<String, Object>>> listSlots(
      @RequestParam(name = "sectionId", required = false) UUID sectionId,
      @RequestParam(name = "teacherUsername", required = false) String teacherUsername) {
    if (teacherUsername != null && !teacherUsername.isBlank()) {
      return ApiResponse.ok(service.listSlotsForTeacher(teacherUsername));
    }
    return ApiResponse.ok(service.listSlotsForSection(sectionId));
  }

  @GetMapping("/my-slots")
  public ApiResponse<List<Map<String, Object>>> mySlots() {
    return ApiResponse.ok(service.listSlotsForTeacher(null));
  }

  @GetMapping("/rooms")
  public ApiResponse<List<Map<String, Object>>> listRooms() {
    return ApiResponse.ok(generation.listRooms());
  }

  @PostMapping("/rooms")
  public ApiResponse<Map<String, Object>> saveRoom(@RequestBody Map<String, Object> body) {
    return ApiResponse.ok(generation.saveRoom(body));
  }

  @DeleteMapping("/rooms/{id}")
  public ApiResponse<Map<String, Object>> deleteRoom(@PathVariable("id") UUID id) {
    generation.deleteRoom(id);
    return ApiResponse.ok(Map.of("deleted", true));
  }

  @PostMapping("/sections/{sectionId}/generate")
  public ApiResponse<Map<String, Object>> generate(
      @PathVariable("sectionId") UUID sectionId) {
    return ApiResponse.ok(generation.generate(sectionId));
  }

  @PostMapping("/sections/{sectionId}/validate")
  public ApiResponse<Map<String, Object>> validate(
      @PathVariable("sectionId") UUID sectionId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(generation.validate(sectionId, body));
  }

  @PutMapping("/sections/{sectionId}")
  public ApiResponse<List<Map<String, Object>>> replaceSectionTimetable(
      @PathVariable("sectionId") UUID sectionId, @RequestBody Map<String, Object> body) {
    return ApiResponse.ok(service.replaceSectionTimetable(sectionId, body));
  }
}
