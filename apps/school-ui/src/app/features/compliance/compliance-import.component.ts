import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  ImportBootstrap,
  ImportJob,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-import',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Import Center</h2>
          <p>
            Gap-fill student/staff answers from CSV or Excel using the active board field map. Rows
            match on admission / employee number — masters are never created here.
          </p>
        </div>
        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
          <a routerLink="/admin/compliance" class="btn">Dashboard</a>
          <a routerLink="/admin/compliance/readiness" class="btn">Data Readiness</a>
        </div>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c">
          <p>{{ error }}</p>
        </div>
      }

      @if (hint) {
        <div class="panel" style="margin-bottom: 1rem">
          <p>{{ hint }}</p>
        </div>
      }

      @if (bootstrap) {
        <div class="panel" style="margin-bottom: 1rem">
          <p class="muted" style="margin-top: 0">
            Board <strong>{{ bootstrap.boardCode }}</strong>
            @if (bootstrap.packKey) {
              · pack {{ bootstrap.packKey }}
            }
          </p>
          <div class="grid two">
            <label>
              <span class="field-title">Entity</span>
              <select [(ngModel)]="entityType" (ngModelChange)="onEntityChange()">
                <option value="STUDENT">Student</option>
                <option value="STAFF">Staff</option>
              </select>
            </label>
            <label style="display: flex; align-items: flex-end; gap: 0.5rem; padding-bottom: 0.35rem">
              <input type="checkbox" [(ngModel)]="fillBlankOnly" />
              <span>Fill blank fields only (recommended)</span>
            </label>
          </div>
          <div style="display: flex; gap: 0.5rem; flex-wrap: wrap; margin-top: 0.75rem">
            <button class="btn" type="button" (click)="downloadTemplate()">Download template</button>
            <label class="btn primary" style="cursor: pointer; margin: 0">
              {{ uploading ? 'Uploading…' : 'Upload CSV / XLSX' }}
              <input
                type="file"
                accept=".csv,.xlsx,.xls"
                hidden
                [disabled]="uploading"
                (change)="onFileSelected($event)"
              />
            </label>
          </div>
          <p class="muted" style="margin-bottom: 0">
            Match key:
            {{ entityType === 'STAFF' ? bootstrap.matchKeyStaff : bootstrap.matchKeyStudent }}.
            Columns follow the active pack field map.
          </p>
        </div>

        <div class="panel" style="margin-bottom: 1rem">
          <h3 style="margin-top: 0; font-size: 1.1rem">Template columns</h3>
          <table class="data">
            <thead>
              <tr>
                <th>Field</th>
                <th>Label</th>
                <th>Required</th>
                <th>Severity</th>
              </tr>
            </thead>
            <tbody>
              @for (col of columns; track col.fieldKey) {
                <tr>
                  <td><code>{{ col.fieldKey }}</code></td>
                  <td>{{ col.label }}</td>
                  <td>{{ col.required ? 'Yes' : 'No' }}</td>
                  <td>{{ col.severity }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      @if (selectedJob) {
        <div class="panel" style="margin-bottom: 1rem">
          <div class="page-head" style="margin-bottom: 0.5rem">
            <h3 style="margin: 0; font-size: 1.1rem">
              Job #{{ selectedJob.id }} · {{ selectedJob.status }}
            </h3>
            <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
              <button
                class="btn"
                type="button"
                [disabled]="busy || selectedJob.status === 'COMMITTED'"
                (click)="revalidate()"
              >
                Re-validate
              </button>
              <button
                class="btn primary"
                type="button"
                [disabled]="busy || selectedJob.status === 'COMMITTED' || selectedJob.readyCount < 1"
                (click)="commit()"
              >
                {{ busy ? 'Working…' : 'Commit gap-fill' }}
              </button>
            </div>
          </div>
          <p>
            {{ selectedJob.fileName || '—' }} · {{ selectedJob.entityType }} ·
            rows {{ selectedJob.totalRows }} · matched {{ selectedJob.matchedCount }} · ready
            {{ selectedJob.readyCount }} · errors {{ selectedJob.errorCount }} · updated
            {{ selectedJob.updatedCount }}
            @if (selectedJob.fillBlankOnly) {
              · blank-only
            }
          </p>
          @if (selectedJob.rows?.length) {
            <table class="data">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Match</th>
                  <th>Status</th>
                  <th>Message</th>
                </tr>
              </thead>
              <tbody>
                @for (row of selectedJob.rows; track row.id) {
                  <tr>
                    <td>{{ row.rowNo }}</td>
                    <td>{{ row.matchKey || '—' }}</td>
                    <td>{{ row.status }}</td>
                    <td>{{ row.errorMessage || '—' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }

      <div class="panel">
        <h3 style="margin-top: 0; font-size: 1.1rem">Recent jobs</h3>
        @if (loadingJobs) {
          <p class="muted">Loading jobs…</p>
        } @else if (!jobs.length) {
          <p class="muted">No import jobs yet.</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>Id</th>
                <th>Entity</th>
                <th>File</th>
                <th>Status</th>
                <th>Ready / Err</th>
                <th>Updated</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              @for (job of jobs; track job.id) {
                <tr>
                  <td>{{ job.id }}</td>
                  <td>{{ job.entityType }}</td>
                  <td>{{ job.fileName || '—' }}</td>
                  <td>{{ job.status }}</td>
                  <td>{{ job.readyCount }} / {{ job.errorCount }}</td>
                  <td>{{ job.updatedCount }}</td>
                  <td>
                    <button class="btn" type="button" (click)="openJob(job.id)">Open</button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </section>
  `,
})
export class ComplianceImportComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);

  bootstrap: ImportBootstrap | null = null;
  jobs: ImportJob[] = [];
  selectedJob: ImportJob | null = null;
  entityType: 'STUDENT' | 'STAFF' = 'STUDENT';
  fillBlankOnly = true;
  loadingJobs = true;
  uploading = false;
  busy = false;
  error = '';
  hint = '';

  get columns() {
    if (!this.bootstrap) return [];
    return this.entityType === 'STAFF'
      ? this.bootstrap.staffColumns
      : this.bootstrap.studentColumns;
  }

  ngOnInit(): void {
    this.api.importBootstrap().subscribe({
      next: (b) => {
        this.bootstrap = b;
        this.fillBlankOnly = b.fillBlankOnlyDefault !== false;
      },
      error: (err) => {
        this.error =
          err?.error?.message ||
          err?.message ||
          'Unable to load Import Center. Check FEATURE_CBSE_COMPLIANCE.';
      },
    });
    this.reloadJobs();
  }

  onEntityChange(): void {
    this.error = '';
  }

  downloadTemplate(): void {
    this.api.downloadImportTemplate(this.entityType).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `${this.entityType.toLowerCase()}-compliance-import-template.csv`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Template download failed.';
      },
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    this.uploading = true;
    this.error = '';
    this.hint = '';
    this.api.uploadImport(file, this.entityType, this.fillBlankOnly).subscribe({
      next: (job) => {
        this.uploading = false;
        this.selectedJob = job;
        this.reloadJobs();
        this.hint = `Parsed ${job.totalRows} row(s). Review ready/error counts, then commit.`;
      },
      error: (err) => {
        this.uploading = false;
        this.error = err?.error?.message || err?.message || 'Upload failed.';
      },
    });
  }

  openJob(id: number): void {
    this.error = '';
    this.api.getImportJob(id).subscribe({
      next: (job) => (this.selectedJob = job),
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Unable to load job.';
      },
    });
  }

  revalidate(): void {
    if (!this.selectedJob) return;
    this.busy = true;
    this.error = '';
    this.api.validateImportJob(this.selectedJob.id).subscribe({
      next: (job) => {
        this.busy = false;
        this.selectedJob = job;
        this.reloadJobs();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || err?.message || 'Validate failed.';
      },
    });
  }

  commit(): void {
    if (!this.selectedJob) return;
    this.busy = true;
    this.error = '';
    this.hint = '';
    this.api.commitImportJob(this.selectedJob.id).subscribe({
      next: (res) => {
        this.busy = false;
        this.selectedJob = res.job;
        this.hint = res.hint || 'Commit finished.';
        this.reloadJobs();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || err?.message || 'Commit failed.';
      },
    });
  }

  private reloadJobs(): void {
    this.loadingJobs = true;
    this.api.listImportJobs().subscribe({
      next: (jobs) => {
        this.jobs = jobs;
        this.loadingJobs = false;
      },
      error: () => {
        this.loadingJobs = false;
      },
    });
  }
}
