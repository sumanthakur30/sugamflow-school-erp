package com.sugamflow.school.settings.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DesignTheme {

  private String organizationId;
  private String branchId;
  private String version;
  private String status;

  private Map<String, String> branding = new LinkedHashMap<>();
  private Map<String, String> colors = new LinkedHashMap<>();
  private Map<String, Object> typography = new LinkedHashMap<>();
  private Map<String, Object> loginScreen = new LinkedHashMap<>();
  private Map<String, Object> dashboard = new LinkedHashMap<>();
  private boolean darkModeEnabled = true;
  private boolean lightModeEnabled = true;

  public static DesignTheme platformDefault(String organizationId, String branchId) {
    DesignTheme t = new DesignTheme();
    t.organizationId = organizationId;
    t.branchId = branchId;
    t.version = "1";
    t.status = "PUBLISHED";
    t.branding.putAll(
        Map.of(
            "schoolName", "SugamFlow School",
            "productTagline", "Configuration over customization",
            "schoolLogo", "",
            "loginLogo", "",
            "mobileSplash", "",
            "favicon", "",
            "watermark", "",
            "footer", "Powered by SugamFlow",
            "reportHeader", "",
            "reportFooter", ""));
    t.colors.putAll(
        Map.ofEntries(
            Map.entry("primary", "#0B6E4F"),
            Map.entry("secondary", "#084C61"),
            Map.entry("accent", "#E9B44C"),
            Map.entry("menu", "#0B3D2E"),
            Map.entry("button", "#0B6E4F"),
            Map.entry("text", "#1A1A1A"),
            Map.entry("warning", "#D97706"),
            Map.entry("success", "#15803D"),
            Map.entry("error", "#B91C1C"),
            Map.entry("surface", "#F3F7F5"),
            Map.entry("panel", "#FFFFFF")));
    t.typography.putAll(
        Map.of(
            "fontFamily", "Source Sans 3",
            "fontSize", "14px",
            "headingStyle", "semibold",
            "buttonStyle", "solid",
            "borderRadius", "8px",
            "cardStyle", "flat",
            "spacing", "comfortable"));
    t.loginScreen.putAll(
        Map.of(
            "backgroundImage", "",
            "sliderImages", List.of(),
            "videoBackground", "",
            "announcementArea", true,
            "noticeBoard", true,
            "holidayBanner", true,
            "admissionBanner", true));
    t.dashboard.putAll(
        Map.of(
            "widgetArrangement", List.of("attendance", "fees", "notices", "shortcuts"),
            "cards", List.of("students", "teachers", "collections", "pendingFees"),
            "shortcuts", List.of(),
            "quickActions", List.of("admitStudent", "collectFee", "markAttendance"),
            "charts", List.of("feeTrend", "attendanceTrend"),
            "pinnedModules", List.of(),
            "favoriteReports", List.of(),
            "hiddenComponents", List.of()));
    return t;
  }

  public String getOrganizationId() { return organizationId; }
  public void setOrganizationId(String organizationId) { this.organizationId = organizationId; }
  public String getBranchId() { return branchId; }
  public void setBranchId(String branchId) { this.branchId = branchId; }
  public String getVersion() { return version; }
  public void setVersion(String version) { this.version = version; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Map<String, String> getBranding() { return branding; }
  public void setBranding(Map<String, String> branding) { this.branding = branding; }
  public Map<String, String> getColors() { return colors; }
  public void setColors(Map<String, String> colors) { this.colors = colors; }
  public Map<String, Object> getTypography() { return typography; }
  public void setTypography(Map<String, Object> typography) { this.typography = typography; }
  public Map<String, Object> getLoginScreen() { return loginScreen; }
  public void setLoginScreen(Map<String, Object> loginScreen) { this.loginScreen = loginScreen; }
  public Map<String, Object> getDashboard() { return dashboard; }
  public void setDashboard(Map<String, Object> dashboard) { this.dashboard = dashboard; }
  public boolean isDarkModeEnabled() { return darkModeEnabled; }
  public void setDarkModeEnabled(boolean darkModeEnabled) { this.darkModeEnabled = darkModeEnabled; }
  public boolean isLightModeEnabled() { return lightModeEnabled; }
  public void setLightModeEnabled(boolean lightModeEnabled) { this.lightModeEnabled = lightModeEnabled; }
}
