import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EntitlementsService } from '../../core/entitlements.service';
import { PlanStatus, planStatus } from './settings-hub.util';

interface SettingsCard {
  title: string;
  detail: string;
  path: string;
  feature?: string;
  group: string;
}

interface SetupRow {
  label: string;
  feature?: string;
  path: string;
}

@Component({
  selector: 'sf-settings-hub',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './settings-hub.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
  styles: [
    `
      a.panel { display: block; color: inherit; text-decoration: none; }
      table { width: 100%; border-collapse: collapse; }
      th, td { text-align: left; padding: 0.45rem 0.2rem; border-bottom: 1px solid color-mix(in srgb, var(--sf-menu) 18%, transparent); }
    `,
  ],
})
export class SettingsHubComponent implements OnInit {
  private readonly entitlements = inject(EntitlementsService);

  query = '';
  loadState: 'ready' | 'unavailable' | 'loading' = 'loading';
  flags: Record<string, boolean> | null = null;

  readonly checklist: SetupRow[] = [
    { label: 'Students', feature: 'FEATURE_STUDENT_MASTER', path: '/admin/students' },
    { label: 'Admission number and class fee', path: '/admin/settings/setup' },
    { label: 'Admissions', feature: 'FEATURE_ADMISSION', path: '/admin/admission' },
    { label: 'Academic structure', path: '/admin/academic' },
    { label: 'Fees', feature: 'FEATURE_FEE', path: '/admin/fee' },
    { label: 'Attendance', feature: 'FEATURE_ATTENDANCE', path: '/admin/attendance' },
    { label: 'Payroll', feature: 'FEATURE_PAYROLL', path: '/admin/payroll' },
    { label: 'ID cards and certificates', feature: 'FEATURE_REPORT_BUILDER', path: '/admin/reports' },
    { label: 'Forms', feature: 'FEATURE_FORM_BUILDER', path: '/admin/forms' },
    { label: 'Workflows', feature: 'FEATURE_WORKFLOW_BUILDER', path: '/admin/workflows' },
  ];

  readonly cards: SettingsCard[] = [
    { group: 'Institution', title: 'Campuses', detail: 'Branches under this school.', path: '/admin/branches', feature: 'FEATURE_MULTI_BRANCH' },
    { group: 'Institution', title: 'Localization', detail: 'Language, dates, and regional formats.', path: '/admin/localization' },
    { group: 'Institution', title: 'School profile', detail: 'Board profile used by compliance.', path: '/admin/compliance/profile', feature: 'FEATURE_CBSE_COMPLIANCE' },
    { group: 'Institution', title: 'Branding', detail: 'Logo and theme for this school only.', path: '/admin/design-studio', feature: 'FEATURE_WHITE_LABEL' },
    { group: 'Modules', title: 'Plan modules', detail: 'What this subscription includes. Turning a module off here is a plan change, not a data delete.', path: '/admin/subscription' },
    { group: 'Modules', title: 'Module settings', detail: 'School-specific keys for admission, fees, payroll, and the other modules. Saving does not remove historical records.', path: '/admin/modules', feature: 'FEATURE_ADMIN_CONFIG' },
    { group: 'Academics', title: 'Classes and sessions', detail: 'Sessions, classes, and sections.', path: '/admin/academic' },
    { group: 'Academics', title: 'Calendar', detail: 'Working days and holidays.', path: '/admin/school-calendar' },
    { group: 'Students', title: 'Admission numbers and class fees', detail: 'Number format used for the next student, and a tuition structure for one class.', path: '/admin/settings/setup' },
    { group: 'Students', title: 'Admission number and student fields', detail: 'Form layout and extra fields for this school.', path: '/admin/forms', feature: 'FEATURE_FORM_BUILDER' },
    { group: 'Fees', title: 'Fee collection', detail: 'Structures, receipts, and outstanding fees.', path: '/admin/fee', feature: 'FEATURE_FEE' },
    { group: 'People', title: 'Payroll', detail: 'Salary components already configured for this school.', path: '/admin/payroll', feature: 'FEATURE_PAYROLL' },
    { group: 'People', title: 'Staff access', detail: 'Who can sign in and which actions they can take.', path: '/admin/staff-access', feature: 'FEATURE_STAFF_MASTER' },
    { group: 'Documents', title: 'Document designer', detail: 'One designer for ID cards, certificates, receipts, and report cards.', path: '/admin/reports', feature: 'FEATURE_REPORT_BUILDER' },
    { group: 'Documents', title: 'Workflows', detail: 'Admission, leave, and other approval stages.', path: '/admin/workflows', feature: 'FEATURE_WORKFLOW_BUILDER' },
    { group: 'System', title: 'Menus', detail: 'Which menus this school shows.', path: '/admin/menus', feature: 'FEATURE_ADMIN_CONFIG' },
    { group: 'System', title: 'Audit', detail: 'Who changed configuration.', path: '/admin/audit', feature: 'FEATURE_AUDIT_LOGS' },
  ];

  ngOnInit(): void {
    const current = this.entitlements.current();
    if (current?.loadState === 'ready' && current.featureFlags) {
      this.flags = current.featureFlags;
      this.loadState = 'ready';
      return;
    }
    this.entitlements.load().subscribe((loaded) => {
      this.flags = loaded.featureFlags ?? null;
      this.loadState = loaded.loadState === 'ready' ? 'ready' : 'unavailable';
    });
  }

  groups(): string[] {
    return [...new Set(this.visibleCards().map((card) => card.group))];
  }

  cardsIn(group: string): SettingsCard[] {
    return this.visibleCards().filter((card) => card.group === group);
  }

  statusOf(feature?: string): PlanStatus {
    return planStatus(this.flags, feature);
  }

  statusLabel(feature?: string): string {
    const status = this.statusOf(feature);
    if (status === 'active') {
      return 'Active';
    }
    if (status === 'not-on-plan') {
      return 'Not on this plan';
    }
    return 'Plan not loaded';
  }

  private visibleCards(): SettingsCard[] {
    const q = this.query.trim().toLowerCase();
    return this.cards.filter((card) => {
      if (q && !`${card.group} ${card.title} ${card.detail}`.toLowerCase().includes(q)) {
        return false;
      }
      return this.statusOf(card.feature) !== 'not-on-plan';
    });
  }
}
