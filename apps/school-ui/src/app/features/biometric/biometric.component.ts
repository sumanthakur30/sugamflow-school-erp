import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { Subscription, interval } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { environment } from '../../environments/environment';

type Tab = 'devices' | 'health' | 'live' | 'enrollments' | 'sync' | 'unknown' | 'rules' | 'reports';

@Component({
  selector: 'sf-biometric',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './biometric.component.html',
  styleUrls: ['../../shared/admin-page.scss', './biometric.component.scss'],
})
export class BiometricComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly http = inject(HttpClient);
  private liveSub?: Subscription;

  tab: Tab = 'devices';
  loading = true;
  error = '';
  status = '';
  busy = false;

  health: any = { total: 0, online: 0, offline: 0, syncing: 0, error: 0, devices: [] };
  devices: any[] = [];
  live: any[] = [];
  enrollments: any[] = [];
  events: any[] = [];
  summary: any = {};
  issuedKey = '';

  filters = { branchId: '', deviceType: '', status: '', location: '' };
  enrollmentQuery = '';

  draft: any = {
    name: '',
    vendor: 'ZKTECO',
    deviceType: 'FACE',
    serialNumber: '',
    branchId: '',
    location: '',
    gate: '',
    firmware: '',
    direction: 'BOTH',
    timeZone: 'Asia/Kolkata',
  };

  enrollment: any = {
    displayName: '',
    personType: 'STUDENT',
    personCode: '',
    personId: '',
    classSection: '',
    sectionId: '',
    enrollmentCode: '',
    verificationType: 'FACE',
    notifyMobile: '',
    notifyEmail: '',
  };

  rule: any = {
    punchMode: 'FIRST_LAST',
    schoolStart: '08:30',
    graceMinutes: 10,
    schoolEnd: '15:00',
    splitTime: '12:00',
    halfDayMinutes: 240,
    timeZone: 'Asia/Kolkata',
    driftThresholdSeconds: 120,
    notifyOnCheckIn: true,
    notifyChannels: 'IN_APP,SMS',
  };

  report = { date: new Date().toISOString().slice(0, 10), from: '', to: '', personType: 'STUDENT' };

  ngOnInit(): void {
    this.report.from = this.report.date;
    this.report.to = this.report.date;
    this.reload();
  }

  ngOnDestroy(): void {
    this.liveSub?.unsubscribe();
  }

  select(tab: Tab): void {
    this.tab = tab;
    this.issuedKey = '';
    this.error = '';
    this.status = '';
    if (tab === 'live') {
      this.loadLive();
      this.liveSub?.unsubscribe();
      this.liveSub = interval(5000).subscribe(() => this.loadLive());
    } else {
      this.liveSub?.unsubscribe();
    }
    if (tab === 'enrollments') this.loadEnrollments();
    if (tab === 'sync') this.loadEvents('');
    if (tab === 'unknown') this.loadEvents('UNKNOWN_PERSON');
    if (tab === 'rules') this.loadRules();
    if (tab === 'reports') this.loadSummary();
    if (tab === 'devices' || tab === 'health') this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    const q = new URLSearchParams();
    if (this.filters.branchId) q.set('branchId', this.filters.branchId);
    if (this.filters.deviceType) q.set('deviceType', this.filters.deviceType);
    if (this.filters.status) q.set('status', this.filters.status);
    if (this.filters.location) q.set('location', this.filters.location);
    const suffix = q.toString() ? `?${q.toString()}` : '';
    this.api.get<any>(`/api/attendance/biometric/health`).subscribe({
      next: (health) => {
        this.health = health;
      },
      error: () => {
        this.health = { total: 0, online: 0, offline: 0, syncing: 0, error: 0, devices: [] };
      },
    });
    this.api.get<any[]>(`/api/attendance/biometric/devices${suffix}`).subscribe({
      next: (rows) => {
        this.devices = rows || [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || 'Could not load biometric devices';
      },
    });
  }

  saveDevice(): void {
    this.busy = true;
    this.issuedKey = '';
    this.api.post<any>('/api/attendance/biometric/devices', this.draft).subscribe({
      next: (row) => {
        this.busy = false;
        this.issuedKey = row.apiKey || '';
        this.status = 'Device registered. Copy the API key now. It is not shown again.';
        this.draft.serialNumber = '';
        this.draft.name = '';
        this.reload();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message || 'Could not register device';
      },
    });
  }

  regenerate(device: any): void {
    this.api.post<any>(`/api/attendance/biometric/devices/${device.id}/key`, {}).subscribe({
      next: (row) => {
        this.issuedKey = row.apiKey || '';
        this.status = `New key for ${device.name}. Copy it now.`;
      },
      error: (err) => (this.error = err?.error?.message || 'Could not regenerate key'),
    });
  }

  disable(device: any): void {
    this.api.delete<any>(`/api/attendance/biometric/devices/${device.id}`).subscribe({
      next: () => {
        this.status = `${device.name} disabled`;
        this.reload();
      },
      error: (err) => (this.error = err?.error?.message || 'Could not disable device'),
    });
  }

  command(device: any, action: string): void {
    const dangerous = action === 'REBOOT' || action === 'CLEAR_LOG';
    if (dangerous && !window.confirm(`Send ${action} to ${device.name}?`)) {
      return;
    }
    this.api
      .post<any>(`/api/attendance/biometric/devices/${device.id}/commands`, {
        action,
        confirm: dangerous,
      })
      .subscribe({
        next: () => (this.status = `${action} queued for ${device.name}`),
        error: (err) => (this.error = err?.error?.message || 'Command was not queued'),
      });
  }

  loadLive(): void {
    this.api.get<any[]>('/api/attendance/biometric/live?limit=30').subscribe({
      next: (rows) => (this.live = rows || []),
      error: () => (this.live = []),
    });
  }

  loadEnrollments(): void {
    const q = this.enrollmentQuery ? `?q=${encodeURIComponent(this.enrollmentQuery)}` : '';
    this.api.get<any[]>(`/api/attendance/biometric/enrollments${q}`).subscribe({
      next: (rows) => (this.enrollments = rows || []),
      error: (err) => (this.error = err?.error?.message || 'Could not load enrollments'),
    });
  }

  saveEnrollment(): void {
    this.api.post<any>('/api/attendance/biometric/enrollments', this.enrollment).subscribe({
      next: () => {
        this.status = 'Enrollment saved';
        this.loadEnrollments();
      },
      error: (err) => (this.error = err?.error?.message || 'Could not save enrollment'),
    });
  }

  disableEnrollment(row: any): void {
    this.api.post<any>(`/api/attendance/biometric/enrollments/${row.id}/disable`, {}).subscribe({
      next: () => this.loadEnrollments(),
      error: (err) => (this.error = err?.error?.message || 'Could not disable enrollment'),
    });
  }

  loadEvents(status: string): void {
    const q = status ? `?status=${status}` : '';
    this.api.get<any[]>(`/api/attendance/biometric/events${q}`).subscribe({
      next: (rows) => (this.events = rows || []),
      error: (err) => (this.error = err?.error?.message || 'Could not load events'),
    });
  }

  retry(row: any): void {
    this.api.post<any>(`/api/attendance/biometric/events/${row.id}/retry`, {}).subscribe({
      next: () => {
        this.status = 'Event reprocessed';
        this.loadEvents(this.tab === 'unknown' ? 'UNKNOWN_PERSON' : '');
      },
      error: (err) => (this.error = err?.error?.message || 'Retry failed'),
    });
  }

  ignore(row: any): void {
    this.api.post<any>(`/api/attendance/biometric/events/${row.id}/ignore`, {}).subscribe({
      next: () => this.loadEvents('UNKNOWN_PERSON'),
      error: (err) => (this.error = err?.error?.message || 'Could not ignore event'),
    });
  }

  loadRules(): void {
    this.api.get<any>('/api/attendance/biometric/rules').subscribe({
      next: (rule) => {
        this.rule = { ...this.rule, ...rule };
        if (rule.schoolStart) this.rule.schoolStart = String(rule.schoolStart).slice(0, 5);
        if (rule.schoolEnd) this.rule.schoolEnd = String(rule.schoolEnd).slice(0, 5);
        if (rule.splitTime) this.rule.splitTime = String(rule.splitTime).slice(0, 5);
      },
      error: (err) => (this.error = err?.error?.message || 'Could not load rules'),
    });
  }

  saveRules(): void {
    this.api.put<any>('/api/attendance/biometric/rules', this.rule).subscribe({
      next: () => (this.status = 'Attendance rules saved'),
      error: (err) => (this.error = err?.error?.message || 'Could not save rules'),
    });
  }

  loadSummary(): void {
    this.api.get<any>(`/api/attendance/biometric/summary?date=${this.report.date}`).subscribe({
      next: (row) => (this.summary = row || {}),
      error: (err) => (this.error = err?.error?.message || 'Could not load summary'),
    });
  }

  download(kind: 'daily' | 'people'): void {
    const path =
      kind === 'daily'
        ? `/api/attendance/biometric/reports/daily?date=${this.report.date}`
        : `/api/attendance/biometric/reports/people?from=${this.report.from}&to=${this.report.to}&personType=${this.report.personType}`;
    this.http.get(`${environment.apiBaseUrl}${path}`, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = kind === 'daily' ? `biometric-${this.report.date}.csv` : `biometric-${this.report.personType}.csv`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => (this.error = 'Report download failed'),
    });
  }

  printReport(): void {
    window.print();
  }

  clock(value: string | null): string {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return value;
    return date.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit', second: '2-digit' });
  }
}
