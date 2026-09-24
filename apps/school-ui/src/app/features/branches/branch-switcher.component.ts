import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ThemeService } from '../../core/theme.service';

@Component({
  selector: 'sf-branch-switcher',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './branch-switcher.component.html',
  styleUrl: './branch-switcher.component.scss',
})
export class BranchSwitcherComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthSessionService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly theme = inject(ThemeService);

  @Output() readonly branchChanged = new EventEmitter<string>();

  branches: Array<{
    branchKey: string;
    name: string;
    city?: string;
    status?: string;
    organizationId?: string;
  }> = [];
  currentKey = 'main';
  featureEnabled = false;
  loading = true;
  private loadGeneration = 0;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    const generation = ++this.loadGeneration;
    const orgAtStart = this.auth.getOrganizationId();
    this.api.get<any>('/api/config/branches/bootstrap').subscribe({
      next: (boot) => {
        // Ignore late responses from a previous school after demo → HCP switch.
        if (
          generation !== this.loadGeneration ||
          this.auth.getOrganizationId() !== orgAtStart
        ) {
          return;
        }
        const bootBranches = boot.branches ?? [];
        const bootOrg = String(
          boot.currentBranch?.organizationId || bootBranches[0]?.organizationId || '',
        ).trim();
        // Drop demo-school bootstrap while session is HCP-01 (cached/wrong tenant).
        if (bootOrg && orgAtStart && bootOrg.toLowerCase() !== orgAtStart.toLowerCase()) {
          this.loading = false;
          this.branches = [{ branchKey: 'main', name: 'Main Campus', organizationId: orgAtStart }];
          this.currentKey = 'main';
          this.auth.setBranchId('main');
          this.tenantContext.markCampusReady();
          return;
        }
        this.featureEnabled = !!boot.featureEnabled;
        this.branches = bootBranches;
        const keys = this.branches.map((b) => String(b.branchKey || ''));
        const serverKey = boot.currentBranchKey ? String(boot.currentBranchKey) : '';
        const localKey = this.auth.getBranchId();
        let nextKey = localKey;
        if (localKey && keys.includes(localKey)) {
          nextKey = localKey;
        } else if (keys.includes('main')) {
          nextKey = 'main';
        } else if (serverKey && keys.includes(serverKey)) {
          nextKey = serverKey;
        } else if (keys.length) {
          nextKey = keys[0];
        }
        this.currentKey = nextKey || 'main';
        this.loading = false;
        const branchChanged = !!this.currentKey && this.currentKey !== this.auth.getBranchId();
        if (branchChanged || !this.auth.getBranchId()) {
          this.auth.setBranchId(String(this.currentKey));
        }
        this.tenantContext.markCampusReady();
        if (branchChanged) {
          this.branchChanged.emit(String(this.currentKey));
        }
      },
      error: () => {
        if (
          generation !== this.loadGeneration ||
          this.auth.getOrganizationId() !== orgAtStart
        ) {
          return;
        }
        this.loading = false;
        this.branches = [{ branchKey: this.auth.getBranchId() || 'main', name: 'Main Campus' }];
        this.currentKey = this.auth.getBranchId() || 'main';
        this.auth.setBranchId(this.currentKey);
        this.tenantContext.markCampusReady();
      },
    });
  }

  onChange(key: string): void {
    if (!key || key === this.auth.getBranchId()) {
      return;
    }
    this.auth.setBranchId(key);
    this.currentKey = key;
    this.theme.loadAuthenticated().subscribe();
    this.branchChanged.emit(key);
  }
}
