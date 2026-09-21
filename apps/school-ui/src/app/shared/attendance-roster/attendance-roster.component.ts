import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/api.service';
import {
  ATTENDANCE_MARKS,
  AttendanceBulkResponse,
  AttendanceFilter,
  AttendanceMark,
  AttendanceMarkRequest,
  AttendancePeriod,
  AttendanceRoster,
  AttendanceSection,
  AttendanceStudentState,
  AttendanceSummary,
  ParentAlertSummary,
} from './attendance-roster.models';

/** Class/section roster — pick section once, mark the whole list. */
@Component({
  selector: 'sf-attendance-roster',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './attendance-roster.component.html',
  styleUrls: ['../admin-page.scss', './attendance-roster.component.scss'],
})
export class AttendanceRosterComponent implements OnInit {
  private readonly api = inject(ApiService);
  private rosterRequestId = 0;

  @Input() title = 'Class attendance';
  @Input() subtitle =
    'Pick a class/section once, then mark Present / Absent for the whole roster.';

  readonly markOptions = ATTENDANCE_MARKS;
  readonly statusFilters: Array<{ value: AttendanceFilter; label: string }> = [
    { value: 'ALL', label: 'All statuses' },
    { value: 'PRESENT', label: 'Present' },
    { value: 'ABSENT', label: 'Absent' },
    { value: 'LATE', label: 'Late' },
    { value: 'LEAVE', label: 'Leave' },
    { value: 'NOT_MARKED', label: 'Not marked' },
  ];

  loading = true;
  busy = false;
  error = '';
  status = '';

  sections: AttendanceSection[] = [];
  periods: AttendancePeriod[] = [];
  sectionId = '';
  date = this.todayLocal();
  periodId = '';

  roster: AttendanceRoster | null = null;
  students: AttendanceStudentState[] = [];
  visibleStudents: AttendanceStudentState[] = [];
  searchQuery = '';
  statusFilter: AttendanceFilter = 'ALL';
  selectedKeys = new Set<string>();
  summary: AttendanceSummary = this.emptySummary();
  dirty = false;
  parentAlerts: ParentAlertSummary | null = null;

  ngOnInit(): void {
    this.loadPeriods();
    this.api.get<AttendanceSection[]>('/api/academic/sections').subscribe({
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

  private loadPeriods(): void {
    this.api.get<AttendancePeriod[]>('/api/academic/timetable/periods').subscribe({
      next: (items) => {
        this.periods = (items ?? [])
          .filter((period) => !period.breakPeriod)
          .slice()
          .sort(
            (a, b) =>
              Number(a.periodNo ?? Number.MAX_SAFE_INTEGER) -
              Number(b.periodNo ?? Number.MAX_SAFE_INTEGER),
          );
      },
      error: () => {
        // Day attendance remains available if periods are not configured or cannot be loaded.
        this.periods = [];
      },
    });
  }

  loadRoster(preserveFeedback = false): void {
    if (!this.sectionId || !this.date) {
      return;
    }
    const requestId = ++this.rosterRequestId;
    this.busy = true;
    this.error = '';
    if (!preserveFeedback) {
      this.status = '';
      this.parentAlerts = null;
    }
    let path =
      `/api/attendance/roster?sectionId=${encodeURIComponent(this.sectionId)}` +
      `&date=${encodeURIComponent(this.date)}`;
    if (this.periodId) {
      path += `&periodId=${encodeURIComponent(this.periodId)}`;
    }
    this.api.get<AttendanceRoster>(path).subscribe({
      next: (data) => {
        if (requestId !== this.rosterRequestId) return;
        this.roster = data;
        this.students = (data?.students ?? []).map((student, index) => ({
          ...student,
          key:
            student.studentId ||
            student.admissionNo ||
            `${student.studentName || 'student'}-${index}`,
          rosterIndex: index + 1,
          status: this.normalizeMark(student.markStatus),
          remarkText: String(student.remark || ''),
        }));
        this.selectedKeys.clear();
        this.searchQuery = '';
        this.statusFilter = 'ALL';
        this.dirty = false;
        this.refreshView();
        this.busy = false;
      },
      error: (err) => {
        if (requestId !== this.rosterRequestId) return;
        this.busy = false;
        this.roster = null;
        this.students = [];
        this.visibleStudents = [];
        this.selectedKeys.clear();
        this.summary = this.emptySummary();
        this.error = err?.error?.message ?? 'Failed to load roster';
      },
    });
  }

  refreshView(): void {
    const query = this.searchQuery.trim().toLowerCase();
    this.visibleStudents = this.students.filter((student) => {
      const matchesQuery =
        !query ||
        String(student.studentName || '')
          .toLowerCase()
          .includes(query) ||
        String(student.admissionNo || '')
          .toLowerCase()
          .includes(query);
      const matchesStatus =
        this.statusFilter === 'ALL' ||
        (this.statusFilter === 'NOT_MARKED'
          ? student.status === null
          : student.status === this.statusFilter);
      return matchesQuery && matchesStatus;
    });
    this.summary = this.students.reduce<AttendanceSummary>(
      (counts, student) => {
        if (student.status === 'PRESENT') counts.present++;
        else if (student.status === 'ABSENT') counts.absent++;
        else if (student.status === 'LATE') counts.late++;
        else if (student.status === 'LEAVE') counts.leave++;
        else counts.notMarked++;
        return counts;
      },
      { ...this.emptySummary(), total: this.students.length },
    );
  }

  markAll(status: AttendanceMark): void {
    if (this.locked() || !this.students.length) return;
    for (const student of this.students) {
      student.status = status;
    }
    this.selectedKeys.clear();
    this.dirty = true;
    this.refreshView();
  }

  setStudentStatus(student: AttendanceStudentState, status: AttendanceMark): void {
    if (this.locked() || student.status === status) return;
    student.status = status;
    this.dirty = true;
    this.refreshView();
  }

  setRemark(student: AttendanceStudentState, value: string): void {
    if (this.locked()) return;
    student.remarkText = value;
    this.dirty = true;
  }

  toggleStudent(key: string, checked: boolean): void {
    if (checked) this.selectedKeys.add(key);
    else this.selectedKeys.delete(key);
  }

  toggleVisible(checked: boolean): void {
    for (const student of this.visibleStudents) {
      if (checked) this.selectedKeys.add(student.key);
      else this.selectedKeys.delete(student.key);
    }
  }

  applyBulkStatus(status: AttendanceMark): void {
    if (this.locked() || !this.selectedKeys.size) return;
    for (const student of this.students) {
      if (this.selectedKeys.has(student.key)) {
        student.status = status;
      }
    }
    this.selectedKeys.clear();
    this.dirty = true;
    this.refreshView();
  }

  isSelected(key: string): boolean {
    return this.selectedKeys.has(key);
  }

  allVisibleSelected(): boolean {
    return (
      this.visibleStudents.length > 0 &&
      this.visibleStudents.every((student) => this.selectedKeys.has(student.key))
    );
  }

  someVisibleSelected(): boolean {
    const selectedVisible = this.visibleStudents.filter((student) =>
      this.selectedKeys.has(student.key),
    ).length;
    return selectedVisible > 0 && selectedVisible < this.visibleStudents.length;
  }

  clearSelection(): void {
    this.selectedKeys.clear();
  }

  downloadRegister(format: 'PDF' | 'EXCEL' | 'CSV' = 'PDF'): void {
    if (!this.sectionId || !this.date) {
      this.error = 'Pick a section and date first';
      return;
    }
    this.busy = true;
    this.error = '';
    let path =
      `/api/attendance/roster/register?sectionId=${encodeURIComponent(this.sectionId)}` +
      `&date=${encodeURIComponent(this.date)}&format=${encodeURIComponent(format)}`;
    if (this.periodId) {
      path += `&periodId=${encodeURIComponent(this.periodId)}`;
    }
    this.api.get<any>(path).subscribe({
      next: (res) => {
        this.busy = false;
        this.saveBase64File(res);
        this.status = `${format} register downloaded`;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Register export failed';
      },
    });
  }

  private saveBase64File(res: any): void {
    const b64 = String(res?.contentBase64 || '');
    if (!b64) {
      this.error = 'Export returned empty content';
      return;
    }
    const bin = atob(b64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    const blob = new Blob([bytes], { type: res.contentType || 'application/octet-stream' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = res.fileName || 'attendance-register.pdf';
    a.click();
    URL.revokeObjectURL(url);
  }

  initials(name: unknown): string {
    const parts = String(name || '')
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (!parts.length) return '?';
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  save(submit = false): void {
    if (!this.roster || this.busy || this.locked()) {
      return;
    }
    if (submit && this.summary.notMarked > 0) {
      this.error = `Mark all ${this.summary.notMarked} remaining student${
        this.summary.notMarked === 1 ? '' : 's'
      } before submitting attendance.`;
      return;
    }
    if (submit && this.submitted() && !this.dirty) {
      this.status = 'Attendance is already submitted and has no new changes.';
      return;
    }
    const marks = this.buildMarks();
    this.busy = true;
    this.error = '';
    this.status = '';
    this.api
      .put<AttendanceBulkResponse>('/api/attendance/sessions/bulk', {
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
            : `Saved draft (${Number(res?.markCount || marks.length)} marks)`;
          this.loadRoster(true);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save failed';
        },
      });
  }

  submit(): void {
    // Always persist the current roster and submit it in one request. The old submit-only
    // endpoint could finalize a session while leaving unsaved client-side edits behind.
    this.save(true);
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

  submitted(): boolean {
    return String(this.roster?.session?.status || '').toUpperCase() === 'SUBMITTED';
  }

  canSaveDraft(): boolean {
    return !!this.roster && !this.busy && !this.locked() && !this.submitted() && this.dirty;
  }

  canSubmit(): boolean {
    return (
      !!this.roster &&
      this.students.length > 0 &&
      !this.busy &&
      !this.locked() &&
      this.summary.notMarked === 0 &&
      (!this.submitted() || this.dirty)
    );
  }

  alertDeliveries(): Array<{
    studentName: string;
    markStatus: string;
    channel: string;
    status: string;
    error: string;
  }> {
    const rows: Array<{
      studentName: string;
      markStatus: string;
      channel: string;
      status: string;
      error: string;
    }> = [];
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

  private buildMarks(): AttendanceMarkRequest[] {
    return this.students
      .filter(
        (student): student is AttendanceStudentState & { status: AttendanceMark } =>
          student.status !== null,
      )
      .map((student) => ({
        studentId: student.studentId,
        admissionNo: student.admissionNo,
        studentName: student.studentName,
        status: student.status,
        remark: student.remarkText.trim() || undefined,
      }));
  }

  private normalizeMark(value: unknown): AttendanceMark | null {
    const normalized = String(value || '').toUpperCase();
    return this.markOptions.includes(normalized as AttendanceMark)
      ? (normalized as AttendanceMark)
      : null;
  }

  private emptySummary(): AttendanceSummary {
    return { total: 0, present: 0, absent: 0, late: 0, leave: 0, notMarked: 0 };
  }

  private todayLocal(): string {
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private submittedMessage(markCount: number | undefined, alerts: ParentAlertSummary | null): string {
    const count = Number(markCount || 0);
    if (!alerts) {
      return `Submitted (${count} marks)`;
    }
    return `Submitted (${count} marks) · ${Number(alerts.notified || 0)} parent alert${
      Number(alerts.notified || 0) === 1 ? '' : 's'
    } queued`;
  }
}
