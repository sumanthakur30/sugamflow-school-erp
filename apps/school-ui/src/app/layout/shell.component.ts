import { Component, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthSessionService } from '../core/auth-session.service';
import { EntitlementsService } from '../core/entitlements.service';
import { OfflineQueueService } from '../core/offline-queue.service';
import { ThemeService } from '../core/theme.service';
import { BranchSwitcherComponent } from '../features/branches/branch-switcher.component';

export interface NavItem {
  path: string;
  label: string;
  feature?: string;
  /** Empty = any staff role except pure PARENT/STUDENT portal personas */
  roles?: string[];
}

@Component({
  selector: 'sf-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AsyncPipe, BranchSwitcherComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly entitlements = inject(EntitlementsService);
  readonly themeService = inject(ThemeService);
  readonly offlineQueue = inject(OfflineQueueService);

  readonly theme$ = this.themeService.theme$;
  readonly online$ = this.offlineQueue.isOnline$;
  readonly offlineItems$ = this.offlineQueue.items$;

  readonly navCatalog: NavItem[] = [
    { path: '/admin/admission', label: 'Admission', feature: 'FEATURE_ADMISSION' },
    { path: '/admin/students', label: 'Students', feature: 'FEATURE_STUDENT_MASTER' },
    {
      path: '/admin/lifecycle',
      label: 'Academic Lifecycle',
      feature: 'FEATURE_ACADEMIC_LIFECYCLE',
    },
    { path: '/admin/ops', label: 'Ops Depth', feature: 'FEATURE_OPS_DEPTH' },
    { path: '/admin/fee', label: 'Fee Collection', feature: 'FEATURE_FEE' },
    { path: '/admin/finance', label: 'Finance', feature: 'FEATURE_FEE' },
    { path: '/admin/attendance', label: 'Attendance', feature: 'FEATURE_ATTENDANCE' },
    { path: '/admin/devices', label: 'Device Adapters', feature: 'FEATURE_ATTENDANCE' },
    { path: '/admin/offline', label: 'Offline Mode', feature: 'FEATURE_OFFLINE_MODE' },
    { path: '/admin/exam', label: 'Exam / Gradebook', feature: 'FEATURE_EXAM' },
    { path: '/admin/library', label: 'Library', feature: 'FEATURE_LIBRARY' },
    { path: '/admin/hostel', label: 'Hostel', feature: 'FEATURE_HOSTEL' },
    { path: '/admin/transport', label: 'Transport', feature: 'FEATURE_TRANSPORT' },
    { path: '/admin/payroll', label: 'Payroll', feature: 'FEATURE_PAYROLL' },
    { path: '/admin/branches', label: 'Campuses', feature: 'FEATURE_MULTI_BRANCH' },
    { path: '/admin/design-studio', label: 'Design Studio', feature: 'FEATURE_WHITE_LABEL' },
    { path: '/admin/subscription', label: 'Subscription' },
    { path: '/admin/modules', label: 'Module Settings', feature: 'FEATURE_ADMIN_CONFIG' },
    { path: '/admin/forms', label: 'Form Builder', feature: 'FEATURE_FORM_BUILDER' },
    { path: '/admin/workflows', label: 'Workflows', feature: 'FEATURE_WORKFLOW_BUILDER' },
    { path: '/admin/rules', label: 'Rule Engine', feature: 'FEATURE_RULE_ENGINE' },
    { path: '/admin/reports', label: 'Report Designer', feature: 'FEATURE_REPORT_BUILDER' },
    { path: '/admin/notifications', label: 'Notifications' },
    { path: '/admin/menus', label: 'Menu Builder', feature: 'FEATURE_ADMIN_CONFIG' },
    { path: '/admin/localization', label: 'Localization' },
    { path: '/admin/ai', label: 'AI Config', feature: 'FEATURE_AI' },
    { path: '/admin/audit', label: 'Config Audit', feature: 'FEATURE_AUDIT_LOGS' },
    { path: '/parent', label: 'Parent App', feature: 'FEATURE_PARENT_APP' },
    { path: '/teacher', label: 'Teacher App', feature: 'FEATURE_TEACHER_APP' },
  ];

  nav: NavItem[] = [...this.navCatalog];

  ngOnInit(): void {
    this.themeService.loadAuthenticated().subscribe();
    this.entitlements.load().subscribe(() => this.refreshNav());
  }

  refreshNav(): void {
    const role = (this.auth.getRole() || '').toUpperCase();
    const portalOnly = role === 'PARENT' || role === 'STUDENT';
    const teacherPortal = role === 'TEACHER';

    this.nav = this.navCatalog.filter((item) => {
      if (portalOnly) {
        return item.path === '/parent';
      }
      if (teacherPortal && item.path.startsWith('/admin')) {
        // Teachers use teacher portal; still allow attendance/exam if needed later
        return item.path === '/teacher';
      }
      if (!this.entitlements.isEnabled(item.feature)) {
        return false;
      }
      if (item.roles?.length) {
        const elevated = role === 'SHOP_OWNER' || role === 'SUPER_ADMIN' || role === 'ADMIN';
        if (!elevated && !item.roles.map((r) => r.toUpperCase()).includes(role)) {
          return false;
        }
      }
      return true;
    });
  }

  sessionLabel(): string {
    const s = this.auth.getSession();
    if (!s) {
      return '';
    }
    return `${s.username} · ${s.shopId} · ${this.auth.getBranchId()}`;
  }

  onBranchChanged(): void {
    const url = this.router.url;
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigateByUrl(url);
    });
  }

  logout(): void {
    this.auth.logout();
    this.entitlements.clear();
    this.themeService.clearToFallback();
    this.router.navigateByUrl('/login');
  }
}
