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
        </div>
      }

      @if (loading) {
        <p class="muted">Loading compliance dashboard…</p>
      }

      @if (dash && !loading) {
        <div class="grid two" style="margin-bottom: 1rem">
          <div class="panel">
            <div class="field-title">Compliance score</div>
            <div style="font-size: 2rem; font-weight: 700">{{ dash.complianceScore }}%</div>
            <p class="muted">Profile {{ dash.profileCompletenessPercent }}% complete</p>
          </div>
          <div class="panel">
            <div class="field-title">Campaigns</div>
            <p>Pending: <strong>{{ dash.pendingCampaigns }}</strong></p>
            <p>Submitted: <strong>{{ dash.submittedCampaigns }}</strong></p>
            <p>Overdue: <strong>{{ dash.overdueCampaigns }}</strong></p>
          </div>
        </div>

        <div class="panel" style="margin-bottom: 1rem">
          <div class="page-head" style="margin-bottom: 0.5rem">
            <h3 style="margin: 0; font-size: 1.1rem">Principal action items</h3>
            <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
              <a routerLink="/admin/compliance/readiness" class="btn">Data Readiness</a>
              <a routerLink="/admin/compliance/import" class="btn">Import Center</a>
              <a routerLink="/admin/compliance/infrastructure" class="btn">Infrastructure</a>
              <a routerLink="/admin/compliance/documents" class="btn">Documents Vault</a>
              <a routerLink="/admin/compliance/campaigns" class="btn">Campaign Workspace</a>
              <a routerLink="/admin/compliance/disclosure" class="btn">Disclosure Preview</a>
              <a routerLink="/admin/compliance/profile" class="btn">Edit school profile</a>
              <a routerLink="/admin/compliance/platform-templates" class="btn">Platform Templates</a>
            </div>
          </div>
          <ul>
            @for (item of dash.principalActionItems; track item) {
              <li>{{ item }}</li>
            }
          </ul>
          @if (dash.missingProfileFields > 0) {
            <p class="muted">{{ dash.missingProfileFields }} profile field(s) still missing.</p>
          }
        </div>

        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">Recent campaigns</h3>
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
    this.api.dashboard().subscribe({
      next: (d) => {
        this.dash = d;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.error?.message ||
          err?.message ||
          'Unable to load compliance dashboard. Check FEATURE_CBSE_COMPLIANCE.';
      },
    });
  }
}
