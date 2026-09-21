import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-device-adapters',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './device-adapters.component.html',
  styleUrls: ['../../shared/admin-page.scss', './device-adapters.component.scss'],
})
export class DeviceAdaptersComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  status = '';
  busy = false;

  aiAttendanceEnabled = false;
  autoSubmitFromDevice = true;
  minConfidence = 0.8;
  adapterTypes: any[] = [];
  devices: any[] = [];
  events: any[] = [];
  flags: Record<string, boolean> = {};

  draft = {
    deviceKey: '',
    name: '',
    adapterType: 'AI_CAMERA',
    location: '',
  };

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/attendance/devices/bootstrap').subscribe({
      next: (boot) => {
        this.aiAttendanceEnabled = !!boot.aiAttendanceEnabled;
        this.autoSubmitFromDevice = boot.autoSubmitFromDevice !== false;
        this.minConfidence = boot.minConfidence ?? 0.8;
        this.adapterTypes = boot.adapterTypes ?? [];
        this.devices = boot.devices ?? [];
        this.flags = boot.flags ?? {};
        this.loading = false;
        this.loadEvents();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Device bootstrap failed';
      },
    });
  }

  loadEvents(): void {
    this.api.get<any[]>('/api/attendance/devices/events').subscribe({
      next: (list) => (this.events = list ?? []),
      error: () => (this.events = []),
    });
  }

  enabledTypes(): any[] {
    return this.adapterTypes.filter((t) => t.featureEnabled);
  }

  register(): void {
    if (!this.draft.deviceKey.trim() || !this.draft.name.trim()) {
      this.error = 'deviceKey and name are required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post('/api/attendance/devices', {
        deviceKey: this.draft.deviceKey.trim(),
        name: this.draft.name.trim(),
        adapterType: this.draft.adapterType,
        config: { location: this.draft.location || 'Campus', vendor: 'Simulated' },
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Registered ${this.draft.deviceKey}`;
          this.draft = { deviceKey: '', name: '', adapterType: 'AI_CAMERA', location: '' };
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Register failed';
        },
      });
  }

  simulate(deviceId: string): void {
    this.busy = true;
    this.error = '';
    this.api.post(`/api/attendance/devices/${deviceId}/simulate`, {}).subscribe({
      next: (ev: any) => {
        this.busy = false;
        this.status = `Event ${ev.status}${ev.attendanceRecordId ? ' → record ' + ev.attendanceRecordId : ''}`;
        this.loadEvents();
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Simulate failed';
      },
    });
  }

  toggleDevice(d: any): void {
    const next = d.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.api.put(`/api/attendance/devices/${d.id}`, { status: next }).subscribe({
      next: () => this.reload(),
      error: (err) => (this.error = err?.error?.message ?? 'Update failed'),
    });
  }
}
