import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { SupportTicket, SupportTicketService } from './support-ticket.service';

@Component({
  selector: 'sf-support-ticket-list',
  standalone: true,
  imports: [CommonModule, RouterLink],
  styleUrls: ['../../shared/admin-page.scss'],
  template: `
    <section class="page">
      <div class="page-head head-row">
        <div>
          <h2>My tickets</h2>
          <p>Raise a ticket with the SugamFlow school support team.</p>
        </div>
        <a routerLink="/admin/support/new" class="btn-primary">Report issue</a>
      </div>

      <div class="panel">
        @if (loading) {
          <p class="muted">Loading tickets…</p>
        } @else if (error) {
          <p class="error">{{ error }}</p>
        } @else if (!tickets.length) {
          <p class="muted">No tickets yet. <a routerLink="/admin/support/new">Report an issue</a> to get started.</p>
        } @else {
          <div class="table-wrap">
            <table class="tickets">
              <thead>
                <tr>
                  <th>Ticket</th>
                  <th>Subject</th>
                  <th>Status</th>
                  <th>Priority</th>
                  <th>Created</th>
                </tr>
              </thead>
              <tbody>
                @for (t of tickets; track t.id) {
                  <tr>
                    <td>
                      <a [routerLink]="['/admin/support/tickets', t.id]">{{ t.ticketNumber }}</a>
                    </td>
                    <td>
                      <a [routerLink]="['/admin/support/tickets', t.id]">{{ t.subject }}</a>
                    </td>
                    <td><span class="badge" [attr.data-status]="t.status">{{ labelStatus(t.status) }}</span></td>
                    <td>{{ labelPriority(t.priority) }}</td>
                    <td>{{ t.createdAt | date: 'medium' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <div class="actions pager">
            <button type="button" class="secondary" [disabled]="page <= 0 || loading" (click)="go(page - 1)">
              Previous
            </button>
            <span class="muted">Page {{ page + 1 }} of {{ totalPages || 1 }} ({{ totalElements }} total)</span>
            <button
              type="button"
              class="secondary"
              [disabled]="page + 1 >= totalPages || loading"
              (click)="go(page + 1)"
            >
              Next
            </button>
            <select class="page-size" [value]="size" (change)="changeSize($event)">
              <option value="10">10 / page</option>
              <option value="20">20 / page</option>
              <option value="50">50 / page</option>
            </select>
          </div>
        }
      </div>
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
    .btn-primary {
      display: inline-flex;
      align-items: center;
      padding: 0.55rem 0.95rem;
      border-radius: var(--sf-radius);
      background: var(--sf-button);
      color: #fff;
      font-weight: 600;
      text-decoration: none;
    }
    .table-wrap {
      overflow-x: auto;
    }
    table.tickets {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.92rem;
    }
    table.tickets th,
    table.tickets td {
      text-align: left;
      padding: 0.65rem 0.5rem;
      border-bottom: 1px solid color-mix(in srgb, var(--sf-menu) 14%, transparent);
      vertical-align: top;
    }
    table.tickets th {
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.03em;
      color: color-mix(in srgb, var(--sf-text) 65%, #64748b);
    }
    table.tickets a {
      color: var(--sf-primary);
      font-weight: 600;
      text-decoration: none;
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
    .pager {
      align-items: center;
      margin-top: 1rem;
    }
    .page-size {
      margin-left: auto;
      max-width: 8rem;
    }
  `,
})
export class SupportTicketListComponent implements OnInit {
  private readonly support = inject(SupportTicketService);

  loading = true;
  error = '';
  tickets: SupportTicket[] = [];
  page = 0;
  size = 20;
  totalPages = 0;
  totalElements = 0;

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = '';
    this.support.list(this.page, this.size).subscribe({
      next: (data) => {
        this.tickets = data?.content ?? [];
        this.totalPages = data?.totalPages ?? 0;
        this.totalElements = data?.totalElements ?? 0;
        this.page = data?.number ?? this.page;
        this.size = data?.size ?? this.size;
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.tickets = [];
        this.error = err?.error?.message ?? err?.message ?? 'Could not load tickets.';
      },
    });
  }

  go(page: number): void {
    if (page < 0 || (this.totalPages && page >= this.totalPages)) return;
    this.page = page;
    this.load();
  }

  changeSize(event: Event): void {
    const value = Number((event.target as HTMLSelectElement).value);
    this.size = value || 20;
    this.page = 0;
    this.load();
  }

  labelStatus(status: string): string {
    return status?.replace(/_/g, ' ') ?? status;
  }

  labelPriority(priority: string): string {
    return priority ? priority.charAt(0) + priority.slice(1).toLowerCase() : priority;
  }
}
