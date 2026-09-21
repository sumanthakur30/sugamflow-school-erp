package com.sugamflow.school.settings.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sugamflow.school.common.tenant.TenantScope;
import com.sugamflow.school.settings.integration.SubscriptionClient;
import com.sugamflow.school.settings.model.DesignTheme;
import com.sugamflow.school.settings.model.LocalizationSettings;
import com.sugamflow.school.settings.model.MenuNode;
import com.sugamflow.school.settings.model.ModuleSettings;
import com.sugamflow.school.settings.model.UiScreenConfig;
import com.sugamflow.school.settings.portal.PortalCatalog;
import com.sugamflow.school.settings.offline.OfflineCatalog;
import com.sugamflow.school.settings.persistence.entity.AiSettingsEntity;
import com.sugamflow.school.settings.persistence.entity.DesignThemeEntity;
import com.sugamflow.school.settings.persistence.entity.LocalizationSettingsEntity;
import com.sugamflow.school.settings.persistence.entity.MenuConfigEntity;
import com.sugamflow.school.settings.persistence.entity.ModuleSettingsEntity;
import com.sugamflow.school.settings.persistence.entity.RoleDashboardEntity;
import com.sugamflow.school.settings.persistence.entity.UiScreenConfigEntity;
import com.sugamflow.school.settings.persistence.repo.AiSettingsRepository;
import com.sugamflow.school.settings.persistence.repo.DesignThemeRepository;
import com.sugamflow.school.settings.persistence.repo.LocalizationSettingsRepository;
import com.sugamflow.school.settings.persistence.repo.MenuConfigRepository;
import com.sugamflow.school.settings.persistence.repo.ModuleSettingsRepository;
import com.sugamflow.school.settings.persistence.repo.RoleDashboardRepository;
import com.sugamflow.school.settings.persistence.repo.UiScreenConfigRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsConfigService {

  public static final List<String> MODULE_KEYS =
      List.of(
          "admission", "attendance", "fee", "student", "staff", "payroll", "transport", "hostel", "exam", "library",
          "lms",
          "parent_portal", "teacher_portal", "offline",
          "communication", "inventory", "notification", "security", "ai", "dashboard", "workflow",
          "printing");

  private final DesignThemeRepository designThemeRepository;
  private final ModuleSettingsRepository moduleSettingsRepository;
  private final MenuConfigRepository menuConfigRepository;
  private final LocalizationSettingsRepository localizationSettingsRepository;
  private final UiScreenConfigRepository uiScreenConfigRepository;
  private final AiSettingsRepository aiSettingsRepository;
  private final RoleDashboardRepository roleDashboardRepository;
  private final SubscriptionClient subscriptionClient;
  private final ObjectMapper objectMapper;

  public SettingsConfigService(
      DesignThemeRepository designThemeRepository,
      ModuleSettingsRepository moduleSettingsRepository,
      MenuConfigRepository menuConfigRepository,
      LocalizationSettingsRepository localizationSettingsRepository,
      UiScreenConfigRepository uiScreenConfigRepository,
      AiSettingsRepository aiSettingsRepository,
      RoleDashboardRepository roleDashboardRepository,
      SubscriptionClient subscriptionClient,
      ObjectMapper objectMapper) {
    this.designThemeRepository = designThemeRepository;
    this.moduleSettingsRepository = moduleSettingsRepository;
    this.menuConfigRepository = menuConfigRepository;
    this.localizationSettingsRepository = localizationSettingsRepository;
    this.uiScreenConfigRepository = uiScreenConfigRepository;
    this.aiSettingsRepository = aiSettingsRepository;
    this.roleDashboardRepository = roleDashboardRepository;
    this.subscriptionClient = subscriptionClient;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public DesignTheme getOrCreateTheme(String org, String branch) {
    return designThemeRepository
        .findByOrganizationIdAndBranchId(org, branch)
        .map(this::toTheme)
        .orElseGet(
            () -> {
              DesignTheme theme = DesignTheme.platformDefault(org, branch);
              return saveTheme(theme);
            });
  }

  /**
   * Public login white-label: return stored theme if present, otherwise an in-memory platform
   * default. Does <strong>not</strong> persist — avoids creating orphan rows while users type an
   * organization id on the login screen.
   */
  @Transactional(readOnly = true)
  public DesignTheme findThemeOrPlatformDefault(String org, String branch) {
    return designThemeRepository
        .findByOrganizationIdAndBranchId(org, branch)
        .map(this::toTheme)
        .orElseGet(() -> DesignTheme.platformDefault(org, branch));
  }

  @Transactional
  public DesignTheme saveTheme(DesignTheme theme) {
    DesignThemeEntity entity =
        designThemeRepository
            .findByOrganizationIdAndBranchId(theme.getOrganizationId(), theme.getBranchId())
            .orElseGet(DesignThemeEntity::new);
    entity.setOrganizationId(theme.getOrganizationId());
    entity.setBranchId(theme.getBranchId());
    entity.setVersion(theme.getVersion() == null ? "1" : theme.getVersion());
    entity.setStatus(theme.getStatus() == null ? "DRAFT" : theme.getStatus());
    entity.setPayload(toMap(theme));
    entity.setUpdatedAt(Instant.now());
    return toTheme(designThemeRepository.save(entity));
  }

  @Transactional
  public ModuleSettings getModule(String moduleKey, String org, String branch) {
    return moduleSettingsRepository
        .findByOrganizationIdAndBranchIdAndModuleKey(org, branch, moduleKey)
        .map(
            entity -> {
              ModuleSettings ms = toModule(entity);
              if (enrichModuleDefaults(ms)) {
                return saveModule(ms);
              }
              return ms;
            })
        .orElseGet(
            () -> {
              ModuleSettings ms = new ModuleSettings(moduleKey, org, branch);
              Map<String, Object> settings = new LinkedHashMap<>();
              settings.put("enabled", true);
              settings.put("moduleKey", moduleKey);
              settings.put("notes", "Configure without code changes");
              if ("admission".equals(moduleKey)) {
                applyAdmissionDefaults(settings);
              }
              if ("fee".equals(moduleKey)) {
                applyFeeDefaults(settings);
              }
              if ("student".equals(moduleKey)) {
                applyStudentDefaults(settings);
              }
              if ("staff".equals(moduleKey)) {
                applyStaffDefaults(settings);
              }
              if ("attendance".equals(moduleKey)) {
                applyAttendanceDefaults(settings);
              }
              if ("exam".equals(moduleKey)) {
                applyExamDefaults(settings);
              }
              if ("library".equals(moduleKey)) {
                applyLibraryDefaults(settings);
              }
              if ("hostel".equals(moduleKey)) {
                applyHostelDefaults(settings);
              }
              if ("transport".equals(moduleKey)) {
                applyTransportDefaults(settings);
              }
              if ("payroll".equals(moduleKey)) {
                applyPayrollDefaults(settings);
              }
              if ("lms".equals(moduleKey)) {
                applyLmsDefaults(settings);
              }
              if (PortalCatalog.MODULE_PARENT.equals(moduleKey)) {
                settings.putAll(PortalCatalog.defaultParentSettings());
              }
              if (PortalCatalog.MODULE_TEACHER.equals(moduleKey)) {
                settings.putAll(PortalCatalog.defaultTeacherSettings());
              }
              if (OfflineCatalog.MODULE_KEY.equals(moduleKey)) {
                settings.putAll(OfflineCatalog.defaultSettings());
              }
              ms.setSettings(settings);
              return saveModule(ms);
            });
  }

  private boolean enrichModuleDefaults(ModuleSettings ms) {
    if ("admission".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyAdmissionDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("fee".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyFeeDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("student".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyStudentDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("staff".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyStaffDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("attendance".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyAttendanceDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("exam".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyExamDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("library".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyLibraryDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("lms".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyLmsDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("hostel".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyHostelDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("transport".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyTransportDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if ("payroll".equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyPayrollDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if (PortalCatalog.MODULE_PARENT.equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyParentPortalDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if (PortalCatalog.MODULE_TEACHER.equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyTeacherPortalDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    if (OfflineCatalog.MODULE_KEY.equals(ms.getModuleKey())) {
      Map<String, Object> settings =
          ms.getSettings() != null ? new LinkedHashMap<>(ms.getSettings()) : new LinkedHashMap<>();
      boolean changed = applyOfflineDefaults(settings);
      if (changed) {
        ms.setSettings(settings);
      }
      return changed;
    }
    return false;
  }

  private static boolean applyOfflineDefaults(Map<String, Object> settings) {
    boolean changed = false;
    for (Map.Entry<String, Object> e : OfflineCatalog.defaultSettings().entrySet()) {
      changed |= putIfAbsent(settings, e.getKey(), e.getValue());
    }
    return changed;
  }

  private static boolean applyParentPortalDefaults(Map<String, Object> settings) {
    return mergePortalDefaults(settings, PortalCatalog.defaultParentSettings());
  }

  private static boolean applyTeacherPortalDefaults(Map<String, Object> settings) {
    return mergePortalDefaults(settings, PortalCatalog.defaultTeacherSettings());
  }

  /**
   * Portal catalogs evolve (new nav items / section apiPaths). Top-level putIfAbsent alone leaves
   * stale nested nav/sections in the DB — merge missing nav ids and section keys, and refresh known
   * legacy apiPaths to the roster/gradebook/parent endpoints.
   */
  @SuppressWarnings("unchecked")
  private static boolean mergePortalDefaults(Map<String, Object> settings, Map<String, Object> defaults) {
    boolean changed = false;
    for (Map.Entry<String, Object> e : defaults.entrySet()) {
      if ("nav".equals(e.getKey()) || "sections".equals(e.getKey())) {
        continue;
      }
      changed |= putIfAbsent(settings, e.getKey(), e.getValue());
    }

    Object defNav = defaults.get("nav");
    if (defNav instanceof List<?> wantNav) {
      List<Map<String, Object>> normalized = new ArrayList<>();
      Object curNav = settings.get("nav");
      if (curNav instanceof List<?> list) {
        for (Object item : list) {
          if (item instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked")
            Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) m);
            normalized.add(copy);
          }
        }
      }
      Set<String> have = new LinkedHashSet<>();
      for (Map<String, Object> item : normalized) {
        Object id = item.get("id");
        if (id != null) {
          have.add(String.valueOf(id));
        }
      }
      for (Object item : wantNav) {
        if (!(item instanceof Map<?, ?> m)) {
          continue;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> want = (Map<String, Object>) m;
        String id = String.valueOf(want.get("id"));
        if (!have.contains(id)) {
          normalized.add(new LinkedHashMap<>(want));
          have.add(id);
          changed = true;
        }
      }
      settings.put("nav", normalized);
    }

    Object defSections = defaults.get("sections");
    if (defSections instanceof Map<?, ?> wantSections) {
      Map<String, Object> sections =
          settings.get("sections") instanceof Map<?, ?> cur
              ? new LinkedHashMap<>((Map<String, Object>) cur)
              : new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : wantSections.entrySet()) {
        String key = String.valueOf(e.getKey());
        if (!sections.containsKey(key)) {
          sections.put(key, e.getValue());
          changed = true;
          continue;
        }
        if (e.getValue() instanceof Map<?, ?> wantSec && sections.get(key) instanceof Map<?, ?> curSec) {
          Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) curSec);
          String wantPath = String.valueOf(((Map<?, ?>) wantSec).get("apiPath"));
          String curPath = String.valueOf(merged.get("apiPath"));
          if (isLegacyPortalApiPath(curPath) && !wantPath.equals(curPath) && !"null".equals(wantPath)) {
            merged.put("apiPath", wantPath);
            if (((Map<?, ?>) wantSec).get("emptyMessage") != null) {
              merged.put("emptyMessage", ((Map<?, ?>) wantSec).get("emptyMessage"));
            }
            if (((Map<?, ?>) wantSec).get("title") != null) {
              merged.put("title", ((Map<?, ?>) wantSec).get("title"));
            }
            sections.put(key, merged);
            changed = true;
          }
        }
      }
      settings.put("sections", sections);
    }
    return changed;
  }

  private static boolean isLegacyPortalApiPath(String path) {
    if (path == null) {
      return true;
    }
    return switch (path) {
      case "/api/attendance/records",
          "/api/exam/records",
          "/api/exam/marks",
          "/api/attendance/marks" -> true;
      default -> false;
    };
  }

  /** Returns true if any key was added. */
  private static boolean applyAdmissionDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "admission_form");
    changed |= putIfAbsent(settings, "workflowKey", "admission");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_ADMISSION");
    changed |= putIfAbsent(settings, "offerLetterTemplateKey", "offer_letter");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "admission_approved");
    changed |= putIfAbsent(settings, "enrollOnApprove", true);
    changed |= putIfAbsent(settings, "studentFormKey", "student_master");
    changed |= putIfAbsent(settings, "generateAdmissionNo", true);
    return changed;
  }

  private static boolean applyStudentDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "student_master");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_STUDENT_MASTER");
    changed |= putIfAbsent(settings, "parentFormKey", "parent_master");
    changed |= putIfAbsent(settings, "guardiansAnswerKey", "guardians");
    changed |= putIfAbsent(settings, "captureGuardiansOnEnroll", true);
    changed |= putIfAbsent(settings, "maxGuardians", 4);
    changed |=
        putIfAbsent(
            settings,
            "guardianFieldMap",
            Map.of(
                "guardianFullName", "fullName",
                "guardianRelation", "relation",
                "guardianMobile", "mobile",
                "guardianEmail", "email"));
    changed |= putIfAbsent(settings, "lifecycleEnabled", true);
    changed |= putIfAbsent(settings, "classFieldKey", "classApplied");
    changed |= putIfAbsent(settings, "tcTemplateKey", "transfer_certificate");
    changed |= putIfAbsent(settings, "defaultPromotionMapKey", "default_grade_map");
    changed |= putIfAbsent(settings, "defaultStatusPolicyKey", "default_statuses");
    changed |= putIfAbsent(settings, "defaultTcPolicyKey", "default_tc");
    changed |= putIfAbsent(settings, "currentSessionKey", "2025-26");
    // Phase A/B — NEP identity + media (configuration toggles; no school-specific code)
    changed |= putIfAbsent(settings, "enableAadhaar", true);
    changed |= putIfAbsent(settings, "enablePen", true);
    changed |= putIfAbsent(settings, "enableApaar", true);
    changed |= putIfAbsent(settings, "enableSamagra", false);
    changed |= putIfAbsent(settings, "enableSchoolStudentId", true);
    changed |= putIfAbsent(settings, "maskAadhaar", true);
    changed |= putIfAbsent(settings, "aadhaarMandatory", false);
    changed |= putIfAbsent(settings, "penMandatory", false);
    changed |= putIfAbsent(settings, "enableStudentPhoto", true);
    changed |= putIfAbsent(settings, "enableGuardianPhoto", true);
    changed |= putIfAbsent(settings, "enableDocumentVault", true);
    changed |= putIfAbsent(settings, "maxPhotoKb", 512);
    changed |= putIfAbsent(settings, "maxDocumentKb", 2048);
    changed |=
        putIfAbsent(
            settings,
            "allowedPhotoTypes",
            List.of("image/jpeg", "image/png", "image/webp"));
    return changed;
  }

  private static boolean applyStaffDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "employee_master");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_STAFF_MASTER");
    changed |= putIfAbsent(settings, "directoryEnabled", true);
    return changed;
  }

  private static boolean applyFeeDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "fee_collection");
    changed |= putIfAbsent(settings, "workflowKey", "fee");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_FEE");
    changed |= putIfAbsent(settings, "feeReceiptTemplateKey", "fee_receipt");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "fee_approved");
    changed |= putIfAbsent(settings, "notifyOnDue", true);
    changed |= putIfAbsent(settings, "dueReminderChannels", List.of("IN_APP", "EMAIL", "SMS"));
    changed |= putIfAbsent(settings, "dueReminderMinPendingDays", 1);
    changed |= putIfAbsent(settings, "dueReminderTemplateId", "fee_due_reminder");
    changed |= putIfAbsent(settings, "financeMastersEnabled", true);
    changed |= putIfAbsent(settings, "defaultStructureKey", "grade_8_annual");
    changed |= putIfAbsent(settings, "defaultCurrency", "INR");
    changed |= putIfAbsent(settings, "gstEnabled", true);
    changed |= putIfAbsent(settings, "defaultGstRate", 0);
    changed |= putIfAbsent(settings, "lateFeeEnabled", true);
    changed |= putIfAbsent(settings, "lateFeePolicyKey", "late_per_day_10");
    changed |= putIfAbsent(settings, "scholarshipConcessionKey", "scholarship_50");
    changed |= putIfAbsent(settings, "autoApplyScholarship", true);
    changed |= putIfAbsent(settings, "hostelHeadKey", "HOSTEL");
    changed |= putIfAbsent(settings, "transportHeadKey", "TRANSPORT");
    changed |= putIfAbsent(settings, "defaultHostelMonthlyFee", 4500);
    changed |= putIfAbsent(settings, "defaultTransportFare", 1200);
    changed |= putIfAbsent(settings, "includeHostelInDemand", true);
    changed |= putIfAbsent(settings, "includeTransportInDemand", true);
    changed |= putIfAbsent(settings, "bulkDemandBatchSize", 200);
    return changed;
  }

  private static boolean applyAttendanceDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "attendance_mark");
    changed |= putIfAbsent(settings, "workflowKey", "attendance");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_ATTENDANCE");
    changed |= putIfAbsent(settings, "aiAttendanceEnabled", true);
    changed |= putIfAbsent(settings, "autoSubmitFromDevice", true);
    changed |= putIfAbsent(settings, "minConfidence", 0.8);
    changed |=
        putIfAbsent(
            settings,
            "acceptedAdapterTypes",
            List.of("BIOMETRIC", "FACE", "GPS", "AI_CAMERA"));
    changed |= putIfAbsent(settings, "notifyOnRosterSubmit", true);
    changed |= putIfAbsent(settings, "rosterAlertStatuses", List.of("ABSENT", "LATE"));
    Object channels = settings.get("rosterNotificationChannels");
    if (channels == null) {
      settings.put("rosterNotificationChannels", List.of("SMS", "EMAIL", "IN_APP"));
      changed = true;
    } else if (channels instanceof List<?> list
        && list.size() == 2
        && list.stream().map(String::valueOf).map(String::toUpperCase).collect(java.util.stream.Collectors.toSet())
            .equals(Set.of("SMS", "EMAIL"))) {
      // Upgrade only the legacy default; preserve explicitly customized channel lists.
      settings.put("rosterNotificationChannels", List.of("SMS", "EMAIL", "IN_APP"));
      changed = true;
    }
    return changed;
  }

  private static boolean applyExamDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "exam_marks");
    changed |= putIfAbsent(settings, "workflowKey", "exam");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_EXAM");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "exam_approved");
    return changed;
  }

  private static boolean applyLibraryDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "library_issue");
    changed |= putIfAbsent(settings, "workflowKey", "library");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_LIBRARY");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "library_approved");
    changed |= putIfAbsent(settings, "mastersEnabled", true);
    changed |= putIfAbsent(settings, "defaultFinePolicyKey", "default_fine");
    changed |= putIfAbsent(settings, "defaultCirculationPolicyKey", "default_circ");
    return changed;
  }

  private static boolean applyLmsDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_LMS");
    changed |= putIfAbsent(settings, "mode", "NATIVE_HOMEWORK");
    changed |= putIfAbsent(settings, "externalProvider", "NONE");
    changed |= putIfAbsent(settings, "externalBaseUrl", "");
    changed |= putIfAbsent(settings, "ssoEnabled", false);
    changed |= putIfAbsent(settings, "homeworkEnabled", true);
    changed |= putIfAbsent(settings, "parentSubmissionEnabled", true);
    changed |= putIfAbsent(settings, "assignmentNotifyChannels", List.of("IN_APP", "EMAIL"));
    changed |= putIfAbsent(settings, "notes", "Phase 24: native homework MVP; Moodle/Google Classroom later");
    return changed;
  }

  private static boolean applyHostelDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "hostel_allocation");
    changed |= putIfAbsent(settings, "workflowKey", "hostel");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_HOSTEL");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "hostel_approved");
    changed |= putIfAbsent(settings, "mastersEnabled", true);
    changed |= putIfAbsent(settings, "defaultBlockKey", "main_block");
    changed |= putIfAbsent(settings, "defaultRoomTypeKey", "twin_sharing");
    changed |= putIfAbsent(settings, "defaultAllocationPolicyKey", "default_alloc");
    return changed;
  }

  private static boolean applyTransportDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "transport_route");
    changed |= putIfAbsent(settings, "workflowKey", "transport");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_TRANSPORT");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "transport_approved");
    changed |= putIfAbsent(settings, "mastersEnabled", true);
    changed |= putIfAbsent(settings, "defaultRouteKey", "route_a");
    changed |= putIfAbsent(settings, "defaultFareSlabKey", "default_fare");
    return changed;
  }

  private static boolean applyPayrollDefaults(Map<String, Object> settings) {
    boolean changed = false;
    changed |= putIfAbsent(settings, "formKey", "payroll_run");
    changed |= putIfAbsent(settings, "workflowKey", "payroll");
    changed |= putIfAbsent(settings, "requiredFeatureFlag", "FEATURE_PAYROLL");
    changed |= putIfAbsent(settings, "notifyOnApprove", true);
    changed |= putIfAbsent(settings, "approveNotificationChannels", List.of("EMAIL", "IN_APP"));
    changed |= putIfAbsent(settings, "approveNotificationTemplateId", "payroll_approved");
    changed |= putIfAbsent(settings, "mastersEnabled", true);
    changed |= putIfAbsent(settings, "defaultSalaryStructureKey", "default_staff");
    changed |= putIfAbsent(settings, "defaultPayCycleKey", "monthly");
    return changed;
  }

  private static boolean putIfAbsent(Map<String, Object> map, String key, Object value) {
    if (map.containsKey(key) && map.get(key) != null) {
      return false;
    }
    map.put(key, value);
    return true;
  }

  @Transactional
  public ModuleSettings saveModule(ModuleSettings settings) {
    ModuleSettingsEntity entity =
        moduleSettingsRepository
            .findByOrganizationIdAndBranchIdAndModuleKey(
                settings.getOrganizationId(), settings.getBranchId(), settings.getModuleKey())
            .orElseGet(ModuleSettingsEntity::new);
    entity.setOrganizationId(settings.getOrganizationId());
    entity.setBranchId(settings.getBranchId());
    entity.setAcademicSessionId(settings.getAcademicSessionId());
    entity.setModuleKey(settings.getModuleKey());
    entity.setPayload(settings.getSettings() == null ? new LinkedHashMap<>() : settings.getSettings());
    entity.setUpdatedAt(Instant.now());
    return toModule(moduleSettingsRepository.save(entity));
  }

  @Transactional
  public List<MenuNode> getMenus(String org) {
    return menuConfigRepository
        .findByOrganizationId(org)
        .map(e -> objectMapper.convertValue(e.getPayload(), new TypeReference<List<MenuNode>>() {}))
        .orElseGet(() -> saveMenus(org, defaultMenus()));
  }

  /**
   * Filters menu nodes by visibility, caller role, branch, and subscription feature flags.
   * Empty roles/flags on a node means unrestricted for that dimension.
   */
  public List<MenuNode> getEffectiveMenus(TenantScope scope) {
    List<MenuNode> all = getMenus(scope.organizationId());
    String role = scope.roleCode() == null ? "" : scope.roleCode().trim().toUpperCase();
    String branch = scope.branchId();
    boolean elevated =
        "SHOP_OWNER".equals(role) || "SUPER_ADMIN".equals(role) || "ADMIN".equals(role);
    List<MenuNode> out = new ArrayList<>();
    for (MenuNode n : all) {
      MenuNode filtered = filterMenuNode(n, role, branch, elevated, scope);
      if (filtered != null) {
        out.add(filtered);
      }
    }
    return out;
  }

  private MenuNode filterMenuNode(
      MenuNode n, String role, String branch, boolean elevated, TenantScope scope) {
    if (n == null || !n.isVisible()) {
      return null;
    }
    if (n.getRoles() != null && !n.getRoles().isEmpty() && !elevated) {
      boolean roleOk =
          n.getRoles().stream().anyMatch(r -> r != null && r.trim().equalsIgnoreCase(role));
      if (!roleOk) {
        return null;
      }
    }
    if (n.getBranchIds() != null && !n.getBranchIds().isEmpty() && branch != null) {
      boolean branchOk =
          n.getBranchIds().stream().anyMatch(b -> b != null && b.equalsIgnoreCase(branch));
      if (!branchOk) {
        return null;
      }
    }
    if (n.getRequiredFeatureFlags() != null) {
      for (String flag : n.getRequiredFeatureFlags()) {
        if (flag != null
            && !flag.isBlank()
            && !subscriptionClient.isFeatureEnabled(scope, flag.trim())) {
          return null;
        }
      }
    }
    MenuNode copy = new MenuNode();
    copy.setId(n.getId());
    copy.setLabel(n.getLabel());
    copy.setIcon(n.getIcon());
    copy.setRoute(n.getRoute());
    copy.setOrder(n.getOrder());
    copy.setVisible(true);
    copy.setRoles(n.getRoles() == null ? List.of() : new ArrayList<>(n.getRoles()));
    copy.setRequiredFeatureFlags(
        n.getRequiredFeatureFlags() == null
            ? List.of()
            : new ArrayList<>(n.getRequiredFeatureFlags()));
    copy.setBranchIds(n.getBranchIds() == null ? List.of() : new ArrayList<>(n.getBranchIds()));
    List<MenuNode> children = new ArrayList<>();
    if (n.getChildren() != null) {
      for (MenuNode child : n.getChildren()) {
        MenuNode fc = filterMenuNode(child, role, branch, elevated, scope);
        if (fc != null) {
          children.add(fc);
        }
      }
    }
    copy.setChildren(children);
    return copy;
  }

  @Transactional
  public List<MenuNode> saveMenus(String org, List<MenuNode> nodes) {
    MenuConfigEntity entity =
        menuConfigRepository.findByOrganizationId(org).orElseGet(MenuConfigEntity::new);
    entity.setOrganizationId(org);
    entity.setPayload(objectMapper.convertValue(nodes, new TypeReference<List<Map<String, Object>>>() {}));
    entity.setUpdatedAt(Instant.now());
    MenuConfigEntity saved = menuConfigRepository.save(entity);
    return objectMapper.convertValue(saved.getPayload(), new TypeReference<List<MenuNode>>() {});
  }

  @Transactional
  public LocalizationSettings getLocale(String org, String branch) {
    return localizationSettingsRepository
        .findByOrganizationIdAndBranchId(org, branch)
        .map(e -> objectMapper.convertValue(e.getPayload(), LocalizationSettings.class))
        .orElseGet(
            () -> {
              LocalizationSettings locale = new LocalizationSettings();
              return saveLocale(org, branch, locale);
            });
  }

  @Transactional
  public LocalizationSettings saveLocale(String org, String branch, LocalizationSettings settings) {
    LocalizationSettingsEntity entity =
        localizationSettingsRepository
            .findByOrganizationIdAndBranchId(org, branch)
            .orElseGet(LocalizationSettingsEntity::new);
    entity.setOrganizationId(org);
    entity.setBranchId(branch);
    entity.setPayload(toMap(settings));
    entity.setUpdatedAt(Instant.now());
    return objectMapper.convertValue(
        localizationSettingsRepository.save(entity).getPayload(), LocalizationSettings.class);
  }

  @Transactional
  public UiScreenConfig getScreen(String screenKey, String org) {
    return uiScreenConfigRepository
        .findByOrganizationIdAndScreenKey(org, screenKey)
        .map(e -> objectMapper.convertValue(e.getPayload(), UiScreenConfig.class))
        .orElseGet(
            () -> {
              UiScreenConfig c = new UiScreenConfig();
              c.setScreenKey(screenKey);
              return saveScreen(org, c);
            });
  }

  @Transactional
  public UiScreenConfig saveScreen(String org, UiScreenConfig config) {
    UiScreenConfigEntity entity =
        uiScreenConfigRepository
            .findByOrganizationIdAndScreenKey(org, config.getScreenKey())
            .orElseGet(UiScreenConfigEntity::new);
    entity.setOrganizationId(org);
    entity.setScreenKey(config.getScreenKey());
    entity.setPayload(toMap(config));
    entity.setUpdatedAt(Instant.now());
    return objectMapper.convertValue(
        uiScreenConfigRepository.save(entity).getPayload(), UiScreenConfig.class);
  }

  @Transactional
  public Map<String, Object> getAi(String org) {
    return aiSettingsRepository
        .findByOrganizationId(org)
        .map(AiSettingsEntity::getPayload)
        .orElseGet(() -> saveAi(org, defaultAiFlags()));
  }

  @Transactional
  public Map<String, Object> saveAi(String org, Map<String, Object> flags) {
    AiSettingsEntity entity =
        aiSettingsRepository.findByOrganizationId(org).orElseGet(AiSettingsEntity::new);
    entity.setOrganizationId(org);
    entity.setPayload(flags == null ? new LinkedHashMap<>() : flags);
    entity.setUpdatedAt(Instant.now());
    return aiSettingsRepository.save(entity).getPayload();
  }

  @Transactional
  public Map<String, Object> getRoleDashboard(String org, String role) {
    return roleDashboardRepository
        .findByOrganizationIdAndRoleCode(org, role)
        .map(RoleDashboardEntity::getPayload)
        .orElseGet(
            () ->
                saveRoleDashboard(
                    org,
                    role,
                    Map.of(
                        "role",
                        role,
                        "widgets",
                        List.of("summary", "notices", "quickActions"),
                        "reports",
                        List.of())));
  }

  @Transactional
  public Map<String, Object> saveRoleDashboard(String org, String role, Map<String, Object> body) {
    RoleDashboardEntity entity =
        roleDashboardRepository
            .findByOrganizationIdAndRoleCode(org, role)
            .orElseGet(RoleDashboardEntity::new);
    entity.setOrganizationId(org);
    entity.setRoleCode(role);
    entity.setPayload(body == null ? new LinkedHashMap<>() : body);
    entity.setUpdatedAt(Instant.now());
    return roleDashboardRepository.save(entity).getPayload();
  }

  private DesignTheme toTheme(DesignThemeEntity entity) {
    DesignTheme theme = objectMapper.convertValue(entity.getPayload(), DesignTheme.class);
    theme.setOrganizationId(entity.getOrganizationId());
    theme.setBranchId(entity.getBranchId());
    theme.setVersion(entity.getVersion());
    theme.setStatus(entity.getStatus());
    mergeThemeDefaults(theme);
    return theme;
  }

  /** Backfill new Design Studio keys onto older stored payloads. */
  private void mergeThemeDefaults(DesignTheme theme) {
    DesignTheme defaults = DesignTheme.platformDefault(theme.getOrganizationId(), theme.getBranchId());
    if (theme.getBranding() == null) {
      theme.setBranding(new LinkedHashMap<>());
    }
    defaults.getBranding().forEach((k, v) -> theme.getBranding().putIfAbsent(k, v));
    if (theme.getColors() == null) {
      theme.setColors(new LinkedHashMap<>());
    }
    defaults.getColors().forEach((k, v) -> theme.getColors().putIfAbsent(k, v));
    if (theme.getTypography() == null) {
      theme.setTypography(new LinkedHashMap<>());
    }
    defaults.getTypography().forEach((k, v) -> theme.getTypography().putIfAbsent(k, v));
    if (theme.getLoginScreen() == null) {
      theme.setLoginScreen(new LinkedHashMap<>());
    }
    defaults.getLoginScreen().forEach((k, v) -> theme.getLoginScreen().putIfAbsent(k, v));
  }

  private ModuleSettings toModule(ModuleSettingsEntity entity) {
    ModuleSettings ms =
        new ModuleSettings(entity.getModuleKey(), entity.getOrganizationId(), entity.getBranchId());
    ms.setAcademicSessionId(entity.getAcademicSessionId());
    ms.setSettings(entity.getPayload());
    return ms;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(Object value) {
    return objectMapper.convertValue(value, Map.class);
  }

  private Map<String, Object> defaultAiFlags() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("aiAttendance", true);
    m.put("aiDeviceAdapters", true);
    m.put("aiReportCard", false);
    m.put("aiHomework", false);
    m.put("aiLessonPlan", false);
    m.put("aiTimetable", false);
    m.put("aiParentSummary", false);
    m.put("aiTeacherAssistant", false);
    m.put("aiFeePrediction", false);
    m.put("aiChatbot", false);
    return m;
  }

  private List<MenuNode> defaultMenus() {
    List<MenuNode> nodes = new ArrayList<>();
    nodes.add(menu("dashboard", "Dashboard", "/dashboard", 1, List.of(), List.of()));
    nodes.add(
        menu(
            "admin-config",
            "Admin Settings",
            "/admin",
            90,
            List.of("PRINCIPAL", "ADMIN"),
            List.of("FEATURE_ADMIN_CONFIG")));
    nodes.add(
        menu(
            "design-studio",
            "Design Studio",
            "/admin/design-studio",
            91,
            List.of("ADMIN"),
            List.of("FEATURE_WHITE_LABEL")));
    return nodes;
  }

  private MenuNode menu(
      String id, String label, String route, int order, List<String> roles, List<String> flags) {
    MenuNode n = new MenuNode();
    n.setId(id);
    n.setLabel(label);
    n.setRoute(route);
    n.setOrder(order);
    n.setRoles(new ArrayList<>(roles));
    n.setRequiredFeatureFlags(new ArrayList<>(flags));
    return n;
  }
}
