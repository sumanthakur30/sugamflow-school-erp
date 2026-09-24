import { Component, OnInit, inject } from '@angular/core';
import { AsyncPipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthSessionService } from '../../core/auth-session.service';
import { ThemeService } from '../../core/theme.service';
import { ApiService } from '../../core/api.service';
import { PortalContextService } from './portal-context.service';
import { BranchSwitcherComponent } from '../branches/branch-switcher.component';

@Component({
  selector: 'sf-portal-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AsyncPipe, BranchSwitcherComponent],
  templateUrl: './portal-shell.component.html',
  styleUrls: ['./portal-shell.component.scss'],
})
export class PortalShellComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly portalCtx = inject(PortalContextService);
  readonly themeService = inject(ThemeService);
  readonly theme$ = this.themeService.theme$;

  portalKey = 'parent';
  title = 'Portal';
  subtitle = '';
  nav: Array<{ id: string; label: string; route: string }> = [];
  loading = true;
  featureEnabled = false;
  error = '';

  ngOnInit(): void {
    this.themeService.loadAuthenticated().subscribe();
    this.route.data.subscribe((data) => {
      this.portalKey = String(data['portalKey'] || 'parent');
      this.loadBootstrap();
    });
  }

  loadBootstrap(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>(`/api/config/portals/${this.portalKey}/bootstrap`).subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.loading = false;
        if (!this.featureEnabled) {
          this.portalCtx.clear();
          return;
        }
        this.title = boot.title || this.portalKey;
        this.subtitle = boot.subtitle || '';
        this.nav = boot.nav || [];
        // Do not override JWT role — gateway ignores client X-Auth-Role for school APIs.
        // Keep UI destination only; relationship scoping uses the signed-in account role.
        this.portalCtx.set(this.portalKey, boot);
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Portal bootstrap failed';
        const msg = String(this.error).toUpperCase();
        // Do NOT imply plan feature is off on transport/server errors
        this.featureEnabled = !(
          msg.includes('FEATURE_DISABLED') || msg.includes('NOT ENABLED FOR THIS PLAN')
        );
      },
    });
  }

  sessionLabel(): string {
    const s = this.auth.getSession();
    if (!s) {
      return '';
    }
    return `${s.username} · ${this.title}`;
  }

  goAdmin(): void {
    this.auth.setActiveRole(this.auth.getSession()?.role ?? 'SHOP_OWNER');
    this.router.navigateByUrl('/admin/dashboard');
  }

  onBranchChanged(): void {
    this.loadBootstrap();
    const url = this.router.url;
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigateByUrl(url);
    });
  }

  logout(event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    try {
      this.auth.logout();
      this.portalCtx.clear();
      this.themeService.clearToFallback();
    } catch {
      // still navigate
    }
    window.location.assign('/login');
  }
}
