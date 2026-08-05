import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

type EventRouting = {
  event: string;
  enabled: boolean;
  channels: string[];
  templateCount?: number;
  source?: string;
};

@Component({
  selector: 'sf-notifications',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.scss', '../../shared/admin-page.scss'],
})
export class NotificationsComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  saving = false;
  error = '';
  status = '';

  allChannels: string[] = [];
  routing: EventRouting[] = [];
  selectedEvent = '';

  draftEnabled = true;
  draftChannels: string[] = [];

  ngOnInit(): void {
    this.reload();
  }

  get selected(): EventRouting | null {
    return this.routing.find((row) => row.event === this.selectedEvent) ?? null;
  }

  get dirty(): boolean {
    const current = this.selected;
    if (!current) {
      return false;
    }
    const a = [...current.channels].map((c) => c.toUpperCase()).sort().join('|');
    const b = [...this.draftChannels].map((c) => c.toUpperCase()).sort().join('|');
    return current.enabled !== this.draftEnabled || a !== b;
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.status = '';

    let pending = 2;
    const done = () => {
      pending -= 1;
      if (pending <= 0) {
        this.loading = false;
        if (!this.selectedEvent && this.routing.length) {
          this.selectEvent(this.routing[0].event);
        } else if (this.selectedEvent) {
          this.selectEvent(this.selectedEvent);
        }
      }
    };

    this.api.get<string[]>('/api/school/notification-config/channels').subscribe({
      next: (channels) => {
        this.allChannels = channels ?? [];
        done();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load notification channels.';
        done();
      },
    });

    this.api.get<EventRouting[]>('/api/school/notification-config/routing').subscribe({
      next: (rows) => {
        this.routing = rows ?? [];
        done();
      },
      error: (err) => {
        this.error = err?.error?.message ?? 'Could not load event routing.';
        this.routing = [];
        done();
      },
    });
  }

  selectEvent(event: string): void {
    this.selectedEvent = event;
    this.status = '';
    const row = this.routing.find((item) => item.event === event);
    this.draftEnabled = row?.enabled ?? true;
    this.draftChannels = [...(row?.channels ?? ['IN_APP'])];
  }

  isChannelOn(channel: string): boolean {
    return this.draftChannels.some((c) => c.toUpperCase() === channel.toUpperCase());
  }

  toggleChannel(channel: string): void {
    if (this.saving || !this.draftEnabled) {
      return;
    }
    const upper = channel.toUpperCase();
    if (upper === 'IN_APP') {
      // Always keep in-app when the event is enabled.
      if (!this.isChannelOn('IN_APP')) {
        this.draftChannels = [...this.draftChannels, 'IN_APP'];
      }
      return;
    }
    if (this.isChannelOn(upper)) {
      this.draftChannels = this.draftChannels.filter((c) => c.toUpperCase() !== upper);
      if (!this.isChannelOn('IN_APP')) {
        this.draftChannels = [...this.draftChannels, 'IN_APP'];
      }
    } else {
      this.draftChannels = [...this.draftChannels, upper];
    }
  }

  channelLabel(channel: string): string {
    switch (channel.toUpperCase()) {
      case 'IN_APP':
        return 'In-app';
      case 'SMS':
        return 'SMS';
      case 'WHATSAPP':
        return 'WhatsApp';
      case 'EMAIL':
        return 'Email';
      case 'PUSH':
        return 'Push';
      case 'VOICE':
        return 'Voice';
      case 'TELEGRAM':
        return 'Telegram';
      default:
        return channel;
    }
  }

  eventHint(event: string): string {
    switch (event) {
      case 'ADMISSION':
        return 'Offers and admission status updates';
      case 'ATTENDANCE':
        return 'Absent / late parent alerts';
      case 'FEES':
        return 'Fee receipts and due reminders';
      case 'EXAM':
        return 'Marks and report cards';
      case 'EMERGENCY':
        return 'Urgent school-wide alerts';
      default:
        return 'Automated messages for this module';
    }
  }

  save(): void {
    if (!this.selectedEvent || this.saving || !this.dirty) {
      return;
    }
    this.saving = true;
    this.error = '';
    this.status = '';
    const channels = this.draftEnabled
      ? Array.from(new Set([...this.draftChannels.map((c) => c.toUpperCase()), 'IN_APP']))
      : ['IN_APP'];

    this.api
      .put<EventRouting>(`/api/school/notification-config/routing/${this.selectedEvent}`, {
        enabled: this.draftEnabled,
        channels,
      })
      .subscribe({
        next: (row) => {
          this.routing = this.routing.map((item) =>
            item.event === row.event ? { ...item, ...row } : item,
          );
          this.selectEvent(row.event);
          this.status = `${row.event} routing saved (${row.channels.join(', ')})`;
          this.saving = false;
        },
        error: (err) => {
          this.error = err?.error?.message ?? err?.message ?? 'Save failed.';
          this.saving = false;
        },
      });
  }

  resetDraft(): void {
    if (this.selectedEvent) {
      this.selectEvent(this.selectedEvent);
    }
  }
}
