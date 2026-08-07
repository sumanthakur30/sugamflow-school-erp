import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  ComplianceReadiness,
  ValidateRunResult,
  ValidationFinding,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-readiness',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Data Readiness</h2>
          <p>Validate student and staff masters against the CBSE field pack.</p>
        </div>
        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
          <a routerLink="/admin/compliance" class="btn">Dashboard</a>
          <button class="btn primary" type="button" [disabled]="running" (click)="runValidate()">
            {{ running ? 'Validating…' : 'Run validation' }}
          </button>
        </div>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c">
          <p>{{ error }}</p>
        </div>
      }

      @if (lastRun) {
        <div class="panel" style="margin-bottom: 1rem">
          <p>
            Last run: campaign #{{ lastRun.campaignId }} ·
            <strong>{{ lastRun.status }}</strong> · score
            {{ lastRun.complianceScore }}% · checked {{ lastRun.recordsChecked }} records ·
            {{ lastRun.blockerCount }} blockers · {{ lastRun.warnCount }} warnings
          </p>
        </div>
      }

      @if (loading && !readiness) {
        <p class="muted">Loading readiness…</p>
      }

      @if (readiness) {
        <div class="grid two" style="margin-bottom: 1rem">
          <div class="panel">
            <div class="field-title">Readiness</div>
            <div style="font-size: 2rem; font-weight: 700">{{ readiness.readinessPercent }}%</div>
            <p class="muted">
              Campaign {{ readiness.latestCampaignId || '—' }}
              @if (readiness.latestCampaignStatus) {
                ({{ readiness.latestCampaignStatus }})
              }
            </p>
          </div>
          <div class="panel">
            <div class="field-title">Open findings</div>
            <p>Blockers: <strong>{{ readiness.openBlockers }}</strong></p>
            <p>Warnings: <strong>{{ readiness.openWarnings }}</strong></p>
            <p>Student: {{ readiness.openStudentFindings }} · Staff: {{ readiness.openStaffFindings }}</p>
          </div>
        </div>

        <div class="panel" style="margin-bottom: 1rem">
          <h3 style="margin-top: 0; font-size: 1.1rem">Gaps by field</h3>
          @if (!readiness.gaps?.length) {
            <p class="muted">No open gaps. Run validation after data changes.</p>
          } @else {
            <table class="data">
              <thead>
                <tr>
                  <th>Entity</th>
                  <th>Field</th>
                  <th>Severity</th>
                  <th>Count</th>
                </tr>
              </thead>
              <tbody>
                @for (g of readiness.gaps; track g.entityType + g.fieldKey + g.severity) {
                  <tr>
                    <td>{{ g.entityType }}</td>
                    <td>{{ g.label || g.fieldKey }}</td>
                    <td>{{ g.severity }}</td>
                    <td>{{ g.count }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }

      <div class="panel">
        <div class="page-head" style="margin-bottom: 0.75rem">
          <h3 style="margin: 0; font-size: 1.1rem">Findings</h3>
          <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
            <select [(ngModel)]="entityFilter" (ngModelChange)="loadFindings()">
              <option value="">All entities</option>
              <option value="STUDENT">Student</option>
              <option value="STAFF">Staff</option>
            </select>
            <select [(ngModel)]="severityFilter" (ngModelChange)="loadFindings()">
              <option value="">All severities</option>
              <option value="BLOCKER">Blockers</option>
              <option value="WARN">Warnings</option>
            </select>
          </div>
        </div>

        @if (findingsLoading) {
          <p class="muted">Loading findings…</p>
        } @else if (!findings.length) {
          <p class="muted">No open findings for this filter.</p>
        } @else {
          <table class="data">
            <thead>
              <tr>
                <th>Severity</th>
                <th>Entity</th>
                <th>Record</th>
                  <th>Field</th>
                  <th>Source</th>
                  <th>Message</th>
              </tr>
            </thead>
            <tbody>
              @for (f of findings; track f.id) {
                <tr>
                  <td>{{ f.severity }}</td>
                  <td>{{ f.entityType }}</td>
                  <td>{{ f.entityLabel || f.entityId || '—' }}</td>
                  <td>{{ f.fieldKey || '—' }}</td>
                  <td>{{ f.source || 'RULE' }}</td>
                  <td>
                    <div>{{ f.message }}</div>
                    @if (f.suggestion) {
                      <div class="muted">{{ f.suggestion }}</div>
                    }
                  </td>
                </tr>
              }
            </tbody>
          </table>
          <p class="muted" style="margin-top: 0.5rem">Showing {{ findings.length }} of {{ findingsTotal }}</p>
        }
      </div>
    </section>
  `,
})
export class ComplianceReadinessComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);

  loading = true;
  findingsLoading = false;
  running = false;
  error = '';
  readiness: ComplianceReadiness | null = null;
  findings: ValidationFinding[] = [];
  findingsTotal = 0;
  lastRun: ValidateRunResult | null = null;
  entityFilter = '';
  severityFilter = '';

  ngOnInit(): void {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.api.readiness().subscribe({
      next: (r) => {
        this.readiness = r;
        this.loading = false;
        this.loadFindings();
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.error?.message ||
          err?.message ||
          'Unable to load data readiness. Check FEATURE_CBSE_COMPLIANCE.';
      },
    });
  }

  loadFindings(): void {
    this.findingsLoading = true;
    this.api
      .findings({
        entityType: this.entityFilter || undefined,
        severity: this.severityFilter || undefined,
        page: 0,
        size: 100,
      })
      .subscribe({
        next: (page) => {
          this.findings = page.items || [];
          this.findingsTotal = page.totalElements || 0;
          this.findingsLoading = false;
        },
        error: () => {
          this.findingsLoading = false;
          this.findings = [];
        },
      });
  }

  runValidate(): void {
    this.running = true;
    this.error = '';
    this.api.runValidation({ title: 'CBSE data validation', boardCode: 'CBSE' }).subscribe({
      next: (result) => {
        this.lastRun = result;
        this.running = false;
        this.refresh();
      },
      error: (err) => {
        this.running = false;
        this.error =
          err?.error?.message || err?.message || 'Validation failed. Check student/staff services.';
      },
    });
  }
}
