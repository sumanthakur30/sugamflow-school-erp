import { Component, EventEmitter, OnInit, Output, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';
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
  private readonly theme = inject(ThemeService);

  @Output() readonly branchChanged = new EventEmitter<string>();

  branches: Array<{ branchKey: string; name: string; city?: string; status?: string }> = [];
  currentKey = 'main';
  featureEnabled = false;
  loading = true;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.api.get<any>('/api/config/branches/bootstrap').subscribe({
      next: (boot) => {
        this.featureEnabled = !!boot.featureEnabled;
        this.branches = boot.branches ?? [];
        this.currentKey = boot.currentBranchKey || this.auth.getBranchId();
        this.loading = false;
        if (this.currentKey && this.currentKey !== this.auth.getBranchId()) {
          this.auth.setBranchId(String(this.currentKey));
        }
      },
      error: () => {
        this.loading = false;
        this.branches = [{ branchKey: this.auth.getBranchId(), name: this.auth.getBranchId() }];
        this.currentKey = this.auth.getBranchId();
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
