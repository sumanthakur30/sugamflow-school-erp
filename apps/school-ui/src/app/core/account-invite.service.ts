import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../environments/environment';

export interface RoleTemplate {
  businessTypeCode: string;
  code: string;
  name: string;
  description?: string | null;
  authRole: string;
  employeeType?: string | null;
  sortOrder: number;
  permissions: string[];
}

export interface StaffLoginInviteRequest {
  name: string;
  username: string;
  email: string;
  phone: string;
  address: string;
  role: string;
  templateCode: string;
  businessType: string;
}

export interface StaffLoginInviteResponse {
  userId: number;
  username: string;
  email: string;
  role: string;
  status: string;
  inviteExpiresAt: string;
  /** Raw set-password token — sensitive; use only to build the invite link. */
  token: string;
}

const LOCAL_SCHOOL_TEMPLATES: RoleTemplate[] = [
  {
    businessTypeCode: 'SCHOOL',
    code: 'TEACHER',
    name: 'Teacher',
    description: 'Attendance, homework, marks, timetable',
    authRole: 'SHOP_EMPLOYEE',
    employeeType: 'TEACHING',
    sortOrder: 10,
    permissions: [],
  },
  {
    businessTypeCode: 'SCHOOL',
    code: 'CLASS_TEACHER',
    name: 'Class Teacher',
    description: 'Teacher + class ownership',
    authRole: 'SHOP_EMPLOYEE',
    employeeType: 'TEACHING',
    sortOrder: 15,
    permissions: [],
  },
  {
    businessTypeCode: 'SCHOOL',
    code: 'PRINCIPAL',
    name: 'Principal',
    description: 'Most school modules except platform billing',
    authRole: 'SHOP_EMPLOYEE',
    employeeType: 'LEADERSHIP',
    sortOrder: 5,
    permissions: [],
  },
  {
    businessTypeCode: 'SCHOOL',
    code: 'RECEPTION',
    name: 'Reception',
    description: 'Admission, visitor, student search',
    authRole: 'SHOP_EMPLOYEE',
    employeeType: 'FRONT_OFFICE',
    sortOrder: 20,
    permissions: [],
  },
  {
    businessTypeCode: 'SCHOOL',
    code: 'ACCOUNTANT',
    name: 'Accountant',
    description: 'Fee, salary, expense, reports',
    authRole: 'SHOP_EMPLOYEE',
    employeeType: 'FINANCE',
    sortOrder: 30,
    permissions: [],
  },
];

@Injectable({ providedIn: 'root' })
export class AccountInviteService {
  private readonly http = inject(HttpClient);
  private readonly accountsBase = `${environment.apiBaseUrl}/api/v1/accounts`;

  listSchoolRoleTemplates(): Observable<RoleTemplate[]> {
    return this.http.get<RoleTemplate[]>(`${this.accountsBase}/role-templates`, {
      params: { businessType: 'SCHOOL' },
    });
  }

  /** Fallback when role-templates API is unreachable. */
  localSchoolTemplates(): RoleTemplate[] {
    return [...LOCAL_SCHOOL_TEMPLATES].sort((a, b) => a.sortOrder - b.sortOrder);
  }

  inviteStaff(request: StaffLoginInviteRequest): Observable<StaffLoginInviteResponse> {
    return this.http.post<StaffLoginInviteResponse>(`${this.accountsBase}/invite`, request);
  }

  reissueInvite(accountId: number): Observable<StaffLoginInviteResponse> {
    return this.http.post<StaffLoginInviteResponse>(
      `${this.accountsBase}/${accountId}/invite/reissue`,
      {},
    );
  }

  buildSetPasswordUrl(token: string): string {
    const base = `${window.location.origin}/onboarding/set-password`;
    return `${base}?token=${encodeURIComponent(token)}`;
  }
}
