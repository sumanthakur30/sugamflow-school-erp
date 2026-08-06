import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../environments/environment';
import {
  SESSION_ACADEMIC_KEY,
  SESSION_BRANCH_KEY,
  SESSION_DATA_KEY,
  SESSION_ROLE_KEY,
  SESSION_TENANT_KEY,
  SESSION_TOKEN_KEY,
  SESSION_USER_KEY,
} from './session-keys';
import { TenantContextService } from './tenant-context.service';
import { AdmissionBootstrapService } from './admission-bootstrap.service';
import { ModuleBootstrapService } from './module-bootstrap.service';
import { resolveOrganizationId } from './tenant-context.util';

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
  permissions?: string[] | string;
}

@Injectable({ providedIn: 'root' })
export class AuthSessionService {
  private readonly http = inject(HttpClient);
  private readonly tenantContext = inject(TenantContextService);
  private readonly admissionBootstrap = inject(AdmissionBootstrapService);
  private readonly moduleBootstrap = inject(ModuleBootstrapService);
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

  validateInvitation(token: string): Observable<{ valid: boolean; shopId?: string; username?: string }> {
    return this.http.get<{ valid: boolean; shopId?: string; username?: string }>(
      `${this.authBase}/invitations/validate`,
      { params: { token } },
    );
  }

  acceptInvitation(token: string, password: string): Observable<AuthResponse> {
    // Do not auto-store session — staff should sign in on /login after activating.
    return this.http.post<AuthResponse>(`${this.authBase}/invitations/accept`, { token, password });
  }

  logout(): void {
    localStorage.removeItem(SESSION_TOKEN_KEY);
    localStorage.removeItem(SESSION_DATA_KEY);
    localStorage.removeItem(SESSION_TENANT_KEY);
    localStorage.removeItem(SESSION_BRANCH_KEY);
    localStorage.removeItem(SESSION_ACADEMIC_KEY);
    localStorage.removeItem(SESSION_USER_KEY);
    localStorage.removeItem(SESSION_ROLE_KEY);
    // Belt-and-suspenders: never leave a stale org slug for the next login paint.
    localStorage.removeItem(SESSION_TENANT_KEY);
    this.tenantContext.markCampusPending();
    this.admissionBootstrap.invalidate();
    this.moduleBootstrap.invalidate();
    this.loggedIn.next(false);
  }

  isLoggedIn(): boolean {
    return this.loggedIn.value;
  }

  getAccessToken(): string | null {
    return localStorage.getItem(SESSION_TOKEN_KEY);
  }

  getSession(): AuthResponse | null {
    const raw = localStorage.getItem(SESSION_DATA_KEY);
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
    return localStorage.getItem(SESSION_ROLE_KEY) ?? this.getSession()?.role ?? this.decodeToken()?.role ?? null;
  }

  /** Permission codes from JWT (synced from account-service on invite / Staff access save). */
  getPermissions(): string[] {
    const payload = this.decodeToken();
    const raw = payload?.permissions;
    if (Array.isArray(raw)) {
      return raw
        .map((p) => String(p || '').trim().toUpperCase())
        .filter(Boolean);
    }
    if (typeof raw === 'string' && raw.trim()) {
      return raw
        .split(/[,\s]+/)
        .map((p) => p.trim().toUpperCase())
        .filter(Boolean);
    }
    return [];
  }

  /**
   * True if the user may use a capability.
   * School Owner / Super Admin always pass. When the JWT has no permissions claim yet
   * (legacy sessions), role-based nav continues to apply (fail open on permissions).
   */
  hasPermission(code: string): boolean {
    const role = (this.getRole() || '').toUpperCase();
    if (role === 'SHOP_OWNER' || role === 'SUPER_ADMIN' || role === 'OWNER') {
      return true;
    }
    const need = (code || '').trim().toUpperCase();
    if (!need) {
      return true;
    }
    const held = this.getPermissions();
    if (!held.length) {
      return true;
    }
    return held.includes(need);
  }

  /** True if any of the codes is granted (or no codes required). */
  hasAnyPermission(codes?: string[] | null): boolean {
    if (!codes?.length) {
      return true;
    }
    return codes.some((c) => this.hasPermission(c));
  }

  /** Override role header for portal experiences (PARENT / TEACHER) without re-login. */
  setActiveRole(role: string): void {
    if (role && role.trim()) {
      localStorage.setItem(SESSION_ROLE_KEY, role.trim().toUpperCase());
    } else {
      localStorage.removeItem(SESSION_ROLE_KEY);
    }
  }

  /** Organization slug for school APIs (maps from auth shopId / JWT). */
  getOrganizationId(): string {
    // Prefer JWT shopId over stale sf.tenantId (same rules as API interceptors).
    return resolveOrganizationId();
  }

  getBranchId(): string {
    return localStorage.getItem(SESSION_BRANCH_KEY) ?? 'main';
  }

  setBranchId(branchKey: string): void {
    const key = (branchKey || 'main').trim();
    localStorage.setItem(SESSION_BRANCH_KEY, key || 'main');
  }

  getSessionId(): string {
    return localStorage.getItem(SESSION_ACADEMIC_KEY) ?? '2025-26';
  }

  setSessionId(sessionId: string): void {
    localStorage.setItem(SESSION_ACADEMIC_KEY, (sessionId || '2025-26').trim());
  }

  applySessionContext(response: AuthResponse): void {
    const nextOrg = response.shopId;
    localStorage.setItem(SESSION_TENANT_KEY, nextOrg);
    // Always reset campus on login so demo's rbac-*/rbsc-* keys never ride into HCP KPIs.
    localStorage.setItem(SESSION_BRANCH_KEY, 'main');
    localStorage.setItem(SESSION_ACADEMIC_KEY, '2025-26');
    localStorage.setItem(SESSION_USER_KEY, response.username);
    localStorage.setItem(SESSION_ROLE_KEY, response.role);
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
    localStorage.setItem(SESSION_TOKEN_KEY, token);
    localStorage.setItem(SESSION_DATA_KEY, JSON.stringify(response));
    this.applySessionContext(response);
    // Drop previous school's bootstraps so demo → HCP cannot reuse cached module data.
    this.tenantContext.markCampusPending();
    this.admissionBootstrap.invalidate();
    this.moduleBootstrap.invalidate();
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
