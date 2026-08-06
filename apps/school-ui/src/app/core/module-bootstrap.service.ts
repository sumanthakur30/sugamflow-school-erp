import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, switchMap, tap, throwError, timer } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from './api.service';
import {
  SESSION_ACADEMIC_KEY,
  SESSION_BRANCH_KEY,
  SESSION_TENANT_KEY,
} from './session-keys';

type CacheEntry = {
  tenantKey: string;
  epoch: number;
  value: any | null;
  stream$: Observable<any> | null;
};

/**
 * Shared module bootstrap loader: campus-scoped cache, one settle retry on failure,
 * and optional warm after login so Fee/Admission/etc. open faster.
 */
@Injectable({ providedIn: 'root' })
export class ModuleBootstrapService {
  private readonly api = inject(ApiService);
  private readonly cache = new Map<string, CacheEntry>();
  /** Bumped on invalidate so in-flight demo-school responses cannot overwrite HCP cache. */
  private epoch = 0;

  private tenantKey(): string {
    return [
      localStorage.getItem(SESSION_TENANT_KEY) || '',
      localStorage.getItem(SESSION_BRANCH_KEY) || 'main',
      localStorage.getItem(SESSION_ACADEMIC_KEY) || '2025-26',
    ].join('|');
  }

  invalidate(path?: string): void {
    this.epoch += 1;
    if (path) {
      this.cache.delete(path);
      return;
    }
    this.cache.clear();
  }

  peek(path: string): any | null {
    const entry = this.cache.get(path);
    if (!entry || entry.tenantKey !== this.tenantKey() || entry.epoch !== this.epoch) {
      return null;
    }
    return entry.value;
  }

  /** Prefetch several bootstraps (fire-and-forget). */
  warm(paths: string[]): void {
    for (const path of paths) {
      this.load(path).subscribe({ error: () => undefined });
    }
  }

  /**
   * Load bootstrap JSON. Retries once after 900ms on transport/5xx failures.
   * Pass force=true to bypass cache.
   */
  load(path: string, force = false): Observable<any> {
    const tenantKey = this.tenantKey();
    const epoch = this.epoch;
    const existing = this.cache.get(path);
    if (
      !force &&
      existing?.stream$ &&
      existing.tenantKey === tenantKey &&
      existing.epoch === epoch
    ) {
      return existing.stream$;
    }

    const once = () => this.api.get<any>(path);

    const stream$ = once().pipe(
      catchError(() =>
        timer(900).pipe(
          switchMap(() => once()),
          catchError((err) => throwError(() => err)),
        ),
      ),
      tap({
        next: (boot) => {
          if (epoch !== this.epoch) {
            return;
          }
          this.cache.set(path, { tenantKey, epoch, value: boot, stream$ });
        },
        error: () => {
          if (epoch === this.epoch) {
            this.cache.delete(path);
          }
        },
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );

    this.cache.set(path, { tenantKey, epoch, value: null, stream$ });
    return stream$;
  }

  static errorCode(err: any): string {
    const body = err?.error;
    return String(body?.data?.code ?? body?.code ?? '').toUpperCase();
  }

  static isConfigMissing(err: any): boolean {
    const code = ModuleBootstrapService.errorCode(err);
    return code === 'FORM_MISSING' || code === 'WORKFLOW_MISSING' || code === 'MODULE_DISABLED';
  }

  /** True only when API explicitly says the plan feature is off. */
  static isFeatureDisabled(err: any): boolean {
    const code = ModuleBootstrapService.errorCode(err);
    if (code === 'FEATURE_DISABLED') {
      return true;
    }
    const body = err?.error;
    const msg = String(body?.message ?? err?.message ?? '').toUpperCase();
    return msg.includes('FEATURE_DISABLED') || msg.includes('NOT ENABLED FOR THIS PLAN');
  }

  static errorMessage(err: any, fallback: string): string {
    const status = err?.status;
    const code = ModuleBootstrapService.errorCode(err);
    const detail = err?.error?.message ?? err?.message ?? fallback;
    if (status === 503 || status === 0) {
      return `Service unavailable (${status || 'network'}). ${detail}`;
    }
    if (status === 500 || status === 502 || status === 504) {
      return `Temporary server error (${status}). Retry in a moment. ${detail}`;
    }
    if (code === 'WORKFLOW_MISSING') {
      return `${detail} Ensure workflow-service is running and system workflows are seeded, then refresh.`;
    }
    if (code === 'FORM_MISSING') {
      return `${detail} Ensure form-builder-service is running and forms are seeded, then refresh.`;
    }
    return detail;
  }
}
