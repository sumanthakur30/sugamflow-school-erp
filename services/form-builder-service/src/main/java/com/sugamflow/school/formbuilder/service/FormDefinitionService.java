package com.sugamflow.school.formbuilder.service;

import com.sugamflow.school.formbuilder.persistence.entity.FormDefinitionEntity;
import com.sugamflow.school.formbuilder.persistence.repo.FormDefinitionRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormDefinitionService {
  private final FormDefinitionRepository repo;
  public FormDefinitionService(FormDefinitionRepository repo) { this.repo = repo; }

  @Transactional
  public List<Map<String, Object>> list(String org) {
    ensureDefaults();
    Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
    for (FormDefinitionEntity e : repo.findVisible(org)) {
      if (e.getOrganizationId() == null || org.equals(e.getOrganizationId())) {
        byKey.put(e.getFormKey(), e.getPayload());
      }
    }
    // tenant overrides win
    for (FormDefinitionEntity e : repo.findVisible(org)) {
      if (org.equals(e.getOrganizationId())) {
        byKey.put(e.getFormKey(), e.getPayload());
      }
    }
    Map<String, Map<String, Object>> platformByKey = new LinkedHashMap<>();
    for (FormDefinitionEntity e : repo.findVisible(org)) {
      if (e.getOrganizationId() == null) {
        platformByKey.put(e.getFormKey(), e.getPayload());
      }
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (Map.Entry<String, Map<String, Object>> entry : byKey.entrySet()) {
      out.add(effectiveForm(entry.getValue(), platformByKey.get(entry.getKey())));
    }
    return out;
  }

  @Transactional
  public Map<String, Object> get(String org, String formKey) {
    ensureDefaults();
    Map<String, Object> platform =
        repo.findByOrganizationIdIsNullAndFormKey(formKey)
            .map(FormDefinitionEntity::getPayload)
            .orElse(null);
    return repo.findByOrganizationIdAndFormKey(org, formKey)
        .map(FormDefinitionEntity::getPayload)
        .map(tenant -> effectiveForm(tenant, platform))
        .orElse(platform);
  }

  @Transactional
  public Map<String, Object> save(String org, String formKey, Map<String, Object> form) {
    form.put("formKey", formKey);
    form.put("organizationId", org);
    FormDefinitionEntity entity =
        repo.findByOrganizationIdAndFormKey(org, formKey).orElseGet(FormDefinitionEntity::new);
    entity.setOrganizationId(org);
    entity.setFormKey(formKey);
    entity.setPayload(form);
    entity.setUpdatedAt(Instant.now());
    return repo.save(entity).getPayload();
  }

  @Transactional
  public void ensureDefaults() {
    for (String key : List.of(
        "student_master","parent_master","employee_master","admission_form","fee_collection","attendance_mark","exam_marks","visitor_form",
        "certificate","library","transport","hostel",
        "library_issue","hostel_allocation","transport_route","payroll_run")) {
      if (repo.findByOrganizationIdIsNullAndFormKey(key).isPresent()) {
        if ("admission_form".equals(key)) {
          ensureAdmissionFormEnriched();
        }
        if ("fee_collection".equals(key)) {
          ensureFeeFormEnriched();
        }
        if ("attendance_mark".equals(key)) {
          ensureAttendanceFormEnriched();
        }
        if ("exam_marks".equals(key)) {
          ensureExamFormEnriched();
        }
        if ("student_master".equals(key)) {
          ensureStudentMasterFormEnriched();
        }
        if ("parent_master".equals(key)) {
          ensureParentMasterFormEnriched();
        }
        if ("library_issue".equals(key)) {
          ensureLibraryIssueFormEnriched();
        }
        if ("hostel_allocation".equals(key)) {
          ensureHostelAllocationFormEnriched();
        }
        if ("transport_route".equals(key)) {
          ensureTransportRouteFormEnriched();
        }
        if ("payroll_run".equals(key)) {
          ensurePayrollRunFormEnriched();
        }
        if ("employee_master".equals(key)) {
          ensureEmployeeMasterFormEnriched();
        }
        continue;
      }
      FormDefinitionEntity e = new FormDefinitionEntity();
      e.setOrganizationId(null);
      e.setFormKey(key);
      e.setPayload(sampleForm(key));
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    }
  }

  /** Phase 5 — ensure platform admission form has fields used by admission rules. */
  private void ensureAdmissionFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("admission_form").ifPresent(e -> {
      Map<String, Object> payload = e.getPayload();
      String raw = String.valueOf(payload);
      boolean needsRebuild =
          !raw.contains("age")
              || !raw.contains("documentsComplete")
              || !raw.contains("guardianFullName");
      if (needsRebuild) {
        e.setPayload(admissionForm());
        e.setUpdatedAt(Instant.now());
        repo.save(e);
        return;
      }
      boolean changed = upgradeFieldType(payload, "classApplied", "DROPDOWN");
      changed |=
          ensureSectionFields(
              payload,
              "government_ids",
              "Government IDs",
              List.of(
                  field("aadhaar", "Aadhaar Number", "TEXTBOX", false, true, false),
                  field("penNumber", "PEN Number", "TEXTBOX", false, true, true),
                  field("apaarId", "APAAR ID", "TEXTBOX", false, true, true),
                  field("samagraId", "Samagra ID", "TEXTBOX", false, true, false),
                  field("schoolStudentId", "School Student ID", "TEXTBOX", false, true, true)));
      changed |=
          ensureSectionFields(
              payload,
              "applicant",
              "Applicant",
              List.of(
                  field("classGrade", "Grade", "TEXTBOX", true, true, false),
                  field("sectionLetter", "Section", "TEXTBOX", true, true, false),
                  field("rollNo", "Roll Number", "TEXTBOX", false, true, true)));
      changed |= ensureFieldVisibilityFlags(payload);
      if (changed) {
        e.setPayload(payload);
        e.setUpdatedAt(Instant.now());
        repo.save(e);
      }
    });
  }

  /** Mutates form payload in place; returns true when a field type was changed. */
  @SuppressWarnings("unchecked")
  private boolean upgradeFieldType(Map<String, Object> form, String fieldKey, String type) {
    Object sectionsObj = form.get("sections");
    if (!(sectionsObj instanceof List<?> sections)) {
      return false;
    }
    boolean changed = false;
    for (Object sectionObj : sections) {
      if (!(sectionObj instanceof Map<?, ?> sectionRaw)) {
        continue;
      }
      Map<String, Object> section = (Map<String, Object>) sectionRaw;
      Object fieldsObj = section.get("fields");
      if (!(fieldsObj instanceof List<?> fields)) {
        continue;
      }
      for (Object fieldObj : fields) {
        if (!(fieldObj instanceof Map<?, ?> fieldRaw)) {
          continue;
        }
        Map<String, Object> field = (Map<String, Object>) fieldRaw;
        if (!fieldKey.equals(String.valueOf(field.get("key")))) {
          continue;
        }
        if (!type.equalsIgnoreCase(String.valueOf(field.get("type")))) {
          field.put("type", type);
          changed = true;
        }
      }
    }
    return changed;
  }

  private void ensureFeeFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("fee_collection").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("amount")
          && payload.contains("pendingDays")
          && payload.contains("email")
          && payload.contains("feeMonth")) {
        return;
      }
      e.setPayload(feeCollectionForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureAttendanceFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("attendance_mark").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("classSection") && payload.contains("attendancePercent") && payload.contains("email")) {
        return;
      }
      e.setPayload(attendanceMarkForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureExamFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("exam_marks").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("marksObtained") && payload.contains("maxMarks") && payload.contains("examName")) {
        return;
      }
      e.setPayload(examMarksForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureStudentMasterFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("student_master").ifPresent(e -> {
      Map<String, Object> payload = e.getPayload();
      String raw = String.valueOf(payload);
      if (!raw.contains("admissionNo") || !raw.contains("classApplied")) {
        e.setPayload(studentMasterForm());
        e.setUpdatedAt(Instant.now());
        repo.save(e);
        return;
      }
      boolean changed =
          ensureSectionFields(
              payload,
              "main",
              "Student",
              List.of(
                  field("fullName", "Full Name", "TEXTBOX", true, true, true),
                  field("admissionNo", "Admission No", "TEXTBOX", false, true, true),
                  field("rollNo", "Roll Number", "TEXTBOX", false, true, true),
                  field("classApplied", "Class", "DROPDOWN", true, true, true),
                  field("classGrade", "Grade", "TEXTBOX", true, true, false),
                  field("sectionLetter", "Section", "TEXTBOX", true, true, false),
                  field("age", "Age (years)", "NUMBER", false),
                  field("mobile", "Mobile", "PHONE", true),
                  field("email", "Email", "EMAIL", false)));
      changed |=
          ensureSectionFields(
              payload,
              "government_ids",
              "Government IDs",
              List.of(
                  field("aadhaar", "Aadhaar Number", "TEXTBOX", false, true, false),
                  field("penNumber", "PEN Number", "TEXTBOX", false, true, true),
                  field("apaarId", "APAAR ID", "TEXTBOX", false, true, true),
                  field("samagraId", "Samagra ID", "TEXTBOX", false, true, false),
                  field("schoolStudentId", "School Student ID", "TEXTBOX", false, true, true)));
      changed |= ensureFieldVisibilityFlags(payload);
      if (changed) {
        e.setPayload(payload);
        e.setUpdatedAt(Instant.now());
        repo.save(e);
      }
    });
  }

  private void ensureParentMasterFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("parent_master").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("relation") && payload.contains("isPrimary")) {
        return;
      }
      e.setPayload(parentMasterForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureLibraryIssueFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("library_issue").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("bookTitle") && payload.contains("dueDate") && payload.contains("email")) {
        return;
      }
      e.setPayload(libraryIssueForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureHostelAllocationFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("hostel_allocation").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("bedNo") && payload.contains("pendingFee") && payload.contains("email")) {
        return;
      }
      e.setPayload(hostelAllocationForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureTransportRouteFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("transport_route").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("distanceKm") && payload.contains("vehicleNo") && payload.contains("email")) {
        return;
      }
      e.setPayload(transportRouteForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensurePayrollRunFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("payroll_run").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("netPay") && payload.contains("basicPay") && payload.contains("email")) {
        return;
      }
      e.setPayload(payrollRunForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private void ensureEmployeeMasterFormEnriched() {
    repo.findByOrganizationIdIsNullAndFormKey("employee_master").ifPresent(e -> {
      String payload = String.valueOf(e.getPayload());
      if (payload.contains("employeeNo") && payload.contains("department") && payload.contains("joiningDate")) {
        return;
      }
      e.setPayload(employeeMasterForm());
      e.setUpdatedAt(Instant.now());
      repo.save(e);
    });
  }

  private Map<String, Object> effectiveForm(Map<String, Object> tenant, Map<String, Object> platform) {
    if (platform == null || hasFormFields(tenant)) {
      return tenant;
    }
    Map<String, Object> merged = new LinkedHashMap<>(platform);
    merged.putAll(tenant);
    merged.put("sections", platform.get("sections"));
    if (platform.get("validationRules") != null) {
      merged.putIfAbsent("validationRules", platform.get("validationRules"));
    }
    if (platform.get("conditionalVisibility") != null) {
      merged.putIfAbsent("conditionalVisibility", platform.get("conditionalVisibility"));
    }
    return merged;
  }

  private boolean hasFormFields(Map<String, Object> form) {
    if (form == null) {
      return false;
    }
    Object sectionsObj = form.get("sections");
    if (!(sectionsObj instanceof List<?> sections) || sections.isEmpty()) {
      return false;
    }
    for (Object sectionObj : sections) {
      if (sectionObj instanceof Map<?, ?> section) {
        Object fields = section.get("fields");
        if (fields instanceof List<?> list && !list.isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private Map<String, Object> sampleForm(String key) {
    if ("admission_form".equals(key)) {
      return admissionForm();
    }
    if ("fee_collection".equals(key)) {
      return feeCollectionForm();
    }
    if ("attendance_mark".equals(key)) {
      return attendanceMarkForm();
    }
    if ("exam_marks".equals(key)) {
      return examMarksForm();
    }
    if ("student_master".equals(key)) {
      return studentMasterForm();
    }
    if ("parent_master".equals(key)) {
      return parentMasterForm();
    }
    if ("library_issue".equals(key)) {
      return libraryIssueForm();
    }
    if ("hostel_allocation".equals(key)) {
      return hostelAllocationForm();
    }
    if ("transport_route".equals(key)) {
      return transportRouteForm();
    }
    if ("payroll_run".equals(key)) {
      return payrollRunForm();
    }
    if ("employee_master".equals(key)) {
      return employeeMasterForm();
    }
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", key);
    form.put("title", key.replace('_', ' '));
    form.put("sections", List.of(Map.of(
        "id", "main", "title", "Main", "repeatable", false,
        "fields", List.of(
            field("fullName","Full Name","TEXTBOX",true),
            field("mobile","Mobile","PHONE",true),
            field("email","Email","EMAIL",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> admissionForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "admission_form");
    form.put("title", "Admission Application");
    form.put(
        "sections",
        List.of(
            Map.of(
                "id",
                "applicant",
                "title",
                "Applicant",
                "repeatable",
                false,
                "fields",
                List.of(
                    field("fullName", "Full Name", "TEXTBOX", true),
                    field("age", "Age (years)", "NUMBER", true),
                    field("mobile", "Mobile", "PHONE", true),
                    field("email", "Email", "EMAIL", false),
                    field("classApplied", "Class Applied", "DROPDOWN", true),
                    field("rollNo", "Roll Number", "TEXTBOX", false),
                    field("documentsComplete", "Documents Complete", "CHECKBOX", true))),
            Map.of(
                "id",
                "government_ids",
                "title",
                "Government IDs",
                "repeatable",
                false,
                "fields",
                List.of(
                    field("aadhaar", "Aadhaar Number", "TEXTBOX", false),
                    field("penNumber", "PEN Number", "TEXTBOX", false),
                    field("apaarId", "APAAR ID", "TEXTBOX", false),
                    field("samagraId", "Samagra ID", "TEXTBOX", false),
                    field("schoolStudentId", "School Student ID", "TEXTBOX", false))),
            Map.of(
                "id",
                "guardian",
                "title",
                "Parent / Guardian",
                "repeatable",
                false,
                "fields",
                List.of(
                    field("guardianFullName", "Guardian Name", "TEXTBOX", false),
                    field("guardianRelation", "Relation", "TEXTBOX", false),
                    field("guardianMobile", "Guardian Mobile", "PHONE", false),
                    field("guardianEmail", "Guardian Email", "EMAIL", false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> parentMasterForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "parent_master");
    form.put("title", "Parent / Guardian");
    form.put(
        "sections",
        List.of(
            Map.of(
                "id",
                "main",
                "title",
                "Guardian",
                "repeatable",
                false,
                "fields",
                List.of(
                    field("fullName", "Full Name", "TEXTBOX", true),
                    field("relation", "Relation", "TEXTBOX", true),
                    field("mobile", "Mobile", "PHONE", true),
                    field("email", "Email", "EMAIL", false),
                    field("userId", "Linked Login Username", "TEXTBOX", false),
                    field("isPrimary", "Primary Contact", "CHECKBOX", false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> studentMasterForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "student_master");
    form.put("title", "Student Master");
    form.put(
        "sections",
        List.of(
            Map.of(
                "id",
                "main",
                "title",
                "Student",
                "repeatable",
                false,
                "fields",
                List.of(
                    field("fullName", "Full Name", "TEXTBOX", true),
                    field("admissionNo", "Admission No", "TEXTBOX", false),
                    field("rollNo", "Roll Number", "TEXTBOX", false),
                    field("classApplied", "Class", "DROPDOWN", true),
                    field("age", "Age (years)", "NUMBER", false),
                    field("mobile", "Mobile", "PHONE", true),
                    field("email", "Email", "EMAIL", false))),
            Map.of(
                "id",
                "government_ids",
                "title",
                "Government IDs",
                "repeatable",
                false,
                "fields",
                List.of(
                    field("aadhaar", "Aadhaar Number", "TEXTBOX", false),
                    field("penNumber", "PEN Number", "TEXTBOX", false),
                    field("apaarId", "APAAR ID", "TEXTBOX", false),
                    field("samagraId", "Samagra ID", "TEXTBOX", false),
                    field("schoolStudentId", "School Student ID", "TEXTBOX", false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> feeCollectionForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "fee_collection");
    form.put("title", "Fee Collection");
    form.put("sections", List.of(Map.of(
        "id", "payment", "title", "Payment", "repeatable", false,
        "fields", List.of(
            field("studentName","Student Name","TEXTBOX",true),
            field("admissionNo","Admission No","TEXTBOX",true),
            field("feeHead","Fee Head","TEXTBOX",true),
            field("feeMonth","Payment for Month","TEXTBOX",true),
            field("amount","Amount","NUMBER",true),
            field("pendingDays","Pending Days","NUMBER",false),
            field("paymentMode","Payment Mode","TEXTBOX",true),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> attendanceMarkForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "attendance_mark");
    form.put("title", "Attendance Mark");
    form.put("sections", List.of(Map.of(
        "id", "attendance", "title", "Attendance", "repeatable", false,
        "fields", List.of(
            field("classSection","Class / Section","TEXTBOX",true),
            field("attendanceDate","Attendance Date","TEXTBOX",true),
            field("studentName","Student Name","TEXTBOX",true),
            field("admissionNo","Admission No","TEXTBOX",true),
            field("status","Status","TEXTBOX",true),
            field("attendancePercent","Attendance %","NUMBER",true),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> examMarksForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "exam_marks");
    form.put("title", "Exam Marks");
    form.put("sections", List.of(Map.of(
        "id", "marks", "title", "Marks", "repeatable", false,
        "fields", List.of(
            field("studentName","Student Name","TEXTBOX",true),
            field("admissionNo","Admission No","TEXTBOX",true),
            field("classSection","Class / Section","TEXTBOX",true),
            field("subject","Subject","TEXTBOX",true),
            field("examName","Exam Name","TEXTBOX",true),
            field("maxMarks","Max Marks","NUMBER",true),
            field("marksObtained","Marks Obtained","NUMBER",true),
            field("grade","Grade","TEXTBOX",false),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> libraryIssueForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "library_issue");
    form.put("title", "Library Book Issue");
    form.put("sections", List.of(Map.of(
        "id", "issue", "title", "Issue", "repeatable", false,
        "fields", List.of(
            field("studentName","Student Name","TEXTBOX",true),
            field("admissionNo","Admission No","TEXTBOX",true),
            field("bookTitle","Book Title","TEXTBOX",true),
            field("bookId","Book ID","TEXTBOX",true),
            field("issueDate","Issue Date","TEXTBOX",true),
            field("dueDate","Due Date","TEXTBOX",true),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> hostelAllocationForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "hostel_allocation");
    form.put("title", "Hostel Allocation");
    form.put("sections", List.of(Map.of(
        "id", "allocation", "title", "Allocation", "repeatable", false,
        "fields", List.of(
            field("studentName","Student Name","TEXTBOX",true),
            field("admissionNo","Admission No","TEXTBOX",true),
            field("roomNo","Room No","TEXTBOX",true),
            field("bedNo","Bed No","NUMBER",true),
            field("hostelBlock","Hostel Block","TEXTBOX",true),
            field("startDate","Start Date","TEXTBOX",true),
            field("pendingFee","Pending Fee","NUMBER",false),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> transportRouteForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "transport_route");
    form.put("title", "Transport Route Assignment");
    form.put("sections", List.of(Map.of(
        "id", "route", "title", "Route", "repeatable", false,
        "fields", List.of(
            field("studentName","Student Name","TEXTBOX",true),
            field("admissionNo","Admission No","TEXTBOX",true),
            field("routeName","Route Name","TEXTBOX",true),
            field("stopName","Stop Name","TEXTBOX",true),
            field("vehicleNo","Vehicle No","TEXTBOX",true),
            field("pickupTime","Pickup Time","TEXTBOX",true),
            field("distanceKm","Distance (km)","NUMBER",false),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> payrollRunForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "payroll_run");
    form.put("title", "Payroll Run");
    form.put("sections", List.of(Map.of(
        "id", "pay", "title", "Pay", "repeatable", false,
        "fields", List.of(
            field("employeeName","Employee Name","TEXTBOX",true),
            field("employeeId","Employee ID","TEXTBOX",true),
            field("month","Month","TEXTBOX",true),
            field("basicPay","Basic Pay","NUMBER",true),
            field("allowances","Allowances","NUMBER",false),
            field("deductions","Deductions","NUMBER",false),
            field("netPay","Net Pay","NUMBER",true),
            field("email","Email","EMAIL",false),
            field("mobile","Mobile","PHONE",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> employeeMasterForm() {
    Map<String, Object> form = new LinkedHashMap<>();
    form.put("formKey", "employee_master");
    form.put("title", "Employee Master");
    form.put("sections", List.of(Map.of(
        "id", "employee", "title", "Employee", "repeatable", false,
        "fields", List.of(
            field("employeeNo","Employee No","TEXTBOX",false),
            field("fullName","Full Name","TEXTBOX",true),
            field("mobile","Mobile","PHONE",true),
            field("email","Email","EMAIL",false),
            field("department","Department","TEXTBOX",true),
            field("designation","Designation","TEXTBOX",true),
            field("employmentType","Employment Type","TEXTBOX",true),
            field("authUsername","Linked Login Username","TEXTBOX",false),
            field("assignedClassSections","Assigned Class Sections","TEXTBOX",false),
            field("gender","Gender","TEXTBOX",false),
            field("joiningDate","Joining Date","TEXTBOX",true),
            field("status","Status","TEXTBOX",false)))));
    form.put("validationRules", List.of());
    form.put("conditionalVisibility", List.of());
    return form;
  }

  private Map<String, Object> field(String key, String label, String type, boolean mandatory) {
    return field(key, label, type, mandatory, true, false);
  }

  private Map<String, Object> field(
      String key,
      String label,
      String type,
      boolean mandatory,
      boolean showInReports,
      boolean showOnIdCard) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("key", key);
    f.put("label", label);
    f.put("type", type);
    f.put("mandatory", mandatory);
    f.put("showInReports", showInReports);
    f.put("showOnIdCard", showOnIdCard);
    return f;
  }

  /** Backfill report/ID-card visibility flags on existing form fields (config-driven). */
  @SuppressWarnings("unchecked")
  private boolean ensureFieldVisibilityFlags(Map<String, Object> form) {
    Object sectionsObj = form.get("sections");
    if (!(sectionsObj instanceof List<?> sections)) {
      return false;
    }
    boolean changed = false;
    for (Object sectionObj : sections) {
      if (!(sectionObj instanceof Map<?, ?>)) {
        continue;
      }
      Map<String, Object> section = (Map<String, Object>) sectionObj;
      Object fieldsObj = section.get("fields");
      if (!(fieldsObj instanceof List<?> fields)) {
        continue;
      }
      for (Object fieldObj : fields) {
        if (!(fieldObj instanceof Map<?, ?>)) {
          continue;
        }
        Map<String, Object> field = (Map<String, Object>) fieldObj;
        String key = String.valueOf(field.get("key"));
        if (!field.containsKey("showInReports")) {
          field.put("showInReports", true);
          changed = true;
        }
        if (!field.containsKey("showOnIdCard")) {
          boolean onCard =
              "fullName".equals(key)
                  || "admissionNo".equals(key)
                  || "classApplied".equals(key)
                  || "rollNo".equals(key)
                  || "penNumber".equals(key)
                  || "apaarId".equals(key)
                  || "schoolStudentId".equals(key);
          field.put("showOnIdCard", onCard);
          changed = true;
        }
      }
    }
    return changed;
  }

  /** Adds missing fields to a section (creates the section when absent). */
  @SuppressWarnings("unchecked")
  private boolean ensureSectionFields(
      Map<String, Object> form,
      String sectionId,
      String sectionTitle,
      List<Map<String, Object>> desiredFields) {
    Object sectionsObj = form.get("sections");
    List<Object> sections =
        sectionsObj instanceof List<?> existing ? new ArrayList<>(existing) : new ArrayList<>();
    Map<String, Object> section = null;
    for (Object sectionObj : sections) {
      if (sectionObj instanceof Map<?, ?> raw && sectionId.equals(String.valueOf(raw.get("id")))) {
        section = (Map<String, Object>) raw;
        break;
      }
    }
    boolean changed = false;
    if (section == null) {
      section = new LinkedHashMap<>();
      section.put("id", sectionId);
      section.put("title", sectionTitle);
      section.put("repeatable", false);
      section.put("fields", new ArrayList<Map<String, Object>>());
      sections.add(section);
      form.put("sections", sections);
      changed = true;
    }
    Object fieldsObj = section.get("fields");
    List<Object> fields =
        fieldsObj instanceof List<?> list ? new ArrayList<>(list) : new ArrayList<>();
    Set<String> present = new LinkedHashSet<>();
    for (Object f : fields) {
      if (f instanceof Map<?, ?> m) {
        present.add(String.valueOf(m.get("key")));
      }
    }
    for (Map<String, Object> desired : desiredFields) {
      String key = String.valueOf(desired.get("key"));
      if (present.contains(key)) {
        continue;
      }
      fields.add(new LinkedHashMap<>(desired));
      present.add(key);
      changed = true;
    }
    section.put("fields", fields);
    return changed;
  }
}

