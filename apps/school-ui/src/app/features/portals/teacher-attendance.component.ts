import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';

const MARK_OPTIONS = ['PRESENT', 'ABSENT', 'LATE', 'LEAVE'] as const;

@Component({
  selector: 'sf-teacher-attendance',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './teacher-attendance.component.html',
  styleUrls: ['../../shared/admin-page.scss', './teacher-attendance.component.scss'],
})
export class TeacherAttendanceComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly markOptions = MARK_OPTIONS;

  loading = true;
  busy = false;
  error = '';
  status = '';

  sections: any[] = [];
  sectionId = '';
  date = new Date().toISOString().slice(0, 10);
  periodId = '';

  roster: any = null;
  draft: Record<string, string> = {};
  parentAlerts: any = null;

  ngOnInit(): void {
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (items) => {
        this.sections = items ?? [];
        if (this.sections.length && !this.sectionId) {
          this.sectionId = this.sections[0].id;
        }
        this.loading = false;
        if (this.sectionId) {
          this.loadRoster();
        }
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load sections';
      },
    });
  }

  loadRoster(preserveFeedback = false): void {
    if (!this.sectionId || !this.date) {
      return;
    }
    this.busy = true;
    this.error = '';
    if (!preserveFeedback) {
      this.status = '';
      this.parentAlerts = null;
    }
    let path = `/api/attendance/roster?sectionId=${encodeURIComponent(this.sectionId)}&date=${encodeURIComponent(this.date)}`;
    if (this.periodId) {
      path += `&periodId=${encodeURIComponent(this.periodId)}`;
    }
    this.api.get<any>(path).subscribe({
      next: (data) => {
        this.roster = data;
        this.draft = {};
        for (const s of data?.students ?? []) {
          const key = s.studentId || s.admissionNo;
          if (key) {
            this.draft[key] = s.markStatus || 'PRESENT';
          }
        }
        this.busy = false;
      },
      error: (err) => {
        this.busy = false;
        this.roster = null;
        this.error = err?.error?.message ?? 'Failed to load roster';
      },
    });
  }

  markAll(status: string): void {
    for (const key of Object.keys(this.draft)) {
      this.draft[key] = status;
    }
  }

  save(submit = false): void {
    if (!this.roster) {
      return;
    }
    const marks = (this.roster.students ?? []).map((s: any) => {
      const key = s.studentId || s.admissionNo;
      return {
        studentId: s.studentId,
        admissionNo: s.admissionNo,
        studentName: s.studentName,
        status: this.draft[key] || 'PRESENT',
      };
    });
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api
      .put<any>('/api/attendance/sessions/bulk', {
        sectionId: this.sectionId,
        date: this.date,
        periodId: this.periodId || null,
        submit: submit ? 'SUBMITTED' : undefined,
        marks,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.parentAlerts = res?.parentAlerts ?? null;
          this.status = submit
            ? this.submittedMessage(res?.markCount, this.parentAlerts)
            : `Saved draft (${res.markCount} marks)`;
          this.loadRoster(true);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save failed';
        },
      });
  }

  submit(): void {
    const sessionId = this.roster?.session?.id;
    if (!sessionId) {
      this.save(true);
      return;
    }
    this.busy = true;
    this.error = '';
    this.api.post<any>(`/api/attendance/sessions/${sessionId}/submit`, {}).subscribe({
      next: (res) => {
        this.busy = false;
        this.parentAlerts = res?.parentAlerts ?? null;
        this.status = this.submittedMessage(
          (this.roster?.students ?? []).length,
          this.parentAlerts,
        );
        this.loadRoster(true);
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Submit failed';
      },
    });
  }

  sessionLabel(): string {
    const s = this.roster?.session;
    if (!s) {
      return 'No session yet';
    }
    return `${s.status || 'DRAFT'}${s.id ? ` · ${String(s.id).slice(0, 8)}` : ''}`;
  }

  locked(): boolean {
    return String(this.roster?.session?.status || '').toUpperCase() === 'LOCKED';
  }

  alertDeliveries(): any[] {
    const rows: any[] = [];
    for (const alert of this.parentAlerts?.alerts ?? []) {
      for (const delivery of alert?.delivery ?? []) {
        rows.push({
          studentName: alert?.studentName || alert?.admissionNo || 'Student',
          markStatus: alert?.status || '',
          channel: delivery?.channel || '',
          status: delivery?.status || 'UNKNOWN',
          error: delivery?.error || '',
        });
      }
    }
    return rows;
  }

  private submittedMessage(markCount: number, alerts: any): string {
    const count = Number(markCount || 0);
    if (!alerts) {
      return `Submitted (${count} marks)`;
    }
    return `Submitted (${count} marks) · ${Number(alerts.notified || 0)} parent alert${
      Number(alerts.notified || 0) === 1 ? '' : 's'
    } queued`;
  }
}
