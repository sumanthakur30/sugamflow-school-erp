import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-notifications',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './notifications.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class NotificationsComponent implements OnInit {
  private readonly api = inject(ApiService);
  events: string[] = [];
  channels: string[] = [];

  ngOnInit(): void {
    this.api
      .get<string[]>('/api/school/notification-config/events')
      .subscribe((e) => (this.events = e));
    this.api
      .get<string[]>('/api/school/notification-config/channels')
      .subscribe((c) => (this.channels = c));
  }
}
