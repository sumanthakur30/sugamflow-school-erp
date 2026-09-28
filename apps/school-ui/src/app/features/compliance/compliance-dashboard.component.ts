import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  ComplianceApiService,
  ComplianceDashboard,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <h2>Board Compliance</h2>
        <p>Board readiness score, pending submissions, and principal actions.</p>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c">
          <p>{{ error }}</p>
          <button type="button" class="btn" (click)="reload()">Retry</button>
        </div>
      }

      @if (loading) {
        <p class="muted">Loading compliance dashboard…</p>
      }

      @if (dash && !loading) {
        <div class="grid two" style="margin-bottom: 1rem">
          <div class="panel">
            <div class="field-title">Board readiness score</div>
            <div style="font-size: 2rem; font-weight: 700">{{ dash.complianceScore }}%</div>
            <div
              style="height: 8px; background: #e2e8f0; border-radius: 999px; overflow: hidden; margin: 0.5rem 0"
              role="meter"
              [attr.aria-valuenow]="dash.complianceScore"
              aria-valuemin="0"
              aria-valuemax="100"
              aria-label="Board readiness score"
            >
              <div
                [style.width.%]="dash.complianceScore"
                style="height: 100%; background: #14532d"
              ></div>
            </div>
            <p class="muted">Profile {{ dash.profileCompletenessPercent }}% complete</p>
          </div>
          <div class="panel">
            <div class="field-title">Pending submissions</div>
            <p>Pending: <strong>{{ dash.pendingCampaigns }}</strong></p>
            <p>Submitted: <strong>{{ dash.submittedCampaigns }}</strong></p>
            <p>Overdue: <strong>{{ dash.overdueCampaigns }}</strong></p>
          </div>
        </div>

        <div class="panel" style="margin-bottom: 1rem">
          <h3 style="margin-top: 0; font-size: 1.1rem">Principal actions</h3>
          <ul>
            @for (item of dash.principalActionItems; track item) {
              <li>{{ item }}</li>
            }
          </ul>
          @if (dash.missingProfileFields > 0) {
            <p class="muted">{{ dash.missingProfileFields }} profile field(s) still missing.</p>
          }
        </div>

        <div class="grid two" style="margin-bottom: 1rem">
          <a class="panel" routerLink="/admin/compliance/infrastructure">Infrastructure</a>
          <a class="panel" routerLink="/admin/compliance/documents">Documents Vault</a>
          <a class="panel" routerLink="/admin/compliance/disclosure">Disclosure Preview</a>
          <a class="panel" routerLink="/admin/compliance/readiness">Data Readiness</a>
          <a class="panel" routerLink="/admin/compliance/import">Import Center</a>
          <a class="panel" routerLink="/admin/compliance/campaigns">Campaign Workspace</a>
          <a class="panel" routerLink="/admin/compliance/profile">School profile</a>
          <a class="panel" routerLink="/admin/compliance/platform-templates">Platform Templates</a>
        </div>

        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">Pending submissions</h3>
          @if (!dash.recentCampaigns?.length) {
            <p class="muted">No campaigns yet. Run validation from Data Readiness to create one.</p>
          } @else {
            <table class="data">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Board</th>
                  <th>Status</th>
                  <th>Due</th>
                  <th>Blockers</th>
                </tr>
              </thead>
              <tbody>
                @for (c of dash.recentCampaigns; track c.id) {
                  <tr>
                    <td>{{ c.title }}</td>
                    <td>{{ c.boardCode }}</td>
                    <td>{{ c.status }}</td>
                    <td>{{ c.dueAt ? (c.dueAt | date: 'mediumDate') : '—' }}</td>
                    <td>{{ c.blockerCount }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>
      }
    </section>
  `,
})
export class ComplianceDashboardComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  loading = true;
  error = '';
  dash: ComplianceDashboard | null = null;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.dashboard().subscribe({
      next: (d) => {
        this.dash = d;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.dash = null;
        this.error =
          err?.error?.message ||
          err?.message ||
          'Unable to load compliance dashboard. Check FEATURE_CBSE_COMPLIANCE.';
      },
    });
  }
}
