import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import {
  SCHOOL_ISSUE_TYPES,
  SupportTicket,
  SupportTicketService,
} from './support-ticket.service';

@Component({
  selector: 'sf-support-ticket-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head head-row">
        <div>
          <h2>{{ ticket?.ticketNumber || 'Ticket' }}</h2>
          <p>Raise a ticket with the SugamFlow school support team.</p>
        </div>
        <a routerLink="/admin/support/tickets" class="secondary-link">← My tickets</a>
      </div>

      @if (loading) {
        <div class="panel"><p class="muted">Loading…</p></div>
      } @else if (error && !ticket) {
        <div class="panel"><p class="error">{{ error }}</p></div>
      } @else if (ticket) {
        <div class="panel">
          <div class="meta-row">
            <span class="badge" [attr.data-status]="ticket.status">{{ labelStatus(ticket.status) }}</span>
            <span class="muted">{{ labelPriority(ticket.priority) }} priority</span>
            <span class="muted">{{ labelIssueType(ticket.issueType) }}</span>
            <span class="muted">Opened {{ ticket.createdAt | date: 'medium' }}</span>
          </div>

          <h3 class="subject">{{ ticket.subject }}</h3>
          <p class="description">{{ ticket.description }}</p>

          <div class="grid two meta-grid">
            @if (ticket.moduleName) {
              <div><strong>Module</strong><div>{{ ticket.moduleName }}</div></div>
            }
            @if (ticket.contactEmail) {
              <div><strong>Email</strong><div>{{ ticket.contactEmail }}</div></div>
            }
            @if (ticket.contactMobile) {
              <div><strong>Mobile</strong><div>{{ ticket.contactMobile }}</div></div>
            }
            @if (ticket.assignedTo) {
              <div><strong>Assigned to</strong><div>{{ ticket.assignedTo }}</div></div>
            }
          </div>

          @if (ticket.attachments?.length) {
            <div class="subpanel">
              <strong>Attachments</strong>
              <ul class="file-list">
                @for (a of ticket.attachments; track a.id) {
                  <li>
                    <button type="button" class="linkish" (click)="download(a.id, a.fileName)">
                      {{ a.fileName }}
                    </button>
                    <span class="muted">({{ formatSize(a.fileSize) }})</span>
                  </li>
                }
              </ul>
            </div>
          }

          @if (canClose) {
            <div class="actions">
              <button type="button" class="secondary" [disabled]="busy" (click)="closeTicket()">
                {{ busy ? 'Closing…' : 'Close ticket' }}
              </button>
            </div>
          }

          @if (statusMsg) {
            <p class="status">{{ statusMsg }}</p>
          }
          @if (error) {
            <p class="error">{{ error }}</p>
          }
        </div>

        <div class="panel">
          <h3>Comments</h3>
          @if (!ticket.comments?.length) {
            <p class="muted">No comments yet.</p>
          } @else {
            <ul class="comments">
              @for (c of ticket.comments; track c.id) {
                <li [class.staff]="c.staffResponse">
                  <div class="comment-head">
                    <strong>{{ c.staffResponse ? 'Support' : c.author || 'You' }}</strong>
                    <span class="muted">{{ c.createdAt | date: 'medium' }}</span>
                  </div>
                  <p>{{ c.body }}</p>
                </li>
              }
            </ul>
          }

          @if (canComment) {
            <label>
              <span class="field-title">Add a comment</span>
              <textarea
                [(ngModel)]="commentBody"
                rows="4"
                maxlength="10000"
                placeholder="Add more detail for the support team"
              ></textarea>
            </label>
            <div class="actions">
              <button type="button" [disabled]="!commentBody.trim() || busy" (click)="addComment()">
                {{ busy ? 'Sending…' : 'Post comment' }}
              </button>
            </div>
          }
        </div>
      }
    </section>
  `,
  styles: `
    .head-row {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .secondary-link {
      color: var(--sf-primary);
      font-weight: 600;
      text-decoration: none;
      align-self: center;
    }
    .meta-row {
      display: flex;
      flex-wrap: wrap;
      gap: 0.65rem 1rem;
      align-items: center;
      margin-bottom: 0.85rem;
    }
    .badge {
      display: inline-block;
      padding: 0.2rem 0.55rem;
      border-radius: 999px;
      font-size: 0.78rem;
      font-weight: 700;
      background: color-mix(in srgb, var(--sf-primary) 12%, transparent);
    }
    .badge[data-status='OPEN'] {
      background: color-mix(in srgb, #2563eb 16%, #fff);
      color: #1d4ed8;
    }
    .badge[data-status='IN_PROGRESS'] {
      background: color-mix(in srgb, #d97706 16%, #fff);
      color: #b45309;
    }
    .badge[data-status='RESOLVED'] {
      background: color-mix(in srgb, #059669 16%, #fff);
      color: #047857;
    }
    .badge[data-status='CLOSED'],
    .badge[data-status='REJECTED'] {
      background: color-mix(in srgb, #64748b 16%, #fff);
      color: #475569;
    }
    .subject {
      margin: 0 0 0.5rem;
      font-size: 1.15rem;
    }
    .description {
      white-space: pre-wrap;
      margin: 0 0 1rem;
    }
    .meta-grid {
      margin-bottom: 1rem;
    }
    .meta-grid strong {
      display: block;
      font-size: 0.78rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: color-mix(in srgb, var(--sf-text) 65%, #64748b);
      margin-bottom: 0.2rem;
    }
    .file-list,
    .comments {
      list-style: none;
      margin: 0.5rem 0 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
    }
    .comments li {
      padding: 0.75rem;
      border-radius: var(--sf-radius);
      background: color-mix(in srgb, var(--sf-panel) 70%, #fff);
      border: 1px solid color-mix(in srgb, var(--sf-menu) 12%, transparent);
    }
    .comments li.staff {
      border-color: color-mix(in srgb, var(--sf-primary) 28%, transparent);
      background: color-mix(in srgb, var(--sf-primary) 6%, #fff);
    }
    .comment-head {
      display: flex;
      justify-content: space-between;
      gap: 0.75rem;
      margin-bottom: 0.35rem;
      font-size: 0.88rem;
    }
    .comments p {
      margin: 0;
      white-space: pre-wrap;
    }
    button.linkish {
      background: transparent;
      color: var(--sf-primary);
      padding: 0;
      font-weight: 600;
    }
    textarea {
      resize: vertical;
      min-height: 5rem;
    }
  `,
})
export class SupportTicketDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly support = inject(SupportTicketService);

  loading = true;
  busy = false;
  error = '';
  statusMsg = '';
  ticket: SupportTicket | null = null;
  commentBody = '';
  private ticketId = '';

  ngOnInit(): void {
    this.ticketId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  get canClose(): boolean {
    const s = this.ticket?.status;
    return s === 'OPEN' || s === 'IN_PROGRESS' || s === 'RESOLVED';
  }

  get canComment(): boolean {
    const s = this.ticket?.status;
    return s === 'OPEN' || s === 'IN_PROGRESS' || s === 'RESOLVED';
  }

  load(): void {
    if (!this.ticketId) {
      this.loading = false;
      this.error = 'Missing ticket id.';
      return;
    }
    this.loading = true;
    this.error = '';
    this.support.get(this.ticketId).subscribe({
      next: (ticket) => {
        this.ticket = ticket;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? err?.message ?? 'Could not load ticket.';
      },
    });
  }

  addComment(): void {
    const body = this.commentBody.trim();
    if (!body || this.busy || !this.ticketId) return;
    this.busy = true;
    this.error = '';
    this.statusMsg = '';
    this.support.comment(this.ticketId, body).subscribe({
      next: (ticket) => {
        this.ticket = ticket;
        this.commentBody = '';
        this.busy = false;
        this.statusMsg = 'Comment posted.';
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? err?.message ?? 'Could not post comment.';
      },
    });
  }

  closeTicket(): void {
    if (!this.canClose || this.busy || !this.ticketId) return;
    this.busy = true;
    this.error = '';
    this.statusMsg = '';
    this.support.close(this.ticketId).subscribe({
      next: (ticket) => {
        this.ticket = ticket;
        this.busy = false;
        this.statusMsg = 'Ticket closed.';
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? err?.message ?? 'Could not close ticket.';
      },
    });
  }

  download(attachmentId: number, fileName: string): void {
    if (!this.ticketId) return;
    this.support.download(this.ticketId, attachmentId).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => {
        this.error = err?.error?.message ?? err?.message ?? 'Download failed.';
      },
    });
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  }

  labelStatus(status: string): string {
    return status?.replace(/_/g, ' ') ?? status;
  }

  labelPriority(priority: string): string {
    return priority ? priority.charAt(0) + priority.slice(1).toLowerCase() : priority;
  }

  labelIssueType(issueType: string): string {
    return SCHOOL_ISSUE_TYPES.find((t) => t.value === issueType)?.label ?? issueType?.replace(/_/g, ' ');
  }
}
