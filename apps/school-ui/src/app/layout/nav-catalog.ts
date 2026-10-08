export interface NavItem {
  path: string;
  label: string;
  feature?: string;
  roles?: string[];
  permissions?: string[];
}

export interface NavGroup {
  id: string;
  label: string;
  icon: string;
  items: NavItem[];
}
export const TOP_NAV: NavGroup[] = [
    {
      id: 'students',
      label: 'Students',
      icon: 'users',
      items: [
        { path: '/admin/leads', label: 'Leads / Inquiry', feature: 'FEATURE_ADMISSION' },
        { path: '/admin/admission', label: 'Admission', feature: 'FEATURE_ADMISSION' },
        {
          path: '/admin/student-directory',
          label: 'Student Directory',
          feature: 'FEATURE_STUDENT_MASTER',
        },
        {
          path: '/admin/udise-export',
          label: 'UDISE+ export',
          feature: 'FEATURE_STUDENT_MASTER',
        },
        { path: '/admin/students', label: 'Student Master', feature: 'FEATURE_STUDENT_MASTER' },
        { path: '/admin/leave', label: 'Leave', feature: 'FEATURE_STUDENT_MASTER' },
        { path: '/admin/app-users', label: 'App users', feature: 'FEATURE_STUDENT_MASTER' },
        { path: '/admin/gate-pass', label: 'Gate pass', feature: 'FEATURE_STUDENT_MASTER' },
        {
          path: '/admin/import',
          label: 'Import Workbench',
          feature: 'FEATURE_STUDENT_MASTER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/lifecycle',
          label: 'Promotions & Transfers',
          feature: 'FEATURE_ACADEMIC_LIFECYCLE',
        },
      ],
    },
    {
      id: 'staff',
      label: 'Staff',
      icon: 'team',
      items: [
        {
          path: '/admin/staff-directory',
          label: 'Staff List',
          feature: 'FEATURE_STAFF_MASTER',
        },
        {
          path: '/admin/staff-directory/add',
          label: 'Add Staff',
          feature: 'FEATURE_STAFF_MASTER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
          permissions: ['MANAGE_STAFF'],
        },
        {
          path: '/admin/staff-directory/invite',
          label: 'Invite login',
          feature: 'FEATURE_STAFF_MASTER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
          permissions: ['MANAGE_STAFF'],
        },
        {
          path: '/admin/staff-access',
          label: 'Staff access',
          feature: 'FEATURE_STAFF_MASTER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
          permissions: ['MANAGE_STAFF'],
        },
        { path: '/admin/payroll', label: 'Payroll', feature: 'FEATURE_PAYROLL', permissions: ['MANAGE_FINANCE'] },
      ],
    },
    {
      id: 'academics',
      label: 'Academics',
      icon: 'book',
      items: [
        {
          path: '/admin/academic',
          label: 'Academic Structure',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/timetable',
          label: 'Timetable',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        { path: '/admin/attendance', label: 'Attendance', feature: 'FEATURE_ATTENDANCE' },
        { path: '/admin/exam', label: 'Exam / Gradebook', feature: 'FEATURE_EXAM' },
        { path: '/admin/lms', label: 'LMS', feature: 'FEATURE_LMS' },
        { path: '/admin/classroom', label: 'Classroom', feature: 'FEATURE_LMS' },
        {
          path: '/admin/devices',
          label: 'Device Adapters',
          feature: 'FEATURE_ATTENDANCE',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/biometric',
          label: 'Biometric Attendance',
          feature: 'FEATURE_ATTENDANCE',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/offline',
          label: 'Offline Mode',
          feature: 'FEATURE_OFFLINE_MODE',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
      ],
    },
    {
      id: 'finance',
      label: 'Finance',
      icon: 'wallet',
      items: [
        { path: '/admin/fee', label: 'Fee Collection', feature: 'FEATURE_FEE', permissions: ['MANAGE_FINANCE'] },
        { path: '/admin/fee-desk', label: 'Fee desk', feature: 'FEATURE_FEE', permissions: ['MANAGE_FINANCE'] },
        { path: '/admin/finance', label: 'Finance / Payments', feature: 'FEATURE_FEE', permissions: ['MANAGE_FINANCE'] },
        {
          path: '/admin/income-expense',
          label: 'Income & Expense',
          feature: 'FEATURE_FEE',
        },
      ],
    },
    {
      id: 'operations',
      label: 'Operations',
      icon: 'building',
      items: [
        { path: '/admin/library', label: 'Library', feature: 'FEATURE_LIBRARY' },
        { path: '/admin/hostel', label: 'Hostel', feature: 'FEATURE_HOSTEL' },
        { path: '/admin/transport', label: 'Transport', feature: 'FEATURE_TRANSPORT' },
        { path: '/admin/addons', label: 'Add-ons' },
        { path: '/admin/ops', label: 'Ops Depth', feature: 'FEATURE_OPS_DEPTH' },
      ],
    },
    {
      id: 'comms',
      label: 'Comms',
      icon: 'chat',
      items: [
        { path: '/admin/comms', label: 'Comms Hub', feature: 'FEATURE_COMMS_HUB', roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'] },
        { path: '/admin/notifications', label: 'Notifications' },
      ],
    },
  ];

export const SIDE_NAV: NavGroup[] = [
    {
      id: 'home',
      label: 'Home',
      icon: 'home',
      items: [{ path: '/admin/dashboard', label: 'Dashboard' }],
    },
    {
      id: 'reports',
      label: 'Reports',
      icon: 'chart',
      items: [
        { path: '/admin/reports-hub', label: 'Reports Hub' },
        {
          path: '/admin/reports',
          label: 'Report Designer',
          feature: 'FEATURE_REPORT_BUILDER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/reports?template=id_card',
          label: 'ID cards',
          feature: 'FEATURE_REPORT_BUILDER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/reports?template=admit_card',
          label: 'Admit cards',
          feature: 'FEATURE_REPORT_BUILDER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/reports?template=transfer_certificate',
          label: 'Transfer certificates',
          feature: 'FEATURE_REPORT_BUILDER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
      ],
    },
    {
      id: 'admin',
      label: 'Admin',
      icon: 'settings',
      items: [
        {
          path: '/admin/branches',
          label: 'Campuses',
          feature: 'FEATURE_MULTI_BRANCH',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL'],
        },
        {
          path: '/admin/subscription',
          label: 'Subscription',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/modules',
          label: 'Module Settings',
          feature: 'FEATURE_ADMIN_CONFIG',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/forms',
          label: 'Form Builder',
          feature: 'FEATURE_FORM_BUILDER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/workflows',
          label: 'Workflows',
          feature: 'FEATURE_WORKFLOW_BUILDER',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/rules',
          label: 'Automation Rules',
          feature: 'FEATURE_RULE_ENGINE',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/menus',
          label: 'Menu Builder',
          feature: 'FEATURE_ADMIN_CONFIG',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/localization',
          label: 'Localization',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/ai',
          label: 'AI Config',
          feature: 'FEATURE_AI',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/audit',
          label: 'Config Audit',
          feature: 'FEATURE_AUDIT_LOGS',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        { path: '/parent', label: 'Parent App', feature: 'FEATURE_PARENT_APP' },
        { path: '/teacher', label: 'Teacher App', feature: 'FEATURE_TEACHER_APP' },
      ],
    },
    {
      id: 'design',
      label: 'School Design',
      icon: 'palette',
      items: [
        {
          path: '/admin/design-studio',
          label: 'Design Studio',
          feature: 'FEATURE_WHITE_LABEL',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
        {
          path: '/admin/website-cms',
          label: 'Website CMS',
          feature: 'FEATURE_WEBSITE_CMS',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
      ],
    },
    {
      id: 'compliance',
      label: 'Compliance',
      icon: 'shield',
      items: [
        {
          path: '/admin/compliance',
          label: 'Board Compliance',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/readiness',
          label: 'Data Readiness',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/infrastructure',
          label: 'Infrastructure',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/documents',
          label: 'Documents Vault',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/campaigns',
          label: 'Campaign Workspace',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/import',
          label: 'Import Center',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/disclosure',
          label: 'Disclosure Preview',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/profile',
          label: 'School profile',
          feature: 'FEATURE_CBSE_COMPLIANCE',
        },
        {
          path: '/admin/compliance/platform-templates',
          label: 'Platform Templates',
          feature: 'FEATURE_CBSE_COMPLIANCE',
          roles: ['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN'],
        },
      ],
    },
    {
      id: 'support',
      label: 'Support',
      icon: 'help',
      items: [
        { path: '/admin/support/new', label: 'Report issue' },
        { path: '/admin/support/tickets', label: 'My tickets' },
      ],
    },
  ];
