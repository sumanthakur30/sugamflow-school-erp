import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';

@Component({
  selector: 'sf-campus-desk',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './campus-desk.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class CampusDeskComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly tenantContext = inject(TenantContextService);
  private campusReadySub?: Subscription;

  kind = 'LEAVE';
  title = 'Leave';
  loading = false;
  busy = false;
  error = '';
  status = '';
  items: any[] = [];
  catalog: any[] = [];

  draft = {
    subjectType: 'STUDENT',
    subjectRef: '',
    subjectName: '',
    title: '',
    note: '',
    catalogKey: '',
    days: 1,
    yearlyQuota: 12,
  };

  ngOnInit(): void {
    this.route.data.subscribe((data) => {
      this.kind = String(data['kind'] || 'LEAVE');
      this.title = String(data['title'] || 'Desk');
    });
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  get catalogKind(): string {
    return this.kind === 'LEAVE' ? 'LEAVE_TYPE' : 'VISIT_PURPOSE';
  }

  get catalogLabel(): string {
    return this.kind === 'LEAVE' ? 'Leave type' : 'Visit purpose';
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any[]>(`/api/student/desk/${this.kind}`).subscribe({
      next: (rows) => {
        this.items = rows || [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Could not load records';
      },
    });
    this.api.get<any[]>(`/api/student/desk/${this.catalogKind}`).subscribe({
      next: (rows) => {
        this.catalog = rows || [];
      },
      error: () => {
        this.catalog = [];
      },
    });
    if (this.kind === 'GATE_PASS') {
      this.api.get<any[]>('/api/student/desk/EXIT_GATE').subscribe({
        next: (rows) => {
          this.catalog = [...this.catalog, ...(rows || [])];
        },
        error: () => undefined,
      });
    }
  }

  create(): void {
    if (!this.draft.subjectName.trim() && !this.draft.subjectRef.trim()) {
      this.error = 'Enter a student or staff name, or an admission number.';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<any>(`/api/student/desk/${this.kind}`, {
        subjectType: this.draft.subjectType,
        subjectRef: this.draft.subjectRef.trim(),
        subjectName: this.draft.subjectName.trim(),
        title: this.draft.title.trim() || this.draft.catalogKey || this.title,
        note: this.draft.note.trim(),
        payload: {
          catalogKey: this.draft.catalogKey,
          days: Number(this.draft.days) > 0 ? Number(this.draft.days) : 1,
        },
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `${this.title} saved`;
          this.draft.title = '';
          this.draft.note = '';
          this.draft.subjectRef = '';
          this.draft.subjectName = '';
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save failed';
        },
      });
  }

  addCatalog(): void {
    const label = this.draft.catalogKey.trim();
    if (!label) return;
    this.api
      .post(`/api/student/desk/${this.catalogKind}`, {
        title: label,
        subjectType: 'SETTING',
        subjectName: label,
        payload:
          this.kind === 'LEAVE'
            ? { yearlyQuota: Number(this.draft.yearlyQuota) > 0 ? Number(this.draft.yearlyQuota) : 12 }
            : {},
      })
      .subscribe({
        next: () => {
          this.draft.catalogKey = '';
          this.reload();
        },
        error: (err) => {
          this.error = err?.error?.message ?? 'Could not add setting';
        },
      });
  }

  setStatus(row: any, status: string): void {
    this.api.post(`/api/student/desk/items/${row.id}/status`, { status }).subscribe({
      next: () => this.reload(),
      error: (err) => {
        this.error = err?.error?.message ?? 'Status update failed';
      },
    });
  }
}
