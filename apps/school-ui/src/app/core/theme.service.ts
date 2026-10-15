import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../environments/environment';
import { resolveOrganizationId } from './tenant-context.util';

export interface DesignTheme {
  organizationId?: string;
  branchId?: string;
  version?: string;
  status?: string;
  branding?: Record<string, string>;
  colors?: Record<string, string>;
  typography?: Record<string, unknown>;
  loginScreen?: Record<string, unknown>;
  dashboard?: Record<string, unknown>;
  darkModeEnabled?: boolean;
  lightModeEnabled?: boolean;
}

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;
  private readonly themeSubject = new BehaviorSubject<DesignTheme | null>(null);
  /** Bumped on org switch / logout so late HTTP responses cannot repaint the previous school. */
  private applyGeneration = 0;

  readonly theme$ = this.themeSubject.asObservable();

  theme(): DesignTheme | null {
    return this.themeSubject.value;
  }

  /** Call on login/logout/org change before fetching the next theme. */
  beginSession(): number {
    return ++this.applyGeneration;
  }

  private currentOrg(): string {
    // Same source as API interceptors (JWT shopId wins over stale sf.tenantId).
    return resolveOrganizationId();
  }

  /**
   * Authenticated theme for the signed-in tenant only.
   * Rejects payloads / late responses that belong to another organization.
   */
  loadAuthenticated(): Observable<DesignTheme> {
    const generation = this.applyGeneration;
    const expectedOrg = this.currentOrg();
    return this.http
      .get<{ success: boolean; data: DesignTheme }>(`${this.base}/api/config/design-studio/theme`)
      .pipe(
        map((r) => r.data),
        tap((theme) => this.applyForOrg(generation, expectedOrg, theme)),
        catchError(() => of(this.applyFallbackIfCurrent(generation))),
      );
  }

  /** Pre-login white-label for an organization id. */
  loadPublished(organizationId: string, branchId = 'main'): Observable<DesignTheme> {
    const org = organizationId.trim();
    const generation = this.applyGeneration;
    if (!org) {
      return of(this.applyFallbackIfCurrent(generation));
    }
    const url =
      `${this.base}/api/config/design-studio/theme/published` +
      `?organizationId=${encodeURIComponent(org)}&branchId=${encodeURIComponent(branchId)}`;
    return this.http.get<{ success: boolean; data: DesignTheme }>(url).pipe(
      map((r) => r.data),
      tap((theme) => this.applyForOrg(generation, org, theme)),
      catchError(() => of(this.applyFallbackIfCurrent(generation))),
    );
  }

  /** Apply draft in Design Studio without waiting for reload. */
  apply(theme: DesignTheme | null): DesignTheme {
    const resolved = theme ?? this.platformFallback();
    this.themeSubject.next(resolved);
    this.writeCssVars(resolved);
    this.writeBrandingDom(resolved);
    return resolved;
  }

  clearToFallback(): void {
    this.beginSession();
    this.apply(this.platformFallback());
  }

  /** True when painted theme matches the signed-in organization. */
  matchesSessionOrg(theme: DesignTheme | null | undefined, org = this.currentOrg()): boolean {
    if (!org) {
      return true;
    }
    const themeOrg = (theme?.organizationId || '').trim();
    if (!themeOrg) {
      return false;
    }
    return themeOrg.toLowerCase() === org.toLowerCase();
  }

  private applyForOrg(
    generation: number,
    expectedOrg: string,
    theme: DesignTheme | null,
  ): DesignTheme {
    if (generation !== this.applyGeneration) {
      return this.themeSubject.value ?? this.platformFallback();
    }
    const liveOrg = this.currentOrg();
    // Session changed while request was in flight (demo → HCP or reverse).
    if (expectedOrg && liveOrg && expectedOrg.toLowerCase() !== liveOrg.toLowerCase()) {
      return this.themeSubject.value ?? this.platformFallback();
    }
    const org = liveOrg || expectedOrg;
    const themeOrg = (theme?.organizationId || '').trim();
    if (themeOrg && org && themeOrg.toLowerCase() !== org.toLowerCase()) {
      // Never paint Holly Cross while session is demo-school (or the reverse).
      return this.themeSubject.value ?? this.platformFallback();
    }
    const stamped: DesignTheme = {
      ...(theme ?? this.platformFallback()),
      organizationId: themeOrg || org || undefined,
    };
    return this.apply(stamped);
  }

  private applyFallbackIfCurrent(generation: number): DesignTheme {
    if (generation !== this.applyGeneration) {
      return this.themeSubject.value ?? this.platformFallback();
    }
    const org = this.currentOrg();
    const fallback = this.platformFallback();
    if (org) {
      fallback.organizationId = org;
    }
    return this.apply(fallback);
  }

  /**
   * Tenant colors may tint primary actions. Sidebar, page background, table
   * headers, and status text stay on the platform palette.
   */
  private writeCssVars(theme: DesignTheme): void {
    const root = document.documentElement;
    const colors = theme.colors ?? {};
    const button = this.readableButtonColor(colors['button'] || colors['primary']);
    root.style.setProperty('--sf-primary', button);
    root.style.setProperty('--sf-button', button);
    root.style.setProperty(
      '--sf-primary-hover',
      button.toLowerCase() === '#176b45' ? '#125638' : this.mixHex(button, '#000000', 0.18),
    );

    const skipped = new Set([
      'primary',
      'button',
      'menu',
      'accent',
      'surface',
      'panel',
      'text',
      'warning',
      'success',
      'error',
      'info',
    ]);
    for (const [key, value] of Object.entries(colors)) {
      if (value && !skipped.has(key)) {
        root.style.setProperty(`--sf-${key}`, String(value));
      }
    }
    this.lockChrome(root);

    const typography = theme.typography ?? {};
    if (typography['borderRadius']) {
      root.style.setProperty('--sf-radius', String(typography['borderRadius']));
    }
    if (typography['fontFamily']) {
      const family = String(typography['fontFamily']);
      const stack = `${family}, 'Segoe UI', sans-serif`;
      root.style.setProperty('--sf-font', stack);
      root.style.setProperty('--sf-display', stack);
    }
    if (typography['fontSize']) {
      root.style.setProperty('--sf-font-size', String(typography['fontSize']));
    }

    const login = theme.loginScreen ?? {};
    const bg = login['backgroundImage'];
    if (typeof bg === 'string' && bg.trim()) {
      const resolved = this.resolveAssetUrl(bg.trim());
      root.style.setProperty('--sf-login-bg-image', `url("${resolved}")`);
    } else {
      root.style.removeProperty('--sf-login-bg-image');
    }

    root.style.removeProperty('--sf-bg-glow-a');
    root.style.removeProperty('--sf-bg-glow-b');
  }

  /** Re-assert chrome after any saved theme so a brown menu cannot repaint the shell. */
  private lockChrome(root: HTMLElement): void {
    const locked: Record<string, string> = {
      '--sf-page-bg': '#F4F7F5',
      '--sf-header-bg': '#FFFFFF',
      '--sf-nav': '#16382C',
      '--sf-table-head': '#E8EFEB',
      '--sf-border': '#DCE4DF',
      '--sf-text-muted': '#64736A',
      '--sf-menu': '#16382C',
      '--sf-surface': '#F4F7F5',
      '--sf-panel': '#FFFFFF',
      '--sf-text': '#26352E',
      '--sf-accent': '#176B45',
      '--sf-warning': '#92400E',
      '--sf-success': '#166534',
      '--sf-error': '#991B1B',
      '--sf-info': '#1D4ED8',
    };
    for (const [name, value] of Object.entries(locked)) {
      root.style.setProperty(name, value);
    }
  }

  /** White button labels need at least WCAG AA contrast. Light brand colors fall back. */
  private readableButtonColor(hex: string | undefined): string {
    const fallback = '#176B45';
    const normalized = (hex || '').trim();
    if (!/^#?[0-9a-fA-F]{6}$/.test(normalized)) {
      return fallback;
    }
    const value = normalized.startsWith('#') ? normalized : `#${normalized}`;
    return this.contrastWithWhite(value) >= 4.5 ? value : fallback;
  }

  private contrastWithWhite(hex: string): number {
    return 1.05 / (this.relativeLuminance(hex) + 0.05);
  }

  private relativeLuminance(hex: string): number {
    const n = hex.replace('#', '');
    const channel = (index: number) => {
      const c = parseInt(n.slice(index, index + 2), 16) / 255;
      return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
    };
    return 0.2126 * channel(0) + 0.7152 * channel(2) + 0.0722 * channel(4);
  }

  private writeBrandingDom(theme: DesignTheme): void {
    const branding = theme.branding ?? {};
    const name = branding['schoolName'] || 'SugamFlow School';
    document.title = name;

    const favicon = branding['favicon']?.trim();
    let link = document.querySelector<HTMLLinkElement>("link[rel='icon']");
    if (favicon) {
      if (!link) {
        link = document.createElement('link');
        link.rel = 'icon';
        document.head.appendChild(link);
      }
      link.href = this.resolveAssetUrl(favicon);
    }
  }

  resolveAssetUrl(raw: string): string {
    const value = (raw || '').trim();
    if (!value) return '';
    if (
      value.startsWith('http://') ||
      value.startsWith('https://') ||
      value.startsWith('data:') ||
      value.startsWith('blob:')
    ) {
      return value;
    }
    if (value.startsWith('/')) {
      return `${this.base}${value}`;
    }
    return value;
  }

  private platformFallback(): DesignTheme {
    return {
      status: 'PUBLISHED',
      branding: {
        schoolName: 'SugamFlow School',
        productTagline: 'Configuration over customization',
        footer: 'Powered by SugamFlow',
      },
      colors: {
        primary: '#176B45',
        secondary: '#16382C',
        accent: '#176B45',
        menu: '#16382C',
        button: '#176B45',
        text: '#26352E',
        warning: '#92400E',
        success: '#166534',
        error: '#991B1B',
        info: '#1D4ED8',
        surface: '#F4F7F5',
        panel: '#FFFFFF',
      },
      typography: {
        fontFamily: 'Source Sans 3',
        fontSize: '14px',
        borderRadius: '8px',
      },
      loginScreen: {},
    };
  }

  private mixHex(hex: string, withHex: string, amount: number): string {
    const a = hex.replace('#', '');
    const b = withHex.replace('#', '');
    if (a.length !== 6 || b.length !== 6) {
      return withHex;
    }
    const mix = (i: number) => {
      const x = parseInt(a.slice(i, i + 2), 16);
      const y = parseInt(b.slice(i, i + 2), 16);
      return Math.round(x * (1 - amount) + y * amount)
        .toString(16)
        .padStart(2, '0');
    };
    return `#${mix(0)}${mix(2)}${mix(4)}`;
  }
}
