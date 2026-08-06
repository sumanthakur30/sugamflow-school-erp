import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { ThemeService } from '../../core/theme.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';

@Component({
  selector: 'sf-branches',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './branches.component.html',
  styleUrls: ['../../shared/admin-page.scss', './branches.component.scss'],
})
export class BranchesComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly theme = inject(ThemeService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  featureEnabled = false;
  canAdd = false;
  canManage = false;
  maxBranches: number | string = 1;
  branchCount = 0;
  currentKey = 'main';
  branches: any[] = [];
  error = '';
  status = '';
  busy = false;

  draft = {
    branchKey: '',
    name: '',
    code: '',
    city: '',
    address: '',
    isDefault: false,
  };

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(force = false): void {
    this.loading = true;
    this.error = '';
    const path = '/api/config/branches/bootstrap';
    const apply = (boot: any) => {
      this.featureEnabled = !!boot.featureEnabled;
      this.canAdd = !!boot.canAdd;
      this.canManage = !!boot.canManage;
      this.maxBranches = boot.maxBranches;
      this.branchCount = boot.branchCount ?? 0;
      this.currentKey = boot.currentBranchKey || this.auth.getBranchId();
      this.branches = boot.branches ?? [];
      this.loading = false;
    };
    const peeked = force ? null : this.modules.peek(path);
    if (peeked) {
      apply(peeked);
      return;
    }
    this.modules.load(path, force).subscribe({
      next: apply,
      error: (err) => {
        this.loading = false;
        this.error = ModuleBootstrapService.errorMessage(err, 'Failed to load branches');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  switchTo(key: string): void {
    this.auth.setBranchId(key);
    this.currentKey = key;
    this.theme.loadAuthenticated().subscribe();
    this.status = `Switched to campus ${key}`;
  }

  create(): void {
    if (!this.draft.branchKey.trim() || !this.draft.name.trim()) {
      this.error = 'Branch key and name are required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post('/api/config/branches', {
        branchKey: this.draft.branchKey.trim(),
        name: this.draft.name.trim(),
        code: this.draft.code.trim() || undefined,
        city: this.draft.city.trim() || undefined,
        address: this.draft.address.trim() || undefined,
        isDefault: this.draft.isDefault,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Campus ${this.draft.branchKey} created`;
          this.draft = {
            branchKey: '',
            name: '',
            code: '',
            city: '',
            address: '',
            isDefault: false,
          };
          this.reload(true);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create failed';
        },
      });
  }

  setDefault(branchKey: string): void {
    this.busy = true;
    this.api.put(`/api/config/branches/${branchKey}`, { isDefault: true }).subscribe({
      next: () => {
        this.busy = false;
        this.status = `${branchKey} is now the default campus`;
        this.reload(true);
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Update failed';
      },
    });
  }

  saveMeta(b: any): void {
    this.busy = true;
    this.api
      .put(`/api/config/branches/${b.branchKey}`, {
        name: b.name,
        code: b.code,
        city: b.city,
        address: b.address,
        status: b.status,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Updated ${b.branchKey}`;
          this.reload(true);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Update failed';
        },
      });
  }
}
