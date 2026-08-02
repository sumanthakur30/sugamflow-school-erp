import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell.component';
import { authGuard } from './core/auth.guard';
import { featureGuard } from './core/feature.guard';
import { UnknownRouteComponent } from './core/unknown-route.component';
import { unknownRouteGuard } from './core/unknown-route.guard';

const PLATFORM_ADMIN_ROLES = ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'];
const CAMPUS_ADMIN_ROLES = [...PLATFORM_ADMIN_ROLES, 'PRINCIPAL'];
const STAFF_DASHBOARD_ROLES = [
  ...CAMPUS_ADMIN_ROLES,
  'SHOP_EMPLOYEE',
  'ACCOUNTANT',
  'ACCOUNTS',
  'FINANCE',
  'RECEPTION',
  'RECEPTIONIST',
  'LIBRARIAN',
  'TEACHER',
  'STAFF',
];

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'onboarding/set-password',
    loadComponent: () =>
      import('./features/onboarding/set-password.component').then((m) => m.SetPasswordComponent),
  },
  {
    path: 'verify/document/:token',
    loadComponent: () =>
      import('./features/verify/document-verify.component').then((m) => m.DocumentVerifyComponent),
  },
  {
    path: 'parent',
    canActivate: [authGuard, featureGuard],
    loadComponent: () =>
      import('./features/portals/portal-shell.component').then((m) => m.PortalShellComponent),
    data: {
      portalKey: 'parent',
      feature: 'FEATURE_PARENT_APP',
      roles: ['PARENT', 'GUARDIAN', 'STUDENT', ...CAMPUS_ADMIN_ROLES],
    },
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
      {
        path: 'report-cards',
        loadComponent: () =>
          import('./features/portals/parent-report-cards.component').then(
            (m) => m.ParentReportCardsComponent,
          ),
      },
      {
        path: 'homework',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_LMS' },
        loadComponent: () =>
          import('./features/portals/parent-homework.component').then(
            (m) => m.ParentHomeworkComponent,
          ),
      },
    ],
  },
  {
    path: 'teacher',
    canActivate: [authGuard, featureGuard],
    loadComponent: () =>
      import('./features/portals/portal-shell.component').then((m) => m.PortalShellComponent),
    data: {
      portalKey: 'teacher',
      feature: 'FEATURE_TEACHER_APP',
      roles: ['TEACHER', ...CAMPUS_ADMIN_ROLES],
    },
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
          import('./features/portals/teacher-attendance.component').then(
            (m) => m.TeacherAttendanceComponent,
          ),
      },
      {
        path: 'gradebook',
        loadComponent: () =>
          import('./features/portals/teacher-gradebook.component').then(
            (m) => m.TeacherGradebookComponent,
          ),
      },
      {
        path: 'report-cards',
        loadComponent: () =>
          import('./features/portals/teacher-report-cards.component').then(
            (m) => m.TeacherReportCardsComponent,
          ),
      },
      {
        path: 'homework',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_LMS' },
        loadComponent: () =>
          import('./features/portals/teacher-homework.component').then(
            (m) => m.TeacherHomeworkComponent,
          ),
      },
    ],
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'admin/dashboard' },
      {
        path: 'admin/dashboard',
        canActivate: [featureGuard],
        data: { roles: STAFF_DASHBOARD_ROLES },
        loadComponent: () =>
          import('./features/dashboard/role-dashboard.component').then(
            (m) => m.RoleDashboardComponent,
          ),
      },
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
        path: 'admin/income-expense',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_FEE' },
        loadComponent: () =>
          import('./features/income-expense/income-expense.component').then(
            (m) => m.IncomeExpenseComponent,
          ),
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
        data: { feature: 'FEATURE_ATTENDANCE', roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/device-adapters/device-adapters.component').then(
            (m) => m.DeviceAdaptersComponent,
          ),
      },
      {
        path: 'admin/offline',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_OFFLINE_MODE', roles: CAMPUS_ADMIN_ROLES },
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
        path: 'admin/lms',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_LMS' },
        loadComponent: () => import('./features/lms/lms.component').then((m) => m.LmsComponent),
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
        path: 'admin/student-directory',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STUDENT_MASTER' },
        loadComponent: () =>
          import('./features/directory/student-directory.component').then(
            (m) => m.StudentDirectoryComponent,
          ),
      },
      {
        path: 'admin/students/:id/360',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STUDENT_MASTER' },
        loadComponent: () =>
          import('./features/students/student-360.component').then((m) => m.Student360Component),
      },
      {
        path: 'admin/students',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STUDENT_MASTER' },
        loadComponent: () =>
          import('./features/students/students.component').then((m) => m.StudentsComponent),
      },
      {
        path: 'admin/import',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STUDENT_MASTER', roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/import/import-workbench.component').then(
            (m) => m.ImportWorkbenchComponent,
          ),
      },
      {
        path: 'admin/comms',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_COMMS_HUB', roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/comms/comms-hub.component').then((m) => m.CommsHubComponent),
      },
      {
        path: 'admin/staff-directory/add',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STAFF_MASTER', roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/directory/staff-directory.component').then(
            (m) => m.StaffDirectoryComponent,
          ),
      },
      {
        path: 'admin/staff-directory/invite',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STAFF_MASTER', roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/directory/staff-directory.component').then(
            (m) => m.StaffDirectoryComponent,
          ),
      },
      {
        path: 'admin/staff-directory',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_STAFF_MASTER' },
        loadComponent: () =>
          import('./features/directory/staff-directory.component').then(
            (m) => m.StaffDirectoryComponent,
          ),
      },
      {
        path: 'admin/lifecycle',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ACADEMIC_LIFECYCLE' },
        loadComponent: () =>
          import('./features/lifecycle/lifecycle.component').then((m) => m.LifecycleComponent),
      },
      {
        path: 'admin/academic',
        canActivate: [featureGuard],
        data: { roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/academic/academic.component').then((m) => m.AcademicComponent),
      },
      {
        path: 'admin/timetable',
        canActivate: [featureGuard],
        data: { roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/timetable/timetable.component').then((m) => m.TimetableComponent),
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
        data: { feature: 'FEATURE_MULTI_BRANCH', roles: CAMPUS_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/branches/branches.component').then((m) => m.BranchesComponent),
      },
      {
        path: 'admin/design-studio',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_WHITE_LABEL', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/design-studio/design-studio.component').then(
            (m) => m.DesignStudioComponent,
          ),
      },
      {
        path: 'admin/website-cms',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_WEBSITE_CMS', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/website-cms/website-cms.component').then((m) => m.WebsiteCmsComponent),
      },
      {
        path: 'admin/subscription',
        canActivate: [featureGuard],
        data: { roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/subscription/subscription.component').then(
            (m) => m.SubscriptionComponent,
          ),
      },
      {
        path: 'admin/modules',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_ADMIN_CONFIG', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/module-settings/module-settings.component').then(
            (m) => m.ModuleSettingsComponent,
          ),
      },
      {
        path: 'admin/forms',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_FORM_BUILDER', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/form-builder/form-builder.component').then(
            (m) => m.FormBuilderComponent,
          ),
      },
      {
        path: 'admin/workflows',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_WORKFLOW_BUILDER', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/workflow-builder/workflow-builder.component').then(
            (m) => m.WorkflowBuilderComponent,
          ),
      },
      {
        path: 'admin/rules',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_RULE_ENGINE', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/rule-engine/rule-engine.component').then((m) => m.RuleEngineComponent),
      },
      {
        path: 'admin/reports-hub',
        loadComponent: () =>
          import('./features/reports-hub/reports-hub.component').then(
            (m) => m.ReportsHubComponent,
          ),
      },
      {
        path: 'admin/reports',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_REPORT_BUILDER', roles: CAMPUS_ADMIN_ROLES },
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
        data: { feature: 'FEATURE_ADMIN_CONFIG', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/menu-builder/menu-builder.component').then(
            (m) => m.MenuBuilderComponent,
          ),
      },
      {
        path: 'admin/localization',
        canActivate: [featureGuard],
        data: { roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/localization/localization.component').then(
            (m) => m.LocalizationComponent,
          ),
      },
      {
        path: 'admin/ai',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_AI', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/ai-config/ai-config.component').then((m) => m.AiConfigComponent),
      },
      {
        path: 'admin/audit',
        canActivate: [featureGuard],
        data: { feature: 'FEATURE_AUDIT_LOGS', roles: PLATFORM_ADMIN_ROLES },
        loadComponent: () =>
          import('./features/audit/audit.component').then((m) => m.AuditComponent),
      },
    ],
  },
  {
    path: '**',
    canActivate: [unknownRouteGuard],
    component: UnknownRouteComponent,
  },
];
