package com.sugamflow.school.settings.model;

import java.util.ArrayList;
import java.util.List;

public class MenuNode {

  private String id;
  private String label;
  private String icon;
  private String route;
  private int order;
  private boolean visible = true;
  private List<String> roles = new ArrayList<>();
  private List<String> requiredFeatureFlags = new ArrayList<>();
  private List<String> branchIds = new ArrayList<>();
  private List<MenuNode> children = new ArrayList<>();

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getLabel() { return label; }
  public void setLabel(String label) { this.label = label; }
  public String getIcon() { return icon; }
  public void setIcon(String icon) { this.icon = icon; }
  public String getRoute() { return route; }
  public void setRoute(String route) { this.route = route; }
  public int getOrder() { return order; }
  public void setOrder(int order) { this.order = order; }
  public boolean isVisible() { return visible; }
  public void setVisible(boolean visible) { this.visible = visible; }
  public List<String> getRoles() { return roles; }
  public void setRoles(List<String> roles) { this.roles = roles; }
  public List<String> getRequiredFeatureFlags() { return requiredFeatureFlags; }
  public void setRequiredFeatureFlags(List<String> requiredFeatureFlags) {
    this.requiredFeatureFlags = requiredFeatureFlags;
  }
  public List<String> getBranchIds() { return branchIds; }
  public void setBranchIds(List<String> branchIds) { this.branchIds = branchIds; }
  public List<MenuNode> getChildren() { return children; }
  public void setChildren(List<MenuNode> children) { this.children = children; }
}
