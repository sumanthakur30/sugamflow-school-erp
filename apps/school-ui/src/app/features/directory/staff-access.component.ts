import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { AccountInviteService, RoleTemplate } from '../../core/account-invite.service';
import { environment } from '../../environments/environment';

type StaffAccount = {
  id: number;
  name: string;
  username?: string;
  email?: string;
  role?: string;
  status?: string;
  shopId?: string;
};

type StaffPermissions = {
  userId: number;
  username: string;
  name: string;
  role: string;
  permissions: string[];
};

/** School-relevant subset of platform PermissionCatalog.ALL */
const SCHOOL_PERMISSION_OPTIONS: { code: string; label: string; hint: string }[] = [
  { code: 'MANAGE_STAFF', label: 'Manage staff', hint: 'Invite/edit staff and access controls' },
  { code: 'MANAGE_CUSTOMERS', label: 'People records', hint: 'Students, guardians, directory edits' },
  { code: 'MANAGE_ORDERS', label: 'Operational actions', hint: 'Workflow actions and day-to-day processing' },
  { code: 'MANAGE_FINANCE', label: 'Finance', hint: 'Fee, payroll, and finance screens' },
  { code: 'MANAGE_GST', label: 'GST / tax', hint: 'Tax reporting where enabled' },
  { code: 'PROCUREMENT_VIEW', label: 'Procurement view', hint: 'View purchase / expense data' },
  { code: 'PROCUREMENT_FINANCE', label: 'Procurement finance', hint: 'Approve finance for purchases' },
];

@Component({
  selector: 'sf-staff-access',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './staff-access.component.html',
  styleUrls: ['./staff-access.component.scss', '../../shared/admin-page.scss'],
})
export class StaffAccessComponent implements OnInit {
  private readonly http = inject(HttpClient);
  private readonly invites = inject(AccountInviteService);
  private readonly accountsBase = `${environment.apiBaseUrl}/api/v1/accounts`;

  readonly permissionOptions = SCHOOL_PERMISSION_OPTIONS;

  loading = true;
  saving = false;
  error = '';
  status = '';
  q = '';

  roleTemplates: RoleTemplate[] = [];
  accounts: StaffAccount[] = [];
  selectedId: number | null = null;
  selected: StaffPermissions | null = null;
  draftPermissions: string[] = [];

  ngOnInit(): void {
    this.reload();
  }

  get filteredAccounts(): StaffAccount[] {
    const needle = this.q.trim().toLowerCase();
    if (!needle) {
      return this.accounts;
    }
    return this.accounts.filter((a) => {
      const blob = `${a.name} ${a.username || ''} ${a.email || ''} ${a.role || ''} ${a.status || ''}`.toLowerCase();
      return blob.includes(needle);
    });
  }

  get dirty(): boolean {
    if (!this.selected) {
      return false;
    }
    const a = [...(this.selected.permissions || [])].map((p) => p.toUpperCase()).sort().join('|');
    const b = [...this.draftPermissions].map((p) => p.toUpperCase()).sort().join('|');
    return a !== b;
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.status = '';
    let pending = 2;
    const done = () => {
      pending -= 1;
      if (pending <= 0) {
        this.loading = false;
        if (this.selectedId != null) {
          this.selectAccount(this.selectedId);
        }
      }
    };

    this.invites.listSchoolRoleTemplates().subscribe({
      next: (rows) => {
        this.roleTemplates = (rows || []).filter((r) => r.code !== 'OWNER');
        done();
      },
      error: () => {
        this.roleTemplates = this.invites.localSchoolTemplates().filter((r) => r.code !== 'OWNER');
        done();
      },
    });

    this.http.get<StaffAccount[]>(this.accountsBase).subscribe({
      next: (rows) => {
        this.accounts = rows || [];
        done();
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Could not load staff accounts. Is account-service running?';
        this.accounts = [];
        done();
      },
    });
  }

  selectAccount(id: number): void {
    this.selectedId = id;
    this.error = '';
    this.status = '';
    this.http.get<StaffPermissions>(`${this.accountsBase}/${id}/permissions`).subscribe({
      next: (perms) => {
        this.selected = perms;
        this.draftPermissions = [...(perms.permissions || [])];
      },
      error: (err) => {
        this.selected = null;
        this.draftPermissions = [];
        this.error = err?.error?.message || err?.message || 'Could not load permissions for this account.';
      },
    });
  }

  isOn(code: string): boolean {
    return this.draftPermissions.some((p) => p.toUpperCase() === code.toUpperCase());
  }

  toggle(code: string): void {
    if (this.saving) {
      return;
    }
    const upper = code.toUpperCase();
    if (this.isOn(upper)) {
      this.draftPermissions = this.draftPermissions.filter((p) => p.toUpperCase() !== upper);
    } else {
      this.draftPermissions = [...this.draftPermissions, upper];
    }
  }

  save(): void {
    if (!this.selected || this.saving || !this.dirty) {
      return;
    }
    this.saving = true;
    this.error = '';
    this.status = '';
    this.http
      .put<StaffPermissions>(`${this.accountsBase}/${this.selected.userId}/permissions`, {
        permissions: this.draftPermissions,
      })
      .subscribe({
        next: (perms) => {
          this.selected = perms;
          this.draftPermissions = [...(perms.permissions || [])];
          this.status =
            `Saved permissions for ${perms.name || perms.username}. ` +
            `They must sign out and sign in again for menu limits to apply.`;
          this.saving = false;
          // Best-effort sync into auth JWT store
          this.http.post(`${this.accountsBase}/${perms.userId}/permissions/sync-auth`, {}).subscribe({
            next: () => undefined,
            error: () => undefined,
          });
        },
        error: (err) => {
          this.error = err?.error?.message || err?.message || 'Save failed.';
          this.saving = false;
        },
      });
  }

  resetDraft(): void {
    if (this.selected) {
      this.draftPermissions = [...(this.selected.permissions || [])];
    }
  }
}
