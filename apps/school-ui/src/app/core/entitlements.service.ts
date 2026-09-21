import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, tap, of, catchError } from 'rxjs';
import { ApiService } from './api.service';

export interface Entitlements {
  planId?: string;
  featureFlags?: Record<string, boolean>;
  limits?: Record<string, number>;
}

@Injectable({ providedIn: 'root' })
export class EntitlementsService {
  private readonly api = inject(ApiService);
  private readonly subject = new BehaviorSubject<Entitlements | null>(null);

  readonly entitlements$ = this.subject.asObservable();

  load(): Observable<Entitlements> {
    return this.api.get<Entitlements>('/api/subscription/tenants/current/entitlements').pipe(
      tap((e) => this.subject.next(e ?? {})),
      catchError(() => {
        const empty = {};
        this.subject.next(empty);
        return of(empty);
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
    if (!flags) {
      // Until loaded, keep item visible to avoid empty nav flash; shell loads before render.
      return true;
    }
    return flags[flag] === true;
  }
}
