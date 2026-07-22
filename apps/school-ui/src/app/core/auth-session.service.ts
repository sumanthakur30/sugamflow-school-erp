import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../environments/environment';

export interface AuthResponse {
  accountId?: number | null;
  shopId: string;
  tenantId: number;
  username: string;
  role: string;
  accessToken?: string | null;
  mfaRequired?: boolean | null;
  mfaToken?: string | null;
}

export interface LoginRequest {
  shopId: string;
  username: string;
  password: string;
}

interface JwtPayload {
  shopId?: string | number;
  tenantId?: string | number;
  role?: string;
  sub?: string;
  exp?: number;
}

const TOKEN_KEY = 'sf.accessToken';
const SESSION_KEY = 'sf.session';

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly http = inject(HttpClient);
  private readonly authBase = `${environment.apiBaseUrl}/api/v1/auth`;
  private readonly loggedIn = new BehaviorSubject<boolean>(this.hasValidSession());

  readonly isLoggedIn$ = this.loggedIn.asObservable();

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.authBase}/login`, request).pipe(
      tap((response) => {
        if (!response.mfaRequired) {
          this.storeSession(response);
        }
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(SESSION_KEY);
    localStorage.removeItem('sf.tenantId');
    localStorage.removeItem('sf.branchId');
    localStorage.removeItem('sf.sessionId');
    localStorage.removeItem('sf.userId');
    localStorage.removeItem('sf.role');
    this.loggedIn.next(false);
  }

  isLoggedIn(): boolean {
    return this.loggedIn.value;
  }

  getAccessToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  getSession(): AuthResponse | null {
    const raw = localStorage.getItem(SESSION_KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthResponse;
    } catch {
      return null;
    }
  }

  getRole(): string | null {
    return localStorage.getItem('sf.role') ?? this.getSession()?.role ?? this.decodeToken()?.role ?? null;
  }

  /** Override role header for portal experiences (PARENT / TEACHER) without re-login. */
  setActiveRole(role: string): void {
    if (role && role.trim()) {
      localStorage.setItem('sf.role', role.trim().toUpperCase());
    }
  }

  /** Organization slug for school APIs (maps from auth shopId). */
  getOrganizationId(): string {
    return localStorage.getItem('sf.tenantId') ?? this.getSession()?.shopId ?? 'demo-school';
  }

  getBranchId(): string {
    return localStorage.getItem('sf.branchId') ?? 'main';
  }

  setBranchId(branchKey: string): void {
    const key = (branchKey || 'main').trim();
    localStorage.setItem('sf.branchId', key || 'main');
  }

  getSessionId(): string {
    return localStorage.getItem('sf.sessionId') ?? '2025-26';
  }

  setSessionId(sessionId: string): void {
    localStorage.setItem('sf.sessionId', (sessionId || '2025-26').trim());
  }

  applySessionContext(response: AuthResponse): void {
    const previousOrg = localStorage.getItem('sf.tenantId');
    const nextOrg = response.shopId;
    localStorage.setItem('sf.tenantId', nextOrg);
    // Never carry campus/session from another school into a newly registered org.
    if (!previousOrg || previousOrg !== nextOrg) {
      localStorage.setItem('sf.branchId', 'main');
      localStorage.setItem('sf.sessionId', '2025-26');
    } else {
      localStorage.setItem('sf.branchId', localStorage.getItem('sf.branchId') ?? 'main');
      localStorage.setItem('sf.sessionId', localStorage.getItem('sf.sessionId') ?? '2025-26');
    }
    localStorage.setItem('sf.userId', response.username);
    localStorage.setItem('sf.role', response.role);
  }

  toScopedUsername(baseUsername: string, shopId: string): string {
    const user = baseUsername.trim();
    const shop = shopId.trim();
    const suffix = `_${shop}`;
    if (user.endsWith(suffix)) {
      return user;
    }
    return `${user}${suffix}`;
  }

  private storeSession(response: AuthResponse): void {
    const token = response.accessToken;
    if (!token) {
      return;
    }
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(SESSION_KEY, JSON.stringify(response));
    this.applySessionContext(response);
    this.loggedIn.next(true);
  }

  private hasValidSession(): boolean {
    const payload = this.decodeToken();
    if (!payload?.exp) {
      return false;
    }
    return payload.exp * 1000 > Date.now();
  }

  private decodeToken(): JwtPayload | null {
    const token = this.getAccessToken();
    if (!token) {
      return null;
    }
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      return JSON.parse(atob(base64)) as JwtPayload;
    } catch {
      return null;
    }
  }
}
