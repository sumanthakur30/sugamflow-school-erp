import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, shareReplay, switchMap, timeout } from 'rxjs';
import { ApiService } from './api.service';
import { AuthSessionService } from './auth-session.service';
import { HttpClient } from '@angular/common/http';
import { environment } from '../environments/environment';

export interface ProvisionResult {
  organizationId: string;
  provisioned: boolean;
  alreadyProvisioned?: boolean;
  schoolName?: string;
  themeStatus?: string;
  planId?: string;
}

interface PublicShop {
  shopId?: string;
  shopName?: string;
}

/**
 * Idempotent first-login bootstrap: main campus, Design Studio schoolName from shop registry,
 * starter subscription. Safe to call on every authenticated session start.
 */
@Injectable({ providedIn: 'root' })
export class ProvisionService {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiBaseUrl;

  private inFlight: Observable<ProvisionResult | null> | null = null;
  private lastOrg: string | null = null;

  /** Run once per org per browser session (in-memory); still idempotent server-side. */
  ensureProvisioned(): Observable<ProvisionResult | null> {
    const org = this.auth.getOrganizationId();
    if (!org || org.length < 2) {
      return of(null);
    }
    if (this.lastOrg === org && this.inFlight) {
      return this.inFlight;
    }

    this.lastOrg = org;
    this.inFlight = this.resolveSchoolName(org).pipe(
      switchMap((schoolName) =>
        this.api.post<ProvisionResult>(
          '/api/config/provision',
          schoolName ? { schoolName } : {},
        ),
      ),
      timeout(8000),
      catchError(() => of(null)),
      shareReplay(1),
    );
    return this.inFlight;
  }

  private resolveSchoolName(org: string): Observable<string | null> {
    return this.http.get<PublicShop>(`${this.base}/api/v1/public/shops/${encodeURIComponent(org)}`).pipe(
      map((shop) => (shop?.shopName && shop.shopName.trim() ? shop.shopName.trim() : null)),
      catchError(() => of(null)),
    );
  }
}
