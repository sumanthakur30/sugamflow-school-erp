import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, catchError, map, of, tap } from 'rxjs';
import { environment } from '../environments/environment';

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

  readonly theme$ = this.themeSubject.asObservable();

  theme(): DesignTheme | null {
    return this.themeSubject.value;
  }

  /** Authenticated theme (draft or published) for the current tenant. */
  loadAuthenticated(): Observable<DesignTheme> {
    return this.http
      .get<{ success: boolean; data: DesignTheme }>(`${this.base}/api/config/design-studio/theme`)
      .pipe(
        map((r) => r.data),
        tap((theme) => this.apply(theme)),
        catchError(() => of(this.applyFallback())),
      );
  }

  /** Pre-login white-label for an organization id. */
  loadPublished(organizationId: string, branchId = 'main'): Observable<DesignTheme> {
    const org = organizationId.trim();
    if (!org) {
      return of(this.applyFallback());
    }
    const url =
      `${this.base}/api/config/design-studio/theme/published` +
      `?organizationId=${encodeURIComponent(org)}&branchId=${encodeURIComponent(branchId)}`;
    return this.http.get<{ success: boolean; data: DesignTheme }>(url).pipe(
      map((r) => r.data),
      tap((theme) => this.apply(theme)),
      catchError(() => of(this.applyFallback())),
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
    this.apply(this.platformFallback());
  }

  private applyFallback(): DesignTheme {
    return this.apply(this.platformFallback());
  }

  private writeCssVars(theme: DesignTheme): void {
    const root = document.documentElement;
    const colors = theme.colors ?? {};
    for (const [key, value] of Object.entries(colors)) {
      if (value) {
        root.style.setProperty(`--sf-${key}`, String(value));
      }
    }
    // Derived tokens if not explicitly configured
    if (!colors['surface'] && colors['primary']) {
      root.style.setProperty('--sf-surface', this.mixHex(String(colors['primary']), '#ffffff', 0.92));
    }
    if (!colors['panel']) {
      root.style.setProperty('--sf-panel', '#ffffff');
    }

    const typography = theme.typography ?? {};
    if (typography['borderRadius']) {
      root.style.setProperty('--sf-radius', String(typography['borderRadius']));
    }
    if (typography['fontFamily']) {
      root.style.setProperty('--sf-font', `${typography['fontFamily']}, 'Segoe UI', sans-serif`);
    }
    if (typography['fontSize']) {
      root.style.setProperty('--sf-font-size', String(typography['fontSize']));
    }

    const login = theme.loginScreen ?? {};
    const bg = login['backgroundImage'];
    if (typeof bg === 'string' && bg.trim()) {
      root.style.setProperty('--sf-login-bg-image', `url("${bg.trim()}")`);
    } else {
      root.style.removeProperty('--sf-login-bg-image');
    }

    const primary = colors['primary'] ?? '#0B6E4F';
    const accent = colors['accent'] ?? '#E9B44C';
    root.style.setProperty('--sf-bg-glow-a', this.hexToRgba(String(accent), 0.18));
    root.style.setProperty('--sf-bg-glow-b', this.hexToRgba(String(primary), 0.16));
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
      link.href = favicon;
    }
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
        primary: '#0B6E4F',
        secondary: '#084C61',
        accent: '#E9B44C',
        menu: '#0B3D2E',
        button: '#0B6E4F',
        text: '#1A1A1A',
        warning: '#D97706',
        success: '#15803D',
        error: '#B91C1C',
        surface: '#F3F7F5',
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

  private hexToRgba(hex: string, alpha: number): string {
    const n = hex.replace('#', '');
    if (n.length !== 6) {
      return `rgba(11, 110, 79, ${alpha})`;
    }
    const r = parseInt(n.slice(0, 2), 16);
    const g = parseInt(n.slice(2, 4), 16);
    const b = parseInt(n.slice(4, 6), 16);
    return `rgba(${r}, ${g}, ${b}, ${alpha})`;
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
