import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell.component';
import { authGuard } from './core/auth.guard';
import { featureGuard } from './core/feature.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'parent',
    canActivate: [authGuard, featureGuard],
    loadComponent: () =>
      import('./features/portals/portal-shell.component').then((m) => m.PortalShellComponent),
    data: { portalKey: 'parent', feature: 'FEATURE_PARENT_APP' },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/portals/portal-home.component').then((m) => m.PortalHomeComponent),
      },
      {
        path: 'attendance',
        loadComponent: () =>
          import('./features/portals/portal-section.component').then(
            (m) => m.PortalSectionComponent,
          ),
        data: { sectionKey: 'attendance' },
      },
      {
        path: 'fees',
        loadComponent: () =>
          import('./features/portals/portal-section.component').then(
            (m) => m.PortalSectionComponent,
          ),
        data: { sectionKey: 'fees' },
      },
      {
        path: 'exams',
        loadComponent: () =>
          import('./features/portals/portal-section.component').then(
            (m) => m.PortalSectionComponent,
          ),
        data: { sectionKey: 'exams' },
      },
    ],
  },
  {
    path: 'teacher',
    canActivate: [authGuard, featureGuard],
    loadComponent: () =>
      import('./features/portals/portal-shell.component').then((m) => m.PortalShellComponent),
    data: { portalKey: 'teacher', feature: 'FEATURE_TEACHER_APP' },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/portals/portal-home.component').then((m) => m.PortalHomeComponent),
      },
      {
        path: 'students',
        loadComponent: () =>
          import('./features/portals/portal-section.component').then(
            (m) => m.PortalSectionComponent,
          ),
        data: { sectionKey: 'students' },
      },
      {
        path: 'attendance',
        loadComponent: () =>
          import('./features/portals/portal-section.component').then(
            (m) => m.PortalSectionComponent,
          ),
        data: { sectionKey: 'attendance' },
      },
      {
        path: 'gradebook',
        loadComponent: () =>
          import('./features/portals/portal-section.component').then(
            (m) => m.PortalSectionComponent,
          ),
        data: { sectionKey: 'gradebook' },
      },
    ],
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'admin/admission' },
      {
        path: 'admin/admission',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ADMISSION' },
        loadComponent: () =>
          import('./features/admission/admission.component').then((m) => m.AdmissionComponent),
      },
      {
        path: 'admin/fee',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_FEE' },
        loadComponent: () => import('./features/fee/fee.component').then((m) => m.FeeComponent),
      },
      {
        path: 'admin/finance',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_FEE' },
        loadComponent: () =>
          import('./features/finance/finance.component').then((m) => m.FinanceComponent),
      },
      {
        path: 'admin/attendance',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ATTENDANCE' },
        loadComponent: () =>
          import('./features/attendance/attendance.component').then((m) => m.AttendanceComponent),
      },
      {
        path: 'admin/devices',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ATTENDANCE' },
        loadComponent: () =>
          import('./features/device-adapters/device-adapters.component').then(
            (m) => m.DeviceAdaptersComponent,
          ),
      },
      {
        path: 'admin/offline',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_OFFLINE_MODE' },
        loadComponent: () =>
          import('./features/offline/offline.component').then((m) => m.OfflineComponent),
      },
      {
        path: 'admin/exam',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_EXAM' },
        loadComponent: () =>
          import('./features/exam/exam.component').then((m) => m.ExamComponent),
      },
      {
        path: 'admin/library',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_LIBRARY' },
        loadComponent: () =>
          import('./features/library/library.component').then((m) => m.LibraryComponent),
      },
      {
        path: 'admin/hostel',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_HOSTEL' },
        loadComponent: () =>
          import('./features/hostel/hostel.component').then((m) => m.HostelComponent),
      },
      {
        path: 'admin/transport',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_TRANSPORT' },
        loadComponent: () =>
          import('./features/transport/transport.component').then((m) => m.TransportComponent),
      },
      {
        path: 'admin/payroll',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_PAYROLL' },
        loadComponent: () =>
          import('./features/payroll/payroll.component').then((m) => m.PayrollComponent),
      },
      {
        path: 'admin/students',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STUDENT_MASTER' },
        loadComponent: () =>
          import('./features/students/students.component').then((m) => m.StudentsComponent),
      },
      {
        path: 'admin/lifecycle',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ACADEMIC_LIFECYCLE' },
        loadComponent: () =>
          import('./features/lifecycle/lifecycle.component').then((m) => m.LifecycleComponent),
      },
      {
        path: 'admin/ops',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_OPS_DEPTH' },
        loadComponent: () => import('./features/ops/ops.component').then((m) => m.OpsComponent),
      },
      {
        path: 'admin/branches',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_MULTI_BRANCH' },
        loadComponent: () =>
          import('./features/branches/branches.component').then((m) => m.BranchesComponent),
      },
      {
        path: 'admin/design-studio',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_WHITE_LABEL' },
        loadComponent: () =>
          import('./features/design-studio/design-studio.component').then(
            (m) => m.DesignStudioComponent,
          ),
      },
      {
        path: 'admin/subscription',
        loadComponent: () =>
          import('./features/subscription/subscription.component').then(
            (m) => m.SubscriptionComponent,
          ),
      },
      {
        path: 'admin/modules',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ADMIN_CONFIG' },
        loadComponent: () =>
          import('./features/module-settings/module-settings.component').then(
            (m) => m.ModuleSettingsComponent,
          ),
      },
      {
        path: 'admin/forms',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_FORM_BUILDER' },
        loadComponent: () =>
          import('./features/form-builder/form-builder.component').then(
            (m) => m.FormBuilderComponent,
          ),
      },
      {
        path: 'admin/workflows',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_WORKFLOW_BUILDER' },
        loadComponent: () =>
          import('./features/workflow-builder/workflow-builder.component').then(
            (m) => m.WorkflowBuilderComponent,
          ),
      },
      {
        path: 'admin/rules',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_RULE_ENGINE' },
        loadComponent: () =>
          import('./features/rule-engine/rule-engine.component').then((m) => m.RuleEngineComponent),
      },
      {
        path: 'admin/reports',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_REPORT_BUILDER' },
        loadComponent: () =>
          import('./features/report-builder/report-builder.component').then(
            (m) => m.ReportBuilderComponent,
          ),
      },
      {
        path: 'admin/notifications',
        loadComponent: () =>
          import('./features/notifications/notifications.component').then(
            (m) => m.NotificationsComponent,
          ),
      },
      {
        path: 'admin/menus',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ADMIN_CONFIG' },
        loadComponent: () =>
          import('./features/menu-builder/menu-builder.component').then(
            (m) => m.MenuBuilderComponent,
          ),
      },
      {
        path: 'admin/localization',
        loadComponent: () =>
          import('./features/localization/localization.component').then(
            (m) => m.LocalizationComponent,
          ),
      },
      {
        path: 'admin/ai',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_AI' },
        loadComponent: () =>
          import('./features/ai-config/ai-config.component').then((m) => m.AiConfigComponent),
      },
      {
        path: 'admin/audit',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_AUDIT_LOGS' },
        loadComponent: () =>
          import('./features/audit/audit.component').then((m) => m.AuditComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
