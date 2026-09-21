package com.sugamflow.school.ruleengine.service;
import com.sugamflow.school.ruleengine.persistence.entity.BusinessRuleEntity;
import com.sugamflow.school.ruleengine.persistence.repo.BusinessRuleRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BusinessRuleService {
  private final BusinessRuleRepository repo;
  public BusinessRuleService(BusinessRuleRepository repo){ this.repo=repo; }

  @Transactional
  public List<Map<String,Object>> list(String org){
    ensureDefaults();
    Map<String,Map<String,Object>> byKey=new LinkedHashMap<>();
    for(BusinessRuleEntity e: repo.findVisible(org)) if(e.getOrganizationId()==null) byKey.put(e.getRuleId(), e.getPayload());
    for(BusinessRuleEntity e: repo.findVisible(org)) if(org.equals(e.getOrganizationId())) byKey.put(e.getRuleId(), e.getPayload());
    return new ArrayList<>(byKey.values());
  }

  @Transactional(readOnly=true)
  public Map<String,Object> get(String org,String id){
    return repo.findByOrganizationIdAndRuleId(org,id).or(() -> repo.findByOrganizationIdIsNullAndRuleId(id))
      .map(BusinessRuleEntity::getPayload).orElse(null);
  }

  @Transactional
  public Map<String,Object> save(String org,String id,Map<String,Object> rule){
    rule.put("id", id); rule.put("organizationId", org);
    BusinessRuleEntity e=repo.findByOrganizationIdAndRuleId(org,id).orElseGet(BusinessRuleEntity::new);
    e.setOrganizationId(org); e.setRuleId(id); e.setPayload(rule); e.setUpdatedAt(Instant.now());
    return repo.save(e).getPayload();
  }

  @Transactional
  public Map<String,Object> evaluate(String org, Map<String,Object> context){
    List<String> matched=new ArrayList<>();
    for(Map<String,Object> rule: list(org)){
      if(!Boolean.TRUE.equals(rule.getOrDefault("enabled", true))) continue;
      @SuppressWarnings("unchecked") Map<String,Object> when=(Map<String,Object>)rule.get("when");
      if(when==null) continue;
      Object actual=dig(context, String.valueOf(when.get("field")));
      if(matches(actual, String.valueOf(when.get("op")), when.get("value"))){
        @SuppressWarnings("unchecked") Map<String,Object> then=(Map<String,Object>)rule.get("then");
        if(then!=null) matched.add(String.valueOf(then.get("action")));
      }
    }
    return Map.of("matchedActions", matched);
  }

  @Transactional
  public void ensureDefaults(){
    upsertSystem(sample("attendance_exam_block","IF Attendance < 75% THEN Block Exam","attendance.percent","LT",75,"BLOCK_EXAM"));
    upsertSystem(sample("fees_id_disable","IF Fees Pending > 90 Days THEN Disable ID Card","fees.pendingDays","GT",90,"DISABLE_ID_CARD"));
    upsertSystem(sample("birthday_whatsapp","IF Student Birthday THEN Send WhatsApp","student.isBirthday","EQ",true,"SEND_WHATSAPP"));
    // Phase 5 — admission vertical slice (config rules, not hardcoded in admission-service)
    upsertSystem(sample("admission_age_min","IF Applicant age < 3 THEN Block Admission","application.age","LT",3,"BLOCK_ADMISSION"));
    upsertSystem(sample("admission_docs_incomplete","IF Documents incomplete THEN Notify Admission desk","application.documentsComplete","EQ",false,"NOTIFY_ADMISSION"));
    upsertSystem(sample("fee_amount_min","IF Fee amount < 1 THEN Block collection","payment.amount","LT",1,"BLOCK_FEE"));
    upsertSystem(sample("fee_overdue_notify","IF Fee pending days > 90 THEN Notify fee desk","payment.pendingDays","GT",90,"NOTIFY_FEE"));
    upsertSystem(sample("attendance_percent_min","IF Attendance percent < 1 THEN Block marking","attendance.attendancePercent","LT",1,"BLOCK_ATTENDANCE"));
    upsertSystem(sample("exam_marks_overflow","IF Marks obtained > 100 THEN Block exam","exam.marksObtained","GT",100,"BLOCK_EXAM"));
    upsertSystem(sample("exam_marks_underflow","IF Marks obtained < 0 THEN Block exam","exam.marksObtained","LT",0,"BLOCK_EXAM"));
    upsertSystem(sample("exam_fail_notify","IF Marks obtained < 33 THEN Notify exam desk","exam.marksObtained","LT",33,"NOTIFY_EXAM"));
    // Phase 10 — ops modules (library, hostel, transport, payroll) vertical slices.
    upsertSystem(sample("library_bookid_blank","IF Book ID blank THEN Block issue","library.bookId","EQ","","BLOCK_LIBRARY"));
    upsertSystem(sample("library_duedays_negative","IF Due days < 0 THEN Block issue","library.dueDays","LT",0,"BLOCK_LIBRARY"));
    upsertSystem(sample("library_duedays_overflow","IF Due days > 30 THEN Notify library desk","library.dueDays","GT",30,"NOTIFY_LIBRARY"));
    upsertSystem(sample("hostel_bedno_min","IF Bed No < 1 THEN Block allocation","hostel.bedNo","LT",1,"BLOCK_HOSTEL"));
    upsertSystem(sample("hostel_pending_fee_notify","IF Pending fee > 0 THEN Notify hostel desk","hostel.pendingFee","GT",0,"NOTIFY_HOSTEL"));
    upsertSystem(sample("transport_distance_negative","IF Distance km < 0 THEN Block route","transport.distanceKm","LT",0,"BLOCK_TRANSPORT"));
    upsertSystem(sample("transport_distance_overflow","IF Distance km > 50 THEN Notify transport desk","transport.distanceKm","GT",50,"NOTIFY_TRANSPORT"));
    upsertSystem(sample("payroll_netpay_negative","IF Net pay < 0 THEN Block payroll run","payroll.netPay","LT",0,"BLOCK_PAYROLL"));
    upsertSystem(sample("payroll_netpay_low_notify","IF Net pay < 5000 THEN Notify payroll desk","payroll.netPay","LT",5000,"NOTIFY_PAYROLL"));
    // Phase 20b — TC clearance (fee dues, library books) via rule engine.
    upsertSystem(sample("tc_fee_dues_block","IF Fee pending amount > 0 THEN Block TC","fees.pendingAmount","GT",0,"BLOCK_TC"));
    upsertSystem(sample("tc_library_books_block","IF Library outstanding books > 0 THEN Block TC","library.outstandingBooks","GT",0,"BLOCK_TC"));
  }

  private void upsertSystem(Map<String,Object> rule){
    String id=String.valueOf(rule.get("id"));
    if(repo.findByOrganizationIdIsNullAndRuleId(id).isPresent()) return;
    BusinessRuleEntity e=new BusinessRuleEntity();
    e.setOrganizationId(null); e.setRuleId(id); e.setPayload(rule); e.setUpdatedAt(Instant.now());
    repo.save(e);
  }

  private void saveSystem(Map<String,Object> rule){
    upsertSystem(rule);
  }

  private Map<String,Object> sample(String id,String name,String field,String op,Object value,String action){
    Map<String,Object> rule=new LinkedHashMap<>();
    rule.put("id",id); rule.put("name",name); rule.put("enabled",true);
    rule.put("when", Map.of("field",field,"op",op,"value",value));
    rule.put("then", Map.of("action",action));
    return rule;
  }

  private Object dig(Map<String,Object> ctx,String path){
    Object cur=ctx;
    for(String p: path.split("\\.")){
      if(!(cur instanceof Map<?,?> m)) return null;
      cur=m.get(p);
    }
    return cur;
  }

  private boolean matches(Object actual,String op,Object expected){
    if(actual==null) return false;
    return switch(op){
      case "EQ" -> String.valueOf(actual).equals(String.valueOf(expected));
      case "LT" -> toDouble(actual) < toDouble(expected);
      case "GT" -> toDouble(actual) > toDouble(expected);
      case "LTE" -> toDouble(actual) <= toDouble(expected);
      case "GTE" -> toDouble(actual) >= toDouble(expected);
      default -> false;
    };
  }
  private double toDouble(Object v){ return Double.parseDouble(String.valueOf(v)); }
}