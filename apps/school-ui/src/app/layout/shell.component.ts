import { Component, HostListener, OnInit, inject } from '@angular/core';
import { AsyncPipe, NgStyle } from '@angular/common';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthSessionService } from '../core/auth-session.service';
import { EntitlementsService } from '../core/entitlements.service';
import { OfflineQueueService } from '../core/offline-queue.service';
import { ProvisionService } from '../core/provision.service';
import { ThemeService } from '../core/theme.service';
import { TenantContextService } from '../core/tenant-context.service';
import { AdmissionBootstrapService } from '../core/admission-bootstrap.service';
import { ModuleBootstrapService } from '../core/module-bootstrap.service';
import { BranchSwitcherComponent } from '../features/branches/branch-switcher.component';
import { StudentGlobalSearchComponent } from './student-global-search.component';
import { ApiService } from '../core/api.service';
import { applySavedMenu, SavedMenuNode } from './menu-drive';
import { NavGroup, NavItem, SIDE_NAV, TOP_NAV } from './nav-catalog';
import { filter, switchMap, timer } from 'rxjs';


/**
 * SugamFlow-aligned shell:
 * - Context bar + module strip with split tabs (label → default route, caret → submenu)
 * - Sidebar for People / Reports / Admin / Design (not duplicated in top)
 * - Submenu rendered at shell root (never clipped by overflow)
 */
@Component({
  selector: 'sf-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    AsyncPipe,
    NgStyle,
    BranchSwitcherComponent,
    StudentGlobalSearchComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent implements OnInit {
  private readonly auth = inject(AuthSessionService);
  private readonly router = inject(Router);
  private readonly entitlements = inject(EntitlementsService);
  private readonly provision = inject(ProvisionService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly admissionBootstrap = inject(AdmissionBootstrapService);
  private readonly modules = inject(ModuleBootstrapService);
  private readonly api = inject(ApiService);
  readonly themeService = inject(ThemeService);
  readonly offlineQueue = inject(OfflineQueueService);

  readonly theme$ = this.themeService.theme$;
  readonly online$ = this.offlineQueue.isOnline$;
  readonly offlineItems$ = this.offlineQueue.items$;


  readonly topCatalog: NavGroup[] = TOP_NAV;
  readonly sideCatalog: NavGroup[] = SIDE_NAV;
  private savedMenus: SavedMenuNode[] = [];

  topGroups: NavGroup[] = [...this.topCatalog];
  sideGroups: NavGroup[] = [...this.sideCatalog];

  openTopId: string | null = null;
  dropdownStyle: Record<string, string> = {};
  expandedSide = new Set<string>(['reports', 'support', 'compliance']);
  mobileNavOpen = false;
  sidebarCollapsed = false;

  ngOnInit(): void {
    this.provision
      .ensureProvisioned()
      .pipe(switchMap(() => this.themeService.loadAuthenticated()))
      .subscribe((theme) => {
        // If a previous school's branding is still painted, force reload for this session.
        if (!this.themeService.matchesSessionOrg(theme)) {
          this.themeService.clearToFallback();
          this.themeService.loadAuthenticated().subscribe();
        }
      });
    this.entitlements.load().subscribe(() => this.refreshNav());
    this.api.get<SavedMenuNode[]>('/api/config/menus').subscribe({
      next: (menus) => {
        this.savedMenus = menus || [];
        this.refreshNav();
      },
      error: () => this.refreshNav(),
    });
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => {
      this.closeTopMenu();
      this.mobileNavOpen = false;
      this.ensureActiveSideExpanded();
    });
    // Prefetch module bootstraps after campus sync — defer so dashboard KPIs get connections first.
    this.tenantContext.whenCampusReady().subscribe(() => {
      timer(2000).subscribe(() => {
        this.admissionBootstrap.warm();
        this.modules.warm([
          '/api/fee/bootstrap',
          '/api/fee/finance/bootstrap',
          '/api/student/bootstrap',
          '/api/student/directory/bootstrap',
          '/api/staff/directory/bootstrap',
          '/api/payroll/bootstrap',
          '/api/attendance/bootstrap',
          '/api/exam/bootstrap',
          '/api/library/bootstrap',
        ]);
      });
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
      if (item.permissions?.length && !this.auth.hasAnyPermission(item.permissions)) {
        return false;
      }
      return true;
    };

    this.topGroups = applySavedMenu(this.topCatalog, this.savedMenus, keep);
    this.sideGroups = applySavedMenu(this.sideCatalog, this.savedMenus, keep);

    this.ensureActiveSideExpanded();
  }

  /** Default land path for a module tab (SugamFlow primary link). */
  /** Navigate to a list path, always landing on the clean list URL (clears ?new / ?id). */
  goNav(path: string, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    const target = path || '/';
    const clean = target.split('?')[0];
    const current = this.router.url.split('?')[0];
    const hasQuery = this.router.url.includes('?') || target.includes('?');
    if (current === clean && !hasQuery) {
      this.closeTopMenu();
      this.closeMobileNav();
      // Same list URL with stuck in-memory form/detail — force remount.
      void this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
        void this.router.navigateByUrl(clean);
      });
      return;
    }
    // Navigate before the flyout is removed. Closing first was destroying the
    // link during the click, so the page never changed.
    void this.router.navigateByUrl(target).then(() => {
      this.closeTopMenu();
      this.closeMobileNav();
    });
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

  schoolName(theme: { organizationId?: string; branding?: Record<string, string> } | null): string {
    const org = this.auth.getOrganizationId();
    // Never show another school's Design Studio name for this session.
    if (!this.themeService.matchesSessionOrg(theme as any, org)) {
      return org || 'School Admin';
    }
    return theme?.branding?.['schoolName'] || org || 'School Admin';
  }

  brandInitial(theme: { organizationId?: string; branding?: Record<string, string> } | null): string {
    const name = this.schoolName(theme);
    return (name.charAt(0) || 'S').toUpperCase();
  }

  /** Broken logo URL → show letter mark instead of empty img. */
  onBrandLogoError(event: Event): void {
    const img = event.target as HTMLImageElement | null;
    if (!img) return;
    img.style.display = 'none';
    const mark = document.createElement('span');
    mark.className = img.classList.contains('sidebar-brand-logo')
      ? 'sidebar-brand-mark'
      : 'context-brand-mark';
    mark.textContent = (
      img.alt?.trim()?.charAt(0) ||
      this.schoolName(this.themeService.theme()).charAt(0) ||
      'S'
    ).toUpperCase();
    img.parentElement?.insertBefore(mark, img.nextSibling);
  }

  sessionChip(): string {
    const s = this.auth.getSession();
    if (!s) return '';
    return s.username || s.role || 'User';
  }

  roleLabel(): string {
    const raw = (this.auth.getRole() || '').toUpperCase();
    const labels: Record<string, string> = {
      SHOP_OWNER: 'School Owner',
      SUPER_ADMIN: 'Super Admin',
      ADMIN: 'Admin',
      PRINCIPAL: 'Principal',
      TEACHER: 'Teacher',
      CLASS_TEACHER: 'Class Teacher',
      ACCOUNTANT: 'Accountant',
      RECEPTION: 'Reception',
      PARENT: 'Parent',
      STUDENT: 'Student',
      STAFF: 'Staff',
    };
    if (labels[raw]) {
      return labels[raw];
    }
    return raw ? raw.replace(/_/g, ' ') : 'Staff';
  }

  onBranchChanged(): void {
    this.admissionBootstrap.invalidate();
    this.modules.invalidate();
    const url = this.router.url;
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigateByUrl(url);
    });
  }

  logout(event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    try {
      this.closeTopMenu();
      this.mobileNavOpen = false;
      this.auth.logout();
      this.entitlements.clear();
      this.admissionBootstrap.invalidate();
      this.modules.invalidate();
      // Wipe any leftover sf.* keys so login never inherits HCP theme/session.
      for (let i = localStorage.length - 1; i >= 0; i--) {
        const key = localStorage.key(i);
        if (key && key.startsWith('sf.')) {
          localStorage.removeItem(key);
        }
      }
      sessionStorage.clear();
      // Drop inline theme vars so a failed paint is not a mint blank page.
      document.documentElement.removeAttribute('style');
      document.title = 'SugamFlow School';
    } catch {
      // Still leave the app even if a cache clear throws.
    }
    // replace (not assign) — no back-button return into a half-cleared session.
    window.location.replace('/login');
  }
}
