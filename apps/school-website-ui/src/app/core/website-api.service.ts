import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap, shareReplay } from 'rxjs';
import { environment } from '../../environments/environment';

export interface WebsiteResolve {
  organizationId: string;
  host: string;
  status: string;
  templateCode: string | null;
  displayName: string;
  erpLoginUrl: string;
  theme: Record<string, string | null>;
  homepage: Array<Record<string, unknown>>;
  navigation: Array<{ label: string; path: string; order?: number; external?: boolean }>;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class WebsiteApiService {
  readonly site = signal<WebsiteResolve | null>(null);
  private resolve$?: Observable<WebsiteResolve>;

  constructor(private readonly http: HttpClient) {}

  resolve(host = this.detectHost()): Observable<WebsiteResolve> {
    if (!this.resolve$) {
      const url = `${environment.apiBaseUrl}/api/website/public/resolve`;
      this.resolve$ = this.http
        .get<ApiResponse<WebsiteResolve>>(url, { params: { host } })
        .pipe(
          map((r) => r.data),
          tap((site) => {
            this.site.set(site);
            this.applyTheme(site.theme || {});
          }),
          shareReplay(1)
        );
    }
    return this.resolve$;
  }

  getPage(slug: string) {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/cms/public/pages/${slug}`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  listNews() {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Array<Record<string, unknown>>>>(
        `${environment.apiBaseUrl}/api/cms/public/news`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  getNews(slug: string) {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/cms/public/news/${slug}`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  listEvents() {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Array<Record<string, unknown>>>>(
        `${environment.apiBaseUrl}/api/cms/public/events`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  listGallery() {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Array<Record<string, unknown>>>>(
        `${environment.apiBaseUrl}/api/cms/public/gallery`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  private detectHost(): string {
    const host = window.location.hostname;
    if (!host || host === 'localhost' || host === '127.0.0.1') {
      return environment.defaultHost || 'hcp.localhost';
    }
    return host;
  }

  private applyTheme(theme: Record<string, string | null>): void {
    const root = document.documentElement;
    root.style.setProperty('--sf-primary', theme['primaryColor'] || '#0B3D91');
    root.style.setProperty('--sf-secondary', theme['secondaryColor'] || '#F5B700');
    if (theme['faviconUrl']) {
      let link = document.querySelector("link[rel*='icon']") as HTMLLinkElement | null;
      if (!link) {
        link = document.createElement('link');
        link.rel = 'icon';
        document.head.appendChild(link);
      }
      link.href = theme['faviconUrl'];
    }
  }
}
