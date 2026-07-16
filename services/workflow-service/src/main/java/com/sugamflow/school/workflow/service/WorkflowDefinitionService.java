package com.sugamflow.school.workflow.service;
import com.sugamflow.school.workflow.persistence.entity.WorkflowDefinitionEntity;
import com.sugamflow.school.workflow.persistence.repo.WorkflowDefinitionRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowDefinitionService {
  private final WorkflowDefinitionRepository repo;
  public WorkflowDefinitionService(WorkflowDefinitionRepository repo){ this.repo=repo; }

  @Transactional
  public List<Map<String,Object>> list(String org){
    ensureDefaults();
    Map<String,Map<String,Object>> byKey=new LinkedHashMap<>();
    for(WorkflowDefinitionEntity e: repo.findVisible(org)){
      if(e.getOrganizationId()==null) byKey.put(e.getWorkflowKey(), e.getPayload());
    }
    for(WorkflowDefinitionEntity e: repo.findVisible(org)){
      if(org.equals(e.getOrganizationId())) byKey.put(e.getWorkflowKey(), e.getPayload());
    }
    return new ArrayList<>(byKey.values());
  }

  @Transactional
  public Map<String,Object> get(String org,String key){
    ensureDefaults();
    return repo.findByOrganizationIdAndWorkflowKey(org,key)
      .or(() -> repo.findByOrganizationIdIsNullAndWorkflowKey(key))
      .map(WorkflowDefinitionEntity::getPayload).orElse(null);
  }

  @Transactional
  public Map<String,Object> save(String org,String key,Map<String,Object> body){
    body.put("workflowKey", key); body.put("organizationId", org);
    WorkflowDefinitionEntity e = repo.findByOrganizationIdAndWorkflowKey(org,key).orElseGet(WorkflowDefinitionEntity::new);
    e.setOrganizationId(org); e.setWorkflowKey(key); e.setPayload(body); e.setUpdatedAt(Instant.now());
    return repo.save(e).getPayload();
  }

  @Transactional
  public void ensureDefaults(){
    upsertSystem("admission", defaultAdmission());
    upsertSystem("fee", defaultFee());
    upsertSystem("attendance", defaultAttendance());
    upsertSystem("exam", defaultExam());
    upsertSystem("library", defaultLibrary());
    upsertSystem("hostel", defaultHostel());
    upsertSystem("transport", defaultTransport());
    upsertSystem("payroll", defaultPayroll());
  }

  private void upsertSystem(String key, Map<String,Object> payload){
    if(repo.findByOrganizationIdIsNullAndWorkflowKey(key).isPresent()) return;
    WorkflowDefinitionEntity e=new WorkflowDefinitionEntity();
    e.setOrganizationId(null); e.setWorkflowKey(key); e.setPayload(payload); e.setUpdatedAt(Instant.now());
    repo.save(e);
  }

  private Map<String,Object> defaultAdmission(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","admission"); wf.put("name","Admission Approval");
    wf.put("steps", List.of(
      step(1,"Reception","RECEPTION",24), step(2,"Principal","PRINCIPAL",48),
      step(3,"Accounts","ACCOUNTANT",24), step(4,"Management","MANAGEMENT",72),
      step(5,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultFee(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","fee"); wf.put("name","Fee Collection Approval");
    wf.put("steps", List.of(
      step(1,"Cashier","CASHIER",8),
      step(2,"Accounts","ACCOUNTANT",24),
      step(3,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultAttendance(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","attendance"); wf.put("name","Attendance Approval");
    wf.put("steps", List.of(
      step(1,"Teacher","TEACHER",8),
      step(2,"Coordinator","COORDINATOR",24),
      step(3,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultExam(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","exam"); wf.put("name","Exam Marks Approval");
    wf.put("steps", List.of(
      step(1,"Teacher","TEACHER",8),
      step(2,"Exam Coordinator","EXAM_COORDINATOR",24),
      step(3,"Principal","PRINCIPAL",48),
      step(4,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultLibrary(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","library"); wf.put("name","Library Issue Approval");
    wf.put("steps", List.of(
      step(1,"Staff","LIBRARY_STAFF",8),
      step(2,"Approver","LIBRARIAN",24),
      step(3,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultHostel(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","hostel"); wf.put("name","Hostel Allocation Approval");
    wf.put("steps", List.of(
      step(1,"Staff","HOSTEL_STAFF",8),
      step(2,"Approver","WARDEN",24),
      step(3,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultTransport(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","transport"); wf.put("name","Transport Route Approval");
    wf.put("steps", List.of(
      step(1,"Staff","TRANSPORT_STAFF",8),
      step(2,"Approver","TRANSPORT_MANAGER",24),
      step(3,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> defaultPayroll(){
    Map<String,Object> wf=new LinkedHashMap<>();
    wf.put("workflowKey","payroll"); wf.put("name","Payroll Run Approval");
    wf.put("steps", List.of(
      step(1,"Staff","PAYROLL_STAFF",8),
      step(2,"Approver","ACCOUNTANT",24),
      step(3,"Completed","SYSTEM",0)));
    wf.put("autoApproveRules", List.of()); wf.put("rejectRules", List.of());
    wf.put("escalationRules", List.of()); wf.put("notificationRules", List.of());
    return wf;
  }

  private Map<String,Object> step(int seq,String name,String role,int sla){
    Map<String,Object> s=new LinkedHashMap<>();
    s.put("sequence",seq); s.put("name",name); s.put("assignRole",role); s.put("slaHours",sla); s.put("autoApprove",false);
    return s;
  }
}
