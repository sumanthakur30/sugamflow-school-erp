import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService, PageResult } from '../../core/api.service';
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
export class ImportWorkbenchComponent implements OnInit {
  private readonly api = inject(ApiService);

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

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/student/import/bootstrap').subscribe({
      next: (b) => {
        this.featureEnabled = !!b.featureEnabled;
        this.targetFields = b.targetFields || [];
        this.loading = false;
        this.loadJobs();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Import bootstrap failed';
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
    const lines = this.csvText
      .split(/\r?\n/)
      .map((line) => line.trim())
      .filter(Boolean);
    return Math.max(0, lines.length - 1);
  }

  get canReview(): boolean {
    return !!this.csvText.trim() && this.sourceRowCount > 0 && !this.busy;
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
    if (!file.name.toLowerCase().endsWith('.csv')) {
      this.error = 'Choose a CSV file';
      input.value = '';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.csvText = String(reader.result || '');
      this.selectedFileName = file.name;
      this.selected = null;
      this.error = '';
      this.status = `${file.name} selected · ${this.sourceRowCount} student rows`;
    };
    reader.onerror = () => {
      this.error = 'The selected file could not be read';
    };
    reader.readAsText(file);
  }

  downloadTemplate(): void {
    const headers = this.targetFields.length
      ? this.targetFields
      : ['fullName', 'admissionNo', 'age', 'mobile', 'email', 'classApplied', 'gender', 'rollNo'];
    const example: Record<string, string> = {
      fullName: 'Asha Rao',
      admissionNo: 'ADM-001',
      age: '12',
      mobile: '9800000001',
      email: 'asha@example.com',
      classApplied: 'Grade 8-A',
      gender: 'Female',
      rollNo: '1',
    };
    const csv = `${headers.join(',')}\n${headers.map((key) => example[key] || '').join(',')}\n`;
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'student-import-template.csv';
    anchor.click();
    URL.revokeObjectURL(url);
  }

  clearSource(): void {
    this.csvText = '';
    this.selectedFileName = '';
    this.selected = null;
    this.status = '';
  }

  createJob(): void {
    if (!this.canReview) {
      this.error = 'Select a CSV file containing at least one student row';
      return;
    }
    this.busy = true;
    this.status = '';
    this.error = '';
    this.api
      .post<any>('/api/student/import/jobs', {
        fileName: this.selectedFileName || 'pasted-students.csv',
        entityType: 'student_master',
        csvText: this.csvText,
      })
      .subscribe({
        next: (job) => {
          this.busy = false;
          this.selected = job;
          this.status = `File prepared for review · ${job.stats?.ready || 0} ready · ${job.stats?.error || 0} need attention`;
          this.loadJobs();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Create job failed';
        },
      });
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
        this.status = `Committed ${job.stats?.committed} rows`;
        this.loadJobs();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Commit failed';
      },
    });
  }

  statusLabel(value: string | null | undefined): string {
    const status = String(value || '').toUpperCase();
    const labels: Record<string, string> = {
      MAPPED: 'Ready to validate',
      VALIDATED: 'Validation complete',
      READY: 'Ready to import',
      INVALID: 'Needs correction',
      COMMITTED: 'Imported',
      FAILED: 'Needs attention',
    };
    return labels[status] || status.replace(/_/g, ' ') || 'Draft';
  }
}
