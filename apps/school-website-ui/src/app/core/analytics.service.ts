import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { WebsiteApiService } from './website-api.service';

/** Fire-and-forget page_view tracking for Phase 4 analytics. */
@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly api = inject(WebsiteApiService);
  private started = false;

  start(): void {
    if (this.started) return;
    this.started = true;
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd))
      .subscribe((e) => this.track('page_view', e.urlAfterRedirects || '/'));
  }

  track(eventType: string, path: string): void {
    const site = this.api.site();
    const host =
      !window.location.hostname ||
      window.location.hostname === 'localhost' ||
      window.location.hostname === '127.0.0.1'
        ? environment.defaultHost || 'hcp.localhost'
        : window.location.hostname;
    this.http
      .post(`${environment.apiBaseUrl}/api/website/public/track`, {
        host,
        organizationId: site?.organizationId,
        eventType,
        path,
        referrer: document.referrer || '',
        meta: { templateCode: site?.templateCode || null },
      })
      .subscribe({ error: () => undefined });
  }
}
