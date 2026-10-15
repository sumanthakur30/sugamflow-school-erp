import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, tap, of, catchError, map } from 'rxjs';
import { ApiService } from './api.service';

export interface Entitlements {
  planId?: string;
  featureFlags?: Record<string, boolean>;
  limits?: Record<string, number>;
  /** Set by this client. `unavailable` means the plan could not be read. */
  loadState?: 'ready' | 'unavailable';
}

@Injectable({ providedIn: 'root' })
export class EntitlementsService {
  private readonly api = inject(ApiService);
  private readonly subject = new BehaviorSubject<Entitlements | null>(null);

  readonly entitlements$ = this.subject.asObservable();

  load(): Observable<Entitlements> {
    return this.api.get<Entitlements>('/api/subscription/tenants/current/entitlements').pipe(
      map((e) => this.normalize(e)),
      tap((e) => this.subject.next(e)),
      catchError(() => {
        const failed: Entitlements = { loadState: 'unavailable' };
        this.subject.next(failed);
        return of(failed);
      }),
    );
  }

  clear(): void {
    this.subject.next(null);
  }

  current(): Entitlements | null {
    return this.subject.value;
  }

  isEnabled(flag: string | undefined | null): boolean {
    if (!flag) {
      return true;
    }
    const flags = this.subject.value?.featureFlags;
    // Hidden until a real flag map arrives. A missing plan must not show paid modules.
    if (!flags) {
      return false;
    }
    return flags[flag] === true;
  }

  private normalize(raw: Entitlements | null | undefined): Entitlements {
    const flags = raw?.featureFlags;
    if (!flags || typeof flags !== 'object') {
      return { loadState: 'unavailable' };
    }
    return { ...raw, featureFlags: flags, loadState: 'ready' };
  }
}
