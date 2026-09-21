package com.sugamflow.school.settings.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UiScreenConfig {

  private String screenKey;
  private List<UiFieldConfig> fields = new ArrayList<>();
  private List<String> tabs = new ArrayList<>();
  private List<String> quickActions = new ArrayList<>();
  private Map<String, Object> defaultValues;

  public String getScreenKey() { return screenKey; }
  public void setScreenKey(String screenKey) { this.screenKey = screenKey; }
  public List<UiFieldConfig> getFields() { return fields; }
  public void setFields(List<UiFieldConfig> fields) { this.fields = fields; }
  public List<String> getTabs() { return tabs; }
  public void setTabs(List<String> tabs) { this.tabs = tabs; }
  public List<String> getQuickActions() { return quickActions; }
  public void setQuickActions(List<String> quickActions) { this.quickActions = quickActions; }
  public Map<String, Object> getDefaultValues() { return defaultValues; }
  public void setDefaultValues(Map<String, Object> defaultValues) { this.defaultValues = defaultValues; }

  public static class UiFieldConfig {
    private String fieldKey;
    private boolean visible = true;
    private boolean mandatory;
    private boolean readOnly;
    private boolean editable = true;
    private int sequence;
    private String group;
    private String tab;
    private List<String> roles = new ArrayList<>();
    private List<String> branchIds = new ArrayList<>();
    private Map<String, Object> validation;

    public String getFieldKey() { return fieldKey; }
    public void setFieldKey(String fieldKey) { this.fieldKey = fieldKey; }
    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public boolean isReadOnly() { return readOnly; }
    public void setReadOnly(boolean readOnly) { this.readOnly = readOnly; }
    public boolean isEditable() { return editable; }
    public void setEditable(boolean editable) { this.editable = editable; }
    public int getSequence() { return sequence; }
    public void setSequence(int sequence) { this.sequence = sequence; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public String getTab() { return tab; }
    public void setTab(String tab) { this.tab = tab; }
    public List<String> getRoles() { return roles; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public List<String> getBranchIds() { return branchIds; }
    public void setBranchIds(List<String> branchIds) { this.branchIds = branchIds; }
    public Map<String, Object> getValidation() { return validation; }
    public void setValidation(Map<String, Object> validation) { this.validation = validation; }
  }
}
