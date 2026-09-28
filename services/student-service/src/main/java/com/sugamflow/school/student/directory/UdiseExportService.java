package com.sugamflow.school.student.directory;

import com.sugamflow.school.common.security.AccessScope;
import com.sugamflow.school.common.tenant.TenantContext;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.student.integration.ConfigEngineClient;
import com.sugamflow.school.student.persistence.entity.StudentRecordEntity;
import com.sugamflow.school.student.persistence.entity.UdiseExportSettingEntity;
import com.sugamflow.school.student.persistence.repo.StudentRecordRepository;
import com.sugamflow.school.student.persistence.repo.UdiseExportSettingRepository;
import com.sugamflow.school.student.service.RelationshipAccessService;
import com.sugamflow.school.student.web.StudentException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UdiseExportService {

  private static final int PAGE_SIZE = 500;
  private static final int MAX_PAGES = 40;

  private final StudentRecordRepository students;
  private final UdiseExportSettingRepository settings;
  private final ConfigEngineClient engines;
  private final RelationshipAccessService relationshipAccess;

  public UdiseExportService(
      StudentRecordRepository students,
      UdiseExportSettingRepository settings,
      ConfigEngineClient engines,
      RelationshipAccessService relationshipAccess) {
    this.students = students;
    this.settings = settings;
    this.engines = engines;
    this.relationshipAccess = relationshipAccess;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> setting() {
    TenantScope scope = requireScope();
    List<String> selected = selectedKeys(scope.organizationId());
    boolean saved = settings.findById(scope.organizationId()).isPresent();
    List<Map<String, Object>> columns = new ArrayList<>();
    for (UdiseColumns.Column column : UdiseColumns.all()) {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("key", column.key());
      row.put("label", column.label());
      row.put("selected", selected.contains(column.key()));
      columns.add(row);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("columns", columns);
    out.put("usingDefaults", !saved);
    return out;
  }

  @Transactional
  public Map<String, Object> save(Map<String, Object> body) {
    TenantScope scope = requireScope();
    List<String> keys = UdiseColumns.keepKnownInOrder(stringList(body.get("keys")));
    if (keys.isEmpty()) {
      throw new StudentException("BAD_REQUEST", "Select at least one UDISE+ column");
    }
    UdiseExportSettingEntity entity =
        settings.findById(scope.organizationId()).orElseGet(UdiseExportSettingEntity::new);
    entity.setOrganizationId(scope.organizationId());
    entity.setColumnKeys(keys);
    entity.setUpdatedAt(Instant.now());
    settings.save(entity);
    return setting();
  }

  @Transactional(readOnly = true)
  public byte[] exportCsv(Map<String, String> params) {
    TenantScope scope = requireScope();
    List<String> keys = selectedKeys(scope.organizationId());
    StudentDirectoryService.DirectoryQuery dq =
        StudentDirectoryService.DirectoryQuery.from(params, scope);
    AccessScope access = relationshipAccess.resolve(scope);

    StringBuilder sb = new StringBuilder();
    sb.append('\uFEFF');
    sb.append(UdiseColumns.header(keys)).append('\n');

    for (int pageNo = 0; pageNo < MAX_PAGES; pageNo++) {
      Page<StudentRecordEntity> page =
          students.searchDirectory(
              scope.organizationId(),
              nullToEmpty(dq.branch()),
              blank(dq.branch()),
              nullToEmpty(dq.session()),
              blank(dq.session()),
              nullToEmpty(dq.status()),
              blank(dq.status()),
              nullToEmpty(dq.q()),
              blank(dq.q()),
              nullToEmpty(dq.classSection()),
              blank(dq.classSection()),
              nullToEmpty(dq.gender()),
              blank(dq.gender()),
              nullToEmpty(dq.category()),
              blank(dq.category()),
              nullToEmpty(dq.house()),
              blank(dq.house()),
              !dq.transportOnly(),
              !dq.hostelOnly(),
              !dq.scholarshipOnly(),
              Boolean.FALSE,
              PageRequest.of(pageNo, PAGE_SIZE));
      for (StudentRecordEntity student : page.getContent()) {
        if (!allows(access, student)) {
          continue;
        }
        Map<String, Object> answers = student.getAnswers() != null ? student.getAnswers() : Map.of();
        for (int i = 0; i < keys.size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          sb.append(UdiseColumns.csv(UdiseColumns.cell(student.getAdmissionNo(), answers, keys.get(i))));
        }
        sb.append('\n');
      }
      if (!page.hasNext()) {
        break;
      }
    }
    return sb.toString().getBytes(StandardCharsets.UTF_8);
  }

  private List<String> selectedKeys(String organizationId) {
    return settings
        .findById(organizationId)
        .map(UdiseExportSettingEntity::getColumnKeys)
        .map(UdiseColumns::keepKnownInOrder)
        .filter(keys -> !keys.isEmpty())
        .orElseGet(UdiseColumns::defaultKeys);
  }

  private TenantScope requireScope() {
    TenantScope scope = TenantContext.require();
    if (!engines.isFeatureEnabled(scope, StudentDirectoryService.FEATURE_STUDENT_MASTER)) {
      throw new StudentException("FEATURE_OFF", "FEATURE_STUDENT_MASTER is off for this plan");
    }
    return scope;
  }

  private static boolean allows(AccessScope access, StudentRecordEntity student) {
    if (!access.restricted()) {
      return true;
    }
    Map<String, Object> dto = new LinkedHashMap<>();
    dto.put("id", student.getId() != null ? student.getId().toString() : "");
    dto.put("admissionNo", student.getAdmissionNo());
    Map<String, Object> answers = student.getAnswers() != null ? student.getAnswers() : Map.of();
    dto.put("classSection", text(answers.get("classSection")));
    dto.put("answers", answers);
    return access.allowsStudentDto(dto);
  }

  private static List<String> stringList(Object raw) {
    if (!(raw instanceof List<?> list)) {
      return List.of();
    }
    List<String> out = new ArrayList<>();
    for (Object item : list) {
      if (item != null) {
        out.add(String.valueOf(item));
      }
    }
    return out;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }

  private static String text(Object value) {
    return value == null ? "" : String.valueOf(value).trim();
  }
}
