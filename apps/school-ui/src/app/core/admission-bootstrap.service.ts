import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';
import { ApiService } from './api.service';
import {
  SESSION_ACADEMIC_KEY,
  SESSION_BRANCH_KEY,
  SESSION_TENANT_KEY,
} from './session-keys';

/**
 * Caches /api/admission/bootstrap so opening Admission is instant after a warm,
 * and so shell can prefetch while the user is still on the dashboard.
 */
@Injectable({ providedIn: 'root' })
export class AdmissionBootstrapService {
  private readonly api = inject(ApiService);

  private cacheKey = '';
  private cached$: Observable<any> | null = null;
  private cachedValue: any | null = null;
  private epoch = 0;

  /** Drop cache on logout / org or campus change. */
  invalidate(): void {
    this.epoch += 1;
    this.cacheKey = '';
    this.cached$ = null;
    this.cachedValue = null;
  }

  private key(): string {
    return [
      localStorage.getItem(SESSION_TENANT_KEY) || '',
      localStorage.getItem(SESSION_BRANCH_KEY) || 'main',
      localStorage.getItem(SESSION_ACADEMIC_KEY) || '2025-26',
    ].join('|');
  }

  /** Fire-and-forget warm after campus headers are ready. */
  warm(): void {
    this.load().subscribe({ error: () => undefined });
  }

  /**
   * Returns cached bootstrap when available; otherwise fetches and caches.
   * Pass force=true to bypass cache (manual Refresh / error recovery).
   */
  load(force = false): Observable<any> {
    const key = this.key();
    const epoch = this.epoch;
    if (!force && this.cached$ && this.cacheKey === key) {
      return this.cached$;
    }
    this.cacheKey = key;
    this.cachedValue = null;
    this.cached$ = this.api.get<any>('/api/admission/bootstrap').pipe(
      tap((boot) => {
        if (epoch !== this.epoch) {
          return;
        }
        this.cachedValue = boot;
      }),
      shareReplay({ bufferSize: 1, refCount: false }),
    );
    return this.cached$;
  }

  peek(): any | null {
    return this.cacheKey === this.key() ? this.cachedValue : null;
  }
}
