import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'sf-branches',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './branches.component.html',
  styleUrls: ['../../shared/admin-page.scss', './branches.component.scss'],
})
export class BranchesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly theme = inject(ThemeService);

  loading = true;
  featureEnabled = false;
  canAdd = false;
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
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/config/branches/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.canAdd = !!boot.canAdd;
        this.maxBranches = boot.maxBranches;
        this.branchCount = boot.branchCount ?? 0;
        this.currentKey = boot.currentBranchKey || this.auth.getBranchId();
        this.branches = boot.branches ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load branches';
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
          this.reload();
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
        this.reload();
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
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Update failed';
        },
      });
  }
}
