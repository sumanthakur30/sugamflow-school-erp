package com.sugamflow.school.settings.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleSettings {

  private String moduleKey;
  private String organizationId;
  private String branchId;
  private String academicSessionId;
  private Map<String, Object> settings = new LinkedHashMap<>();

  public ModuleSettings() {}

  public ModuleSettings(String moduleKey, String organizationId, String branchId) {
    this.moduleKey = moduleKey;
    this.organizationId = organizationId;
    this.branchId = branchId;
  }

  public String getModuleKey() { return moduleKey; }
  public void setModuleKey(String moduleKey) { this.moduleKey = moduleKey; }
  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getAcademicSessionId() { return academicSessionId; }
  public void setAcademicSessionId(String academicSessionId) { this.academicSessionId = academicSessionId; }
  public Map<String, Object> getSettings() { return settings; }
  public void setSettings(Map<String, Object> settings) { this.settings = settings; }
}
