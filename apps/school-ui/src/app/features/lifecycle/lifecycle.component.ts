import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../core/api.service';

@Component({
  selector: 'sf-lifecycle',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './lifecycle.component.html',
  styleUrls: ['../../shared/admin-page.scss'],
})
export class LifecycleComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  error = '';
  status = '';
  busy = false;

  reportBuilderEnabled = false;
  classFieldKey = 'classApplied';
  promotionMaps: any[] = [];
  sessions: any[] = [];
  tcPolicies: any[] = [];
  students: any[] = [];
  events: any[] = [];

  selectedStudentId = '';
  classValue = 'Grade 8-A';
  promotionMapKey = 'default_grade_map';
  targetSessionId = '2026-27';
  alsoPromote = true;
  tcReason = 'Family relocation';
  tcRemarks = '';
  clearance: any = null;

  mapDraftKey = 'default_grade_map';
  mapDraftJson = '';

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.api.get<any>('/api/student/lifecycle/bootstrap').subscribe({
      next: (boot) => {
        this.reportBuilderEnabled = !!boot.reportBuilderEnabled;
        this.classFieldKey = boot.classFieldKey ?? 'classApplied';
        this.promotionMaps = boot.promotionMaps ?? [];
        this.sessions = boot.sessions ?? [];
        this.tcPolicies = boot.tcPolicies ?? [];
        if (this.promotionMaps.length) {
          const first = this.payload(this.promotionMaps[0]);
          this.promotionMapKey = first.definitionKey ?? this.promotionMapKey;
          this.mapDraftKey = this.promotionMapKey;
          this.mapDraftJson = JSON.stringify(first.mappings ?? {}, null, 2);
        }
        this.loading = false;
        this.loadStudents();
        this.loadEvents();
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Lifecycle bootstrap failed';
      },
    });
  }

  loadStudents(): void {
    this.api.getItems<any>('/api/student/students').subscribe({
      next: (list) => {
        this.students = list ?? [];
        if (!this.selectedStudentId && this.students.length) {
          this.selectedStudentId = this.students[0].id;
        }
      },
      error: () => (this.students = []),
    });
  }

  loadEvents(): void {
    this.api.get<any[]>('/api/student/lifecycle/events').subscribe({
      next: (e) => (this.events = e ?? []),
      error: () => (this.events = []),
    });
  }

  payload(def: any): any {
    return def?.payload || def || {};
  }

  studentClass(row: any): string {
    const answers = row?.answers ?? {};
    return String(answers[this.classFieldKey] ?? answers.classApplied ?? '—');
  }

  studentLabel(row: any): string {
    const name = row?.answers?.fullName ?? row?.admissionNo ?? row?.id;
    return `${name} · ${this.studentClass(row)}`;
  }

  savePromotionMap(): void {
    let mappings: Record<string, string>;
    try {
      mappings = JSON.parse(this.mapDraftJson || '{}');
    } catch {
      this.error = 'Promotion map JSON is invalid';
      return;
    }
    const key = (this.mapDraftKey || '').trim();
    if (!key) {
      this.error = 'Map key is required';
      return;
    }
    this.busy = true;
    this.api
      .put(`/api/student/lifecycle/promotion-maps/${encodeURIComponent(key)}`, {
        definitionKey: key,
        name: 'Promotion map',
        mappings,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.status = `Saved promotion map ${key}`;
          this.reload();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save promotion map failed';
        },
      });
  }

  promoteStudent(): void {
    if (!this.selectedStudentId) {
      this.error = 'Select a student';
      return;
    }
    this.busy = true;
    this.api
      .post<any>('/api/student/lifecycle/promote', {
        studentIds: [this.selectedStudentId],
        promotionMapKey: this.promotionMapKey,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          const row = res.results?.[0];
          this.status = row
            ? `Promoted ${row.admissionNo}: ${row.fromClass} → ${row.toClass}`
            : `Promoted ${res.count} student(s)`;
          this.loadStudents();
          this.loadEvents();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Promotion failed';
        },
      });
  }

  promoteByClass(): void {
    this.busy = true;
    this.api
      .post<any>('/api/student/lifecycle/promote-by-class', {
        classValue: this.classValue,
        promotionMapKey: this.promotionMapKey,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.status = `Promoted class ${res.classValue}: ${res.count} student(s)`;
          this.loadStudents();
          this.loadEvents();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Promote-by-class failed';
        },
      });
  }

  rolloverClass(): void {
    this.busy = true;
    this.api
      .post<any>('/api/student/lifecycle/rollover-by-class', {
        classValue: this.classValue,
        targetSessionId: this.targetSessionId,
        alsoPromote: this.alsoPromote,
        promotionMapKey: this.promotionMapKey,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.status = `Rollover class ${res.classValue} → session ${res.targetSessionId}: ${res.count}`;
          this.loadStudents();
          this.loadEvents();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Rollover failed';
        },
      });
  }

  previewTcClearance(): void {
    if (!this.selectedStudentId) {
      this.error = 'Select a student';
      return;
    }
    this.busy = true;
    this.api
      .post<any>('/api/student/lifecycle/tc/clearance/preview', {
        studentId: this.selectedStudentId,
      })
      .subscribe({
        next: (c) => {
          this.busy = false;
          this.clearance = c;
          this.status = c.cleared
            ? 'TC clearance OK'
            : `TC blocked: ${c.blockReason ?? c.matchedActions?.join(', ')}`;
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Clearance preview failed';
        },
      });
  }

  issueTc(): void {
    if (!this.selectedStudentId) {
      this.error = 'Select a student';
      return;
    }
    this.busy = true;
    this.api
      .post<any>('/api/student/lifecycle/tc', {
        studentId: this.selectedStudentId,
        reason: this.tcReason,
        remarks: this.tcRemarks,
        idempotencyKey: crypto.randomUUID(),
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          const doc = res.payload?.document;
          const docNote =
            res.hasTcDocument || doc?.status === 'READY'
              ? 'TC PDF ready'
              : `TC event recorded (${doc?.error ?? 'no PDF'})`;
          this.status = `${res.referenceNo}: ${docNote}`;
          this.loadStudents();
          this.loadEvents();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'TC issue failed';
        },
      });
  }
}
