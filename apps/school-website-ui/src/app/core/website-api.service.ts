import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, tap, shareReplay } from 'rxjs';
import { environment } from '../../environments/environment';

export interface WebsiteResolve {
  organizationId: string;
  branchId?: string | null;
  siteId?: string | null;
  host: string;
  status: string;
  templateCode: string | null;
  displayName: string;
  erpLoginUrl: string;
  cdnBaseUrl?: string | null;
  theme: Record<string, string | null>;
  homepage: Array<Record<string, unknown>>;
  navigation: Array<{ label: string; path: string; order?: number; external?: boolean }>;
  seo?: Record<string, string | null>;
}

/** School-specific contact/brand — always from theme/CMS, never hardcoded per tenant. */
export interface SiteBrandContact {
  displayName: string;
  tagline: string;
  contactEmail: string;
  contactPhone: string;
  workingHours: string;
  address: string;
  addressLine2: string;
  footerBlurb: string;
  socialFacebook: string;
  socialInstagram: string;
  socialYoutube: string;
  socialWhatsapp: string;
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

  listBlog() {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Array<Record<string, unknown>>>>(
        `${environment.apiBaseUrl}/api/cms/public/blog`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  getBlog(slug: string) {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/cms/public/blog/${slug}`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  listAlumni() {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Array<Record<string, unknown>>>>(
        `${environment.apiBaseUrl}/api/cms/public/alumni`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  getAlumni(slug: string) {
    const org = this.site()?.organizationId;
    return this.http
      .get<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/cms/public/alumni/${slug}`,
        { params: { organizationId: org || '' } }
      )
      .pipe(map((r) => r.data));
  }

  applyAdmission(payload: {
    fullName: string;
    mobile: string;
    email?: string;
    classApplied: string;
    message?: string;
    age?: string;
    captchaToken?: string;
  }) {
    const org = this.site()?.organizationId;
    return this.http
      .post<ApiResponse<Record<string, unknown>>>(
        `${environment.apiBaseUrl}/api/admission/public/apply`,
        {
          organizationId: org,
          ...payload,
        }
      )
      .pipe(map((r) => r.data));
  }

  /** Absolute URL for CMS/media paths (same-origin relative or full URL). */
  mediaUrl(path?: string | null): string {
    if (path == null) return '';
    const raw = String(path).trim();
    if (!raw || raw === 'undefined' || raw === 'null') return '';
    if (/^https?:\/\//i.test(raw)) return raw;
    const base = (environment.apiBaseUrl || '').replace(/\/$/, '');
    return raw.startsWith('/') ? `${base}${raw}` : `${base}/${raw}`;
  }

  /** Deep-link into School ERP login with org + destination prefilled. */
  erpLoginUrl(destination?: string, returnUrl?: string): string {
    const site = this.site();
    const base = (site?.erpLoginUrl || `${environment.erpBaseUrl}/login`).replace(/\/$/, '');
    const loginBase = base.includes('/login') ? base : `${base}/login`;
    const url = new URL(loginBase, window.location.origin);
    if (site?.organizationId) {
      url.searchParams.set('org', site.organizationId);
    }
    if (destination) {
      url.searchParams.set('destination', destination);
    }
    if (returnUrl) {
      url.searchParams.set('returnUrl', returnUrl);
    }
    return url.toString();
  }

  /**
   * Tenant brand/contact from resolve theme (+ displayName / seo fallbacks).
   * Empty strings mean “not configured” — UI should hide those rows.
   */
  brandContact(site?: WebsiteResolve | null): SiteBrandContact {
    const s = site ?? this.site();
    const theme = s?.theme || {};
    const t = (key: string) => this.themeStr(theme, key);
    const seoDesc = (s?.seo?.['defaultDescription'] || '').trim();
    return {
      displayName: (s?.displayName || '').trim(),
      tagline: t('tagline'),
      contactEmail: t('contactEmail') || t('email'),
      contactPhone: t('contactPhone') || t('phone'),
      workingHours: t('workingHours') || t('hours'),
      address: t('address') || t('campusAddress'),
      addressLine2: t('addressLine2'),
      footerBlurb: t('footerBlurb') || seoDesc,
      socialFacebook: t('socialFacebook'),
      socialInstagram: t('socialInstagram'),
      socialYoutube: t('socialYoutube'),
      socialWhatsapp: t('socialWhatsapp'),
    };
  }

  themeStr(theme: Record<string, string | null> | null | undefined, key: string): string {
    const raw = theme?.[key];
    if (raw == null) return '';
    const v = String(raw).trim();
    if (!v || v === 'null' || v === 'undefined') return '';
    return v;
  }

  telHref(phone: string): string {
    const digits = phone.replace(/[^\d+]/g, '');
    return digits ? `tel:${digits}` : '';
  }

  mailtoHref(email: string): string {
    return email ? `mailto:${email}` : '';
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
    const favicon = this.mediaUrl(theme['faviconUrl'] || theme['logoUrl'] || undefined);
    if (favicon) {
      let link = document.querySelector("link[rel*='icon']") as HTMLLinkElement | null;
      if (!link) {
        link = document.createElement('link');
        link.rel = 'icon';
        document.head.appendChild(link);
      }
      link.href = favicon;
    }
  }
}
