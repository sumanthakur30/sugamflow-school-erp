import { Component, OnInit, inject } from '@angular/core';
import { DatePipe, NgFor, NgIf } from '@angular/common';
import { WebsiteApiService } from '../core/website-api.service';

@Component({
  selector: 'app-events-page',
  standalone: true,
  imports: [NgFor, NgIf, DatePipe],
  template: `
    <h1>Events</h1>
    <article *ngFor="let e of events">
      <h2>{{ e['title'] }}</h2>
      <p class="meta">
        {{ asDate(e['startsAt']) | date: 'mediumDate' }}
        <span *ngIf="e['locationText']"> · {{ e['locationText'] }}</span>
      </p>
      <p>{{ e['summary'] }}</p>
    </article>
  `,
  styles: [
    `
      article {
        background: #fff;
        border: 1px solid #e6e9f0;
        border-radius: 12px;
        padding: 1rem;
        margin-bottom: 0.75rem;
      }
      .meta {
        color: #64748b;
      }
    `,
  ],
})
export class EventsPageComponent implements OnInit {
  private readonly api = inject(WebsiteApiService);
  events: Array<Record<string, unknown>> = [];

  ngOnInit(): void {
    this.api.resolve().subscribe(() => {
      this.api.listEvents().subscribe((rows) => (this.events = rows || []));
    });
  }

  asDate(value: unknown): string | number | Date | null {
    if (value == null) return null;
    if (value instanceof Date || typeof value === 'string' || typeof value === 'number') {
      return value;
    }
    return String(value);
  }
}
