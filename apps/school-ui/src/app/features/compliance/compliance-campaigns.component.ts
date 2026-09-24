import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  AiScanJob,
  CampaignArtifact,
  ComplianceApiService,
  ComplianceCampaign,
} from './compliance-api.service';

@Component({
  selector: 'sf-compliance-campaigns',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head">
        <div>
          <h2>Campaign Workspace</h2>
          <p>Validate → approve → lock → export → submit → archive.</p>
        </div>
        <div style="display: flex; gap: 0.5rem; flex-wrap: wrap">
          <a routerLink="/admin/compliance/readiness" class="btn">Data Readiness</a>
          <a routerLink="/admin/compliance" class="btn">Dashboard</a>
        </div>
      </div>

      @if (error) {
        <div class="panel" style="border-color: #c45c5c"><p>{{ error }}</p></div>
      }
      @if (info) {
        <div class="panel"><p>{{ info }}</p></div>
      }

      <div class="panel" style="margin-bottom: 1rem">
        <h3 style="margin-top: 0; font-size: 1.1rem">New campaign</h3>
        <form class="grid two" [formGroup]="createForm" (ngSubmit)="create()">
          <label>
            <span class="field-title">Title</span>
            <input formControlName="title" maxlength="255" />
          </label>
          <label>
            <span class="field-title">Board</span>
            <select formControlName="boardCode">
              <option value="CBSE">CBSE</option>
              <option value="ICSE">ICSE</option>
              <option value="STATE">STATE</option>
            </select>
          </label>
          <label>
            <span class="field-title">Pack key (optional)</span>
            <input formControlName="packKey" placeholder="Defaults from school profile" />
          </label>
          <label style="display: flex; align-items: center; gap: 0.5rem">
            <input type="checkbox" formControlName="requireManagementApproval" />
            <span>Require management approval</span>
          </label>
          <div>
            <button class="btn primary" type="submit" [disabled]="createForm.invalid || busy">
              Create
            </button>
          </div>
        </form>
      </div>

      <div class="grid two">
        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">Campaigns</h3>
          @if (loading) {
            <p class="muted">Loading…</p>
          } @else if (!campaigns.length) {
            <p class="muted">No campaigns yet.</p>
          } @else {
            <table class="data">
              <thead>
                <tr>
                  <th>Title</th>
                  <th>Status</th>
                  <th>Score</th>
                  <th>Blockers</th>
                </tr>
              </thead>
              <tbody>
                @for (c of campaigns; track c.id) {
                  <tr
                    (click)="select(c.id)"
                    [style.background]="selected?.id === c.id ? 'rgba(0,0,0,0.04)' : null"
                    style="cursor: pointer"
                  >
                    <td>{{ c.title }}</td>
                    <td>{{ c.status }}</td>
                    <td>{{ c.complianceScore ?? '—' }}</td>
                    <td>{{ c.blockerCount }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        </div>

        <div class="panel">
          <h3 style="margin-top: 0; font-size: 1.1rem">Selected campaign</h3>
          @if (!selected) {
            <p class="muted">Select a campaign to manage workflow.</p>
          } @else {
            <p>
              <strong>{{ selected.title }}</strong> · {{ selected.boardCode }}
              @if (selected.packKey) {
                <span> / {{ selected.packKey }}</span>
              }
              · <strong>{{ selected.status }}</strong>
            </p>
            <p class="muted">
              Blockers {{ selected.blockerCount }} · Warnings {{ selected.warnCount }} · Score
              {{ selected.complianceScore ?? '—' }}
            </p>

            <div style="display: flex; flex-wrap: wrap; gap: 0.5rem; margin: 0.75rem 0">
              <button class="btn" type="button" [disabled]="busy" (click)="validate()">
                Validate
              </button>
              <button class="btn" type="button" [disabled]="busy" (click)="submitForReview()">
                Submit for review
              </button>
              <button class="btn" type="button" [disabled]="busy" (click)="approve('PRINCIPAL')">
                Principal approve
              </button>
              <button class="btn" type="button" [disabled]="busy" (click)="reject('PRINCIPAL')">
                Principal reject
              </button>
              @if (selected.requireManagementApproval) {
                <button class="btn" type="button" [disabled]="busy" (click)="approve('MANAGEMENT')">
                  Management approve
                </button>
                <button class="btn" type="button" [disabled]="busy" (click)="reject('MANAGEMENT')">
                  Management reject
                </button>
              }
              <button class="btn" type="button" [disabled]="busy" (click)="lock()">Lock</button>
              <button class="btn primary" type="button" [disabled]="busy" (click)="exportPkg()">
                Export CSV+JSON
              </button>
              <button class="btn" type="button" [disabled]="busy" (click)="runAiScan()">
                Run AI WARN scan
              </button>
              <label style="display: flex; align-items: center; gap: 0.35rem">
                <span class="muted">Submit via</span>
                <select [(ngModel)]="submitChannel">
                  <option value="FILE">FILE</option>
                  <option value="SFTP">SFTP (stub)</option>
                  <option value="REST">REST (stub)</option>
                </select>
              </label>
              <button class="btn" type="button" [disabled]="busy" (click)="submitChannelAction()">
                Submit
              </button>
              <button class="btn" type="button" [disabled]="busy" (click)="archive()">
                Archive
              </button>
            </div>

            @if (aiJob) {
              <p class="muted">
                AI scan #{{ aiJob.id }}: {{ aiJob.status }} · {{ aiJob.warnCount }} WARNs ·
                {{ aiJob.provider }}
                @if (aiJob.resultMessage) {
                  — {{ aiJob.resultMessage }}
                }
              </p>
            }

            <h4 style="margin-bottom: 0.35rem">Approvals</h4>
            @if (!selected.approvals?.length) {
              <p class="muted">No approval steps yet.</p>
            } @else {
              <ul>
                @for (a of selected.approvals; track a.id) {
                  <li>
                    {{ a.stepCode }} — {{ a.decision }}
                    @if (a.actorUserId) {
                      ({{ a.actorUserId }})
                    }
                    @if (a.commentText) {
                      — {{ a.commentText }}
                    }
                  </li>
                }
              </ul>
            }

            <h4 style="margin-bottom: 0.35rem">Artifacts</h4>
            @if (!selected.artifacts?.length) {
              <p class="muted">No export artifacts yet.</p>
            } @else {
              <table class="data">
                <thead>
                  <tr>
                    <th>Key</th>
                    <th>Format</th>
                    <th>File</th>
                    <th>Size</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  @for (a of selected.artifacts; track a.id) {
                    <tr>
                      <td>{{ a.artifactKey }}</td>
                      <td>{{ a.formatCode }}</td>
                      <td>{{ a.fileName }}</td>
                      <td>{{ a.fileSize }}</td>
                      <td>
                        <button class="btn" type="button" (click)="download(a)">Download</button>
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            }
          }
        </div>
      </div>
    </section>
  `,
})
export class ComplianceCampaignsComponent implements OnInit {
  private readonly api = inject(ComplianceApiService);
  private readonly fb = inject(FormBuilder);

  createForm = this.fb.nonNullable.group({
    title: ['Board submission', Validators.required],
    boardCode: ['CBSE', Validators.required],
    packKey: [''],
    requireManagementApproval: [false],
  });

  loading = true;
  busy = false;
  error = '';
  info = '';
  campaigns: ComplianceCampaign[] = [];
  selected: ComplianceCampaign | null = null;
  submitChannel = 'FILE';
  aiJob: AiScanJob | null = null;

  ngOnInit(): void {
    this.refresh();
  }

  refresh(selectId?: number): void {
    this.loading = true;
    this.error = '';
    this.api.listCampaigns().subscribe({
      next: (rows) => {
        this.campaigns = rows || [];
        this.loading = false;
        const id = selectId ?? this.selected?.id;
        if (id) {
          this.select(id);
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || err?.message || 'Unable to load campaigns.';
      },
    });
  }

  select(id: number): void {
    this.api.getCampaign(id).subscribe({
      next: (c) => (this.selected = c),
      error: (err) => {
        this.error = err?.error?.message || err?.message || 'Unable to load campaign.';
      },
    });
  }

  create(): void {
    if (this.createForm.invalid) return;
    this.busy = true;
    const v = this.createForm.getRawValue();
    this.api
      .createCampaign({
        title: v.title,
        boardCode: v.boardCode,
        packKey: v.packKey || undefined,
        requireManagementApproval: v.requireManagementApproval,
      })
      .subscribe({
        next: (c) => {
          this.busy = false;
          this.info = `Created campaign #${c.id}`;
          this.refresh(c.id);
        },
        error: (err) => this.fail(err),
      });
  }

  validate(): void {
    if (!this.selected) return;
    this.run(this.api.validateCampaign(this.selected.id), 'Validation complete');
  }

  submitForReview(): void {
    if (!this.selected) return;
    this.run(this.api.submitCampaignForReview(this.selected.id), 'Submitted for principal review');
  }

  approve(step: string): void {
    if (!this.selected) return;
    this.run(
      this.api.approveCampaign(this.selected.id, {
        stepCode: step,
        decision: 'APPROVED',
        comment: 'Approved from Campaign Workspace',
      }),
      `${step} approved`,
    );
  }

  reject(step: string): void {
    if (!this.selected) return;
    this.run(
      this.api.approveCampaign(this.selected.id, {
        stepCode: step,
        decision: 'REJECTED',
        comment: 'Rejected from Campaign Workspace',
      }),
      `${step} rejected`,
    );
  }

  lock(): void {
    if (!this.selected) return;
    this.run(this.api.lockCampaign(this.selected.id), 'Campaign locked');
  }

  exportPkg(): void {
    if (!this.selected) return;
    this.run(this.api.exportCampaign(this.selected.id), 'Export generated');
  }

  runAiScan(): void {
    if (!this.selected) return;
    this.busy = true;
    this.error = '';
    this.info = '';
    this.api.startAiScan(this.selected.id).subscribe({
      next: (job) => {
        this.aiJob = job;
        this.busy = false;
        this.info = `AI scan #${job.id} queued (${job.status}). WARNs are advisory only.`;
        this.pollAi(job.id);
      },
      error: (err) => this.fail(err),
    });
  }

  submitChannelAction(): void {
    if (!this.selected) return;
    this.run(
      this.api.submitCampaign(
        this.selected.id,
        this.submitChannel,
        `${this.submitChannel} submit from Campaign Workspace`,
      ),
      `Marked submitted (${this.submitChannel})`,
    );
  }

  archive(): void {
    if (!this.selected) return;
    this.run(this.api.archiveCampaign(this.selected.id), 'Campaign archived');
  }

  download(a: CampaignArtifact): void {
    if (!this.selected) return;
    this.api.downloadArtifact(this.selected.id, a.id).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = a.fileName;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => this.fail(err),
    });
  }

  private run(
    obs: import('rxjs').Observable<ComplianceCampaign | import('./compliance-api.service').ValidateRunResult>,
    okMsg: string,
  ): void {
    this.busy = true;
    this.error = '';
    this.info = '';
    obs.subscribe({
      next: (result) => {
        this.busy = false;
        this.info = okMsg;
        const id =
          'campaignId' in result && typeof result.campaignId === 'number'
            ? result.campaignId
            : this.selected?.id;
        this.refresh(id);
      },
      error: (err) => this.fail(err),
    });
  }

  private fail(err: { error?: { message?: string }; message?: string }): void {
    this.busy = false;
    this.error = err?.error?.message || err?.message || 'Action failed.';
  }

  private pollAi(jobId: number, attempt = 0): void {
    if (attempt > 20) return;
    setTimeout(() => {
      this.api.getAiScan(jobId).subscribe({
        next: (job) => {
          this.aiJob = job;
          if (job.status === 'QUEUED' || job.status === 'RUNNING') {
            this.pollAi(jobId, attempt + 1);
          } else if (job.status === 'COMPLETED') {
            this.info = job.resultMessage || 'AI WARN scan completed.';
            if (this.selected?.id) this.select(this.selected.id);
          } else {
            this.error = job.resultMessage || 'AI scan failed.';
          }
        },
        error: () => {},
      });
    }, 1000);
  }
}
