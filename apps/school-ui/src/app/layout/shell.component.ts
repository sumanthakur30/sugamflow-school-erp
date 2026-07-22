import { Component, HostListener, OnInit, inject } from '@angular/core';
import { AsyncPipe, NgStyle } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthSessionService } from '../core/auth-session.service';
import { EntitlementsService } from '../core/entitlements.service';
import { OfflineQueueService } from '../core/offline-queue.service';
import { ProvisionService } from '../core/provision.service';
import { ThemeService } from '../core/theme.service';
import { BranchSwitcherComponent } from '../features/branches/branch-switcher.component';
import { filter, switchMap } from 'rxjs';

export interface NavItem {
  path: string;
  label: string;
  feature?: string;
  roles?: string[];
}

export interface NavGroup {
  id: string;
  label: string;
  icon: string;
  items: NavItem[];
}

/**
 * SugamFlow-aligned shell:
 * - Context bar + module strip with split tabs (label → default route, caret → submenu)
 * - Sidebar for People / Reports / Admin / Design (not duplicated in top)
 * - Submenu rendered at shell root (never clipped by overflow)
 */
@Component({
  selector: 'sf-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AsyncPipe, NgStyle, BranchSwitcherComponent],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly entitlements = inject(EntitlementsService);
  private readonly provision = inject(ProvisionService);
  readonly themeService = inject(ThemeService);
  readonly offlineQueue = inject(OfflineQueueService);

  readonly theme$ = this.themeService.theme$;
  readonly online$ = this.offlineQueue.isOnline$;
  readonly offlineItems$ = this.offlineQueue.items$;

  readonly topCatalog: NavGroup[] = [
    {
      id: 'students',
      label: 'Students',
      icon: 'users',
      items: [
        { path: '/admin/admission', label: 'Admission', feature: 'FEATURE_ADMISSION' },
        {
          path: '/admin/student-directory',
          label: 'Student Directory',
          feature: 'FEATURE_STUDENT_MASTER',
        },
        { path: '/admin/students', label: 'Student Master', feature: 'FEATURE_STUDENT_MASTER' },
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
        },
        { path: '/admin/payroll', label: 'Payroll', feature: 'FEATURE_PAYROLL' },
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
        {
          path: '/admin/devices',
          label: 'Device Adapters',
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
        { path: '/admin/fee', label: 'Fee Collection', feature: 'FEATURE_FEE' },
        { path: '/admin/finance', label: 'Finance / Payments', feature: 'FEATURE_FEE' },
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

  readonly sideCatalog: NavGroup[] = [
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
      ],
    },
  ];

  topGroups: NavGroup[] = [...this.topCatalog];
  sideGroups: NavGroup[] = [...this.sideCatalog];

  openTopId: string | null = null;
  dropdownStyle: Record<string, string> = {};
  expandedSide = new Set<string>(['reports']);
  mobileNavOpen = false;
  sidebarCollapsed = false;

  ngOnInit(): void {
    this.provision
      .ensureProvisioned()
      .pipe(switchMap(() => this.themeService.loadAuthenticated()))
      .subscribe();
    this.entitlements.load().subscribe(() => this.refreshNav());
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.closeTopMenu();
      this.mobileNavOpen = false;
      this.ensureActiveSideExpanded();
    });
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    if (target?.closest('[data-sf-nav-keep]')) {
      return;
    }
    this.closeTopMenu();
    // Dismiss the mobile drawer when tapping outside the sidebar/scrim controls.
    if (this.mobileNavOpen && !target?.closest('aside.sidebar')) {
      this.mobileNavOpen = false;
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closeTopMenu();
    this.mobileNavOpen = false;
  }

  @HostListener('window:resize')
  onResize(): void {
    this.closeTopMenu();
  }

  refreshNav(): void {
    const role = (this.auth.getRole() || '').toUpperCase();
    const portalOnly = role === 'PARENT' || role === 'STUDENT';
    const teacherPortal = role === 'TEACHER';

    const keep = (item: NavItem): boolean => {
      if (portalOnly) return item.path === '/parent';
      if (teacherPortal) {
        return item.path === '/teacher' || item.path === '/admin/dashboard';
      }
      if (!this.entitlements.isEnabled(item.feature)) return false;
      if (item.roles?.length) {
        if (!item.roles.map((r) => r.toUpperCase()).includes(role)) {
          return false;
        }
      }
      return true;
    };

    this.topGroups = this.topCatalog
      .map((g) => ({ ...g, items: g.items.filter(keep) }))
      .filter((g) => g.items.length > 0);

    this.sideGroups = this.sideCatalog
      .map((g) => ({ ...g, items: g.items.filter(keep) }))
      .filter((g) => g.items.length > 0);

    this.ensureActiveSideExpanded();
  }

  /** Default land path for a module tab (SugamFlow primary link). */
  /** Navigate to a list path, always landing on the clean list URL (clears ?new / ?id). */
  goNav(path: string, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.closeTopMenu();
    this.closeMobileNav();
    const clean = (path || '/').split('?')[0];
    const current = this.router.url.split('?')[0];
    const hasQuery = this.router.url.includes('?');
    if (current === clean && !hasQuery) {
      // Same list URL with stuck in-memory form/detail — force remount.
      void this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
        void this.router.navigateByUrl(clean);
      });
      return;
    }
    void this.router.navigateByUrl(clean);
  }

  groupHome(group: NavGroup): string {
    return group.items[0]?.path || '/admin/admission';
  }

  openTopMenu(groupId: string, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (this.openTopId === groupId) {
      this.closeTopMenu();
      return;
    }
    const btn = event.currentTarget as HTMLElement;
    const rect = btn.getBoundingClientRect();
    // Anchor under the whole split group when possible
    const groupEl = btn.closest('.module-split') as HTMLElement | null;
    const anchor = groupEl?.getBoundingClientRect() ?? rect;
    const menuWidth = 240;
    let left = anchor.left;
    if (left + menuWidth > window.innerWidth - 8) {
      left = window.innerWidth - menuWidth - 8;
    }
    this.dropdownStyle = {
      top: `${Math.round(anchor.bottom + 4)}px`,
      left: `${Math.max(8, Math.round(left))}px`,
      minWidth: `${Math.max(menuWidth, Math.round(anchor.width))}px`,
    };
    this.openTopId = groupId;
  }

  openGroupItems(): NavItem[] {
    const g = this.topGroups.find((x) => x.id === this.openTopId);
    return g?.items ?? [];
  }

  openGroupLabel(): string {
    return this.topGroups.find((x) => x.id === this.openTopId)?.label ?? '';
  }

  isTopOpen(groupId: string): boolean {
    return this.openTopId === groupId;
  }

  closeTopMenu(): void {
    this.openTopId = null;
  }

  isGroupActive(group: NavGroup): boolean {
    const url = this.router.url.split('?')[0];
    return group.items.some((item) => url === item.path || url.startsWith(item.path + '/'));
  }

  toggleSideSection(groupId: string, event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    if (this.expandedSide.has(groupId)) {
      this.expandedSide.delete(groupId);
    } else {
      this.expandedSide.add(groupId);
    }
    this.expandedSide = new Set(this.expandedSide);
  }

  isSideExpanded(groupId: string): boolean {
    return this.expandedSide.has(groupId);
  }

  toggleSidebar(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.closeTopMenu();
    if (window.matchMedia('(max-width: 900px)').matches) {
      this.mobileNavOpen = !this.mobileNavOpen;
    } else {
      this.sidebarCollapsed = !this.sidebarCollapsed;
    }
  }

  toggleMobileNav(event: MouseEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.closeTopMenu();
    this.mobileNavOpen = !this.mobileNavOpen;
  }

  closeMobileNav(): void {
    this.mobileNavOpen = false;
    this.closeTopMenu();
  }

  private ensureActiveSideExpanded(): void {
    for (const g of this.sideGroups) {
      if (this.isGroupActive(g)) {
        this.expandedSide.add(g.id);
      }
    }
    for (const g of this.topGroups) {
      if (this.isGroupActive(g)) {
        this.expandedSide.add('m-' + g.id);
      }
    }
    this.expandedSide = new Set(this.expandedSide);
  }

  schoolName(theme: { branding?: Record<string, string> } | null): string {
    return theme?.branding?.['schoolName'] || 'School Admin';
  }

  brandInitial(theme: { branding?: Record<string, string> } | null): string {
    const name = this.schoolName(theme);
    return (name.charAt(0) || 'S').toUpperCase();
  }

  sessionChip(): string {
    const s = this.auth.getSession();
    if (!s) return '';
    return s.username || s.role || 'User';
  }

  roleLabel(): string {
    const role = (this.auth.getRole() || '').replace(/_/g, ' ');
    return role || 'Staff';
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
