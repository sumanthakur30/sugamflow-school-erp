import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Subscription } from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import { ListToolbarComponent } from '../../shared/list-toolbar/list-toolbar.component';
import { ListSortOption, pageMeta, sortRows } from '../../shared/list-toolbar/list-controls';

@Component({
  selector: 'sf-import-workbench',
  standalone: true,
  imports: [CommonModule, FormsModule, ListToolbarComponent],
  templateUrl: './import-workbench.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    './import-workbench.component.scss',
  ],
})
export class ImportWorkbenchComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

  loading = true;
  busy = false;
  error = '';
  status = '';
  featureEnabled = false;
  targetFields: string[] = [];
  jobs: any[] = [];
  selected: any = null;
  selectedFileName = '';
  showPasteMode = false;

  listQ = '';
  sortBy = 'updatedAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageIndex = 0;
  pageSize = 50;
  searching = false;
  page: PageResult<any> = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    hasNext: false,
  };
  readonly sortOptions: ListSortOption[] = [
    { key: 'updatedAt', label: 'Updated' },
    { key: 'createdAt', label: 'Created' },
    { key: 'fileName', label: 'File Name' },
    { key: 'status', label: 'Status' },
  ];

  csvText = '';
  fileBase64 = '';
  fileKind: 'csv' | 'xlsx' | '' = '';
  importMode: 'CREATE' | 'UPDATE' | 'UPSERT' = 'CREATE';
  columns: Array<{ key: string; label: string; required?: boolean }> = [];

  ngOnInit(): void {
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    const path = '/api/student/import/bootstrap';
    const apply = (b: any) => {
      this.featureEnabled = !!b.featureEnabled;
      this.targetFields = b.targetFields || [];
      this.columns = Array.isArray(b.columns) ? b.columns : [];
      this.loading = false;
      this.loadJobs();
    };
    const peeked = this.modules.peek(path);
    if (peeked) {
      apply(peeked);
      return;
    }
    this.modules.load(path).subscribe({
      next: apply,
      error: (err) => {
        this.loading = false;
        this.error = ModuleBootstrapService.errorMessage(err, 'Import bootstrap failed');
        if (ModuleBootstrapService.isFeatureDisabled(err)) {
          this.featureEnabled = false;
        } else {
          // Do NOT imply plan feature is off on workflow/form/network errors
          this.featureEnabled = true;
        }
      },
    });
  }

  loadJobs(): void {
    this.searching = true;
    this.api
      .getPage<any>('/api/student/import/jobs', this.pageIndex, this.pageSize, {
        q: this.listQ || undefined,
      })
      .subscribe({
        next: (p) => {
          let items = p.items || [];
          if (this.listQ?.trim()) {
            const q = this.listQ.trim().toLowerCase();
            items = items.filter((row) => {
              const fileName = String(row.fileName ?? '').toLowerCase();
              const status = String(row.status ?? '').toLowerCase();
              const id = String(row.id ?? '').toLowerCase();
              return fileName.includes(q) || status.includes(q) || id.includes(q);
            });
          }
          items = sortRows(items, this.sortBy, this.sortDir, (row, key) => {
            if (key === 'updatedAt') return row.updatedAt || row.createdAt;
            return row?.[key];
          });
          this.page = { ...p, items };
          this.jobs = items;
          this.searching = false;
        },
        error: (err) => {
          this.searching = false;
          this.error = err?.error?.message ?? 'Failed to list jobs';
        },
      });
  }

  get showingFrom(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .from;
  }

  get showingTo(): number {
    return pageMeta(this.page.page, this.page.size || this.pageSize, this.page.totalElements || 0)
      .to;
  }

  get listClearEnabled(): boolean {
    return (
      !!this.listQ ||
      this.sortBy !== 'updatedAt' ||
      this.sortDir !== 'DESC' ||
      this.pageSize !== 50
    );
  }

  clearListFilters(): void {
    this.listQ = '';
    this.sortBy = 'updatedAt';
    this.sortDir = 'DESC';
    this.pageSize = 50;
    this.pageIndex = 0;
    this.loadJobs();
  }

  get sourceRowCount(): number {
    if (this.fileKind === 'xlsx') {
      return this.fileBase64 ? 1 : 0;
    }
    const lines = this.csvText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
    return Math.max(0, lines.length - 1);
  }

  get canReview(): boolean {
    if (this.busy) {
      return false;
    }
    if (this.fileKind === 'xlsx') {
      return !!this.fileBase64;
    }
    return !!this.csvText.trim() && this.sourceRowCount > 0;
  }

  get canCommit(): boolean {
    const ready = Number(this.selected?.stats?.ready || 0);
    const errors = Number(this.selected?.stats?.error || 0);
    return (
      !!this.selected?.id &&
      ready > 0 &&
      errors === 0 &&
      this.selected?.status === 'VALIDATED'
    );
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    const name = file.name.toLowerCase();
    const excel = name.endsWith('.xlsx');
    const csv = name.endsWith('.csv');
    if (!excel && !csv) {
      this.error = 'Choose a CSV or Excel file';
      input.value = '';
      return;
    }
    this.selectedFileName = file.name;
    this.selected = null;
    this.error = '';
    this.fileKind = excel ? 'xlsx' : 'csv';
    this.fileBase64 = '';
    this.csvText = '';
    const reader = new FileReader();
    reader.onerror = () => {
      this.error = 'The selected file could not be read';
    };
    if (excel) {
      reader.onload = () => {
        const bytes = new Uint8Array(reader.result as ArrayBuffer);
        let binary = '';
        const chunk = 0x8000;
        for (let i = 0; i < bytes.length; i += chunk) {
          binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
        }
        this.fileBase64 = btoa(binary);
        this.csvText = '';
        this.status = `${file.name} selected`;
      };
      reader.readAsArrayBuffer(file);
      return;
    }
    reader.onload = () => {
      this.csvText = String(reader.result || '');
      this.fileBase64 = '';
      this.status = `${file.name} selected · ${this.sourceRowCount} student rows`;
    };
    reader.readAsText(file);
  }

  downloadTemplate(): void {
    this.api.getBlob('/api/student/import/template.xlsx').subscribe({
      next: (blob) => this.saveBlob(blob, 'student-import-template.xlsx'),
      error: () => (this.error = 'Could not download the Excel template'),
    });
  }

  downloadErrors(): void {
    if (!this.selected?.id) {
      return;
    }
    this.api.getBlob(`/api/student/import/jobs/${this.selected.id}/errors.xlsx`).subscribe({
      next: (blob) => this.saveBlob(blob, 'student-import-errors.xlsx'),
      error: () => (this.error = 'Could not download the error workbook'),
    });
  }

  clearSource(): void {
    this.csvText = '';
    this.fileBase64 = '';
    this.fileKind = '';
    this.selectedFileName = '';
    this.selected = null;
    this.status = '';
  }

  createJob(): void {
    if (!this.canReview) {
      this.error = 'Select a CSV or Excel file containing at least one student row';
      return;
    }
    this.busy = true;
    this.status = '';
    this.error = '';
    this.api.post<any>('/api/student/import/jobs', this.jobBody()).subscribe({
      next: (job) => {
        this.busy = false;
        this.selected = job;
        const ready = job.stats?.ready || 0;
        const errors = job.stats?.error || 0;
        const skipped = job.stats?.skipped || 0;
        const updates = job.stats?.updates || 0;
        this.status = `File prepared for review · ${ready} ready · ${updates} updates · ${skipped} already on file · ${errors} need attention`;
        this.loadJobs();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Create job failed';
      },
    });
  }

  private jobBody(): Record<string, unknown> {
    const body: Record<string, unknown> = {
      fileName: this.selectedFileName || 'pasted-students.csv',
      entityType: 'student_master',
      mode: this.importMode,
    };
    if (this.fileKind === 'xlsx' && this.fileBase64) {
      body['fileBase64'] = this.fileBase64;
      return body;
    }
    body['csvText'] = this.csvText;
    return body;
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = fileName;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  open(job: any): void {
    this.api.get<any>(`/api/student/import/jobs/${job.id}`).subscribe({
      next: (full) => (this.selected = full),
      error: (err) => (this.error = err?.error?.message ?? 'Failed to load job'),
    });
  }

  dryRun(): void {
    if (!this.selected?.id) return;
    this.busy = true;
    this.api.post<any>(`/api/student/import/jobs/${this.selected.id}/dry-run`, {}).subscribe({
      next: (job) => {
        this.busy = false;
        this.selected = job;
        this.status = `Validated · ready=${job.stats?.ready} error=${job.stats?.error}`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Dry-run failed';
      },
    });
  }

  commit(): void {
    if (!this.canCommit) {
      this.error = 'Resolve validation errors before importing students';
      return;
    }
    this.busy = true;
    this.api.post<any>(`/api/student/import/jobs/${this.selected.id}/commit`, {}).subscribe({
      next: (job) => {
        this.busy = false;
        this.selected = job;
        this.status = `Saved ${job.stats?.committed || 0} students · ${job.stats?.updatedCommitted || job.stats?.updates || 0} updated`;
        this.loadJobs();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Commit failed';
      },
    });
  }

  get importActionLabel(): string {
    const ready = this.selected?.stats?.ready || 0;
    const mode = String(this.selected?.mode || this.importMode);
    if (mode === 'UPDATE') {
      return `Update ${ready} students`;
    }
    if (mode === 'UPSERT') {
      return `Save ${ready} students`;
    }
    return `Import ${ready} students`;
  }

  modeLabel(value: string | null | undefined): string {
    const mode = String(value || 'CREATE').toUpperCase();
    if (mode === 'UPDATE') return 'Update';
    if (mode === 'UPSERT') return 'Create or update';
    return 'Create';
  }

  unmappedColumns(): string[] {
    const raw = this.selected?.stats?.unmapped;
    return Array.isArray(raw) ? raw.map((item) => String(item)) : [];
  }

  statusLabel(value: string | null | undefined): string {
    const status = String(value || '').toUpperCase();
    const labels: Record<string, string> = {
      MAPPED: 'Ready to validate',
      VALIDATED: 'Validation complete',
      READY: 'Ready to import',
      INVALID: 'Needs correction',
      COMMITTED: 'Imported',
      UPDATED: 'Updated',
      SKIPPED: 'Already on file',
      ERROR: 'Needs attention',
      FAILED: 'Needs attention',
    };
    return labels[status] || status.replace(/_/g, ' ') || 'Draft';
  }
}
