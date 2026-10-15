package com.sugamflow.school.student.web;

import com.sugamflow.school.common.api.ApiResponse;
import com.sugamflow.school.student.privacy.SchoolPrivacyService;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.CompleteCommand;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.ConsentCommand;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.ConsentView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.NoticeView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.PurposeView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.RequestCommand;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.RequestView;
import com.sugamflow.school.student.privacy.SchoolPrivacyViews.StatusCommand;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/privacy")
public class SchoolPrivacyController {

  private final SchoolPrivacyService service;

  public SchoolPrivacyController(SchoolPrivacyService service) {
    this.service = service;
  }

  @GetMapping("/notice")
  public ApiResponse<NoticeView> notice() {
    return ApiResponse.ok(service.currentNotice());
  }

  @GetMapping("/purposes")
  public ApiResponse<List<PurposeView>> purposes() {
    return ApiResponse.ok(service.purposes());
  }

  @PostMapping("/consents")
  public ApiResponse<ConsentView> consent(@RequestBody ConsentCommand command) {
    return ApiResponse.ok(service.setConsent(command.purposeCode(), command.action()));
  }

  @PostMapping("/requests")
  public ApiResponse<RequestView> submit(@RequestBody RequestCommand command) {
    return ApiResponse.ok(service.submit(command));
  }

  @GetMapping("/requests")
  public ApiResponse<List<RequestView>> mine() {
    return ApiResponse.ok(service.mine());
  }

  @GetMapping("/requests/organization")
  public ApiResponse<List<RequestView>> organization() {
    return ApiResponse.ok(service.organization());
  }

  @PostMapping("/requests/{id}/complete")
  public ApiResponse<RequestView> complete(@PathVariable("id") UUID id, @RequestBody(required = false) CompleteCommand command) {
    return ApiResponse.ok(service.complete(id, command));
  }

  @PostMapping("/requests/{id}/status")
  public ApiResponse<RequestView> status(@PathVariable("id") UUID id, @RequestBody StatusCommand command) {
    String status = command == null ? "" : command.status();
    String note = command == null ? null : command.note();
    return ApiResponse.ok(service.updateStatus(id, status, note));
  }
}
