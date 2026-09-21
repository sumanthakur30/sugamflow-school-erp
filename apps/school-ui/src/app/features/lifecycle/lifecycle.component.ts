import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ApiService } from '../../core/api.service';
import { TenantContextService } from '../../core/tenant-context.service';
import { ModuleBootstrapService } from '../../core/module-bootstrap.service';
import { StudentLookupComponent } from '../../shared/student-lookup/student-lookup.component';
import { StudentLookupRow } from '../../shared/student-lookup/student-lookup.models';

interface MapRow {
  from: string;
  to: string;
}

@Component({
  selector: 'sf-lifecycle',
  standalone: true,
  imports: [CommonModule, FormsModule, StudentLookupComponent],
  templateUrl: './lifecycle.component.html',
  styleUrls: ['../../shared/admin-page.scss', './lifecycle.component.scss'],
})
export class LifecycleComponent implements OnInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly tenantContext = inject(TenantContextService);
  private readonly modules = inject(ModuleBootstrapService);
  private campusReadySub?: Subscription;

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
  /** Combined class list (academic sections + classes students are in). */
  classOptions: string[] = [];
  /** Raw academic-section labels, kept separate so we can prefer them as the default. */
  private sectionClassLabels: string[] = [];

  selectedStudentId = '';
  selectedStudentSummary: StudentLookupRow | null = null;
  classValue = '';
  promotionMapKey = 'default_grade_map';
  targetSessionId = '';
  alsoPromote = true;
  tcReason = 'Family relocation';
  readonly tcReasonOptions = [
    'Family relocation',
    'Parent job transfer',
    'Moving to another city',
    'Moving to another country',
    'Change of school',
    'Academic reasons',
    'Financial reasons',
    'Personal reasons',
    'Completed studies',
    'Other',
  ];
  tcRemarks = '';
  clearance: any = null;

  mapDraftKey = 'default_grade_map';
  mapRows: MapRow[] = [];
  editingMap = false;

  ngOnInit(): void {
    this.selectedStudentId = this.route.snapshot.queryParamMap.get('studentId') || '';
    this.campusReadySub = this.tenantContext.whenCampusReady().subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.campusReadySub?.unsubscribe();
  }

  reload(force = false): void {
    this.loading = true;
    this.error = '';
    const path = '/api/student/lifecycle/bootstrap';
    const apply = (boot: any) => {
      this.reportBuilderEnabled = !!boot.reportBuilderEnabled;
      this.classFieldKey = boot.classFieldKey ?? 'classApplied';
      this.promotionMaps = boot.promotionMaps ?? [];
      this.sessions = boot.sessions ?? [];
      this.tcPolicies = boot.tcPolicies ?? [];
      if (this.promotionMaps.length) {
        const first = this.payload(this.promotionMaps[0]);
        this.promotionMapKey = first.definitionKey ?? this.promotionMapKey;
        this.mapDraftKey = this.promotionMapKey;
        this.mapRows = this.toRows(first.mappings ?? {});
      }
      if (!this.targetSessionId) {
        this.targetSessionId = this.nextSessionKey() || '';
      }
      this.loading = false;
      this.loadStudents();
      this.loadEvents();
      this.loadClassOptions();
    };
    const peeked = force ? null : this.modules.peek(path);
    if (peeked) {
      apply(peeked);
      return;
    }
    this.modules.load(path, force).subscribe({
      next: apply,
      error: (err) => {
        this.loading = false;
        this.error = ModuleBootstrapService.errorMessage(err, 'Lifecycle bootstrap failed');
      },
    });
  }

  loadStudents(): void {
    this.api.getItems<any>('/api/student/students').subscribe({
      next: (list) => {
        this.students = list ?? [];
        const fromQuery = this.route.snapshot.queryParamMap.get('studentId') || '';
        if (fromQuery && this.students.some((s) => s.id === fromQuery)) {
          this.selectedStudentId = fromQuery;
          this.selectedStudentSummary = this.toLookupRow(
            this.students.find((s) => s.id === fromQuery),
          );
        } else if (this.selectedStudentId && !this.students.some((s) => s.id === this.selectedStudentId)) {
          this.selectedStudentId = '';
          this.selectedStudentSummary = null;
        }
        this.rebuildClassOptions();
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

  loadClassOptions(): void {
    this.api.get<any[]>('/api/academic/sections').subscribe({
      next: (sections) => {
        this.sectionClassLabels = (sections ?? [])
          .map((s) => String(s.studentLabel || '').trim())
          .filter(Boolean);
        this.rebuildClassOptions();
      },
      error: () => {
        this.sectionClassLabels = [];
        this.rebuildClassOptions();
      },
    });
  }

  /**
   * Every selectable class = academic sections + any class students are actually in.
   * Sections come first (the "official" list); student-only classes are appended so
   * nobody is stranded, and everything is sorted naturally (Grade 2 before Grade 10).
   */
  private rebuildClassOptions(): void {
    const studentClasses = this.students
      .map((s) => this.studentClass(s))
      .filter((c) => c && c !== '—');
    const merged = [...new Set([...this.sectionClassLabels, ...studentClasses])];
    merged.sort((a, b) => a.localeCompare(b, undefined, { numeric: true, sensitivity: 'base' }));
    this.classOptions = merged;
    if (!this.classValue && this.classOptions.length) {
      this.classValue = this.sectionClassLabels[0] ?? this.classOptions[0];
    }
  }

  payload(def: any): any {
    return def?.payload || def || {};
  }

  studentClass(row: any): string {
    const answers = row?.answers ?? {};
    return String(
      answers[this.classFieldKey] ?? answers.classApplied ?? row?.classSection ?? '—',
    );
  }

  studentLabel(row: any): string {
    const name = row?.answers?.fullName ?? row?.admissionNo ?? row?.id;
    return `${name} · ${this.studentClass(row)}`;
  }

  studentName(id: string): string {
    const row = this.students.find((s) => s.id === id);
    if (row) return String(row.answers?.fullName || row.admissionNo || id);
    if (this.selectedStudentSummary?.id === id) {
      return this.selectedStudentSummary.fullName || this.selectedStudentSummary.admissionNo || id;
    }
    // Students who already left are no longer in the directory; a raw UUID is noise.
    return /^[0-9a-f-]{20,}$/i.test(id) ? 'Former student' : id;
  }

  selectedStudent(): any {
    return (
      this.students.find((s) => s.id === this.selectedStudentId) ||
      (this.selectedStudentSummary?.id === this.selectedStudentId
        ? this.selectedStudentSummary
        : null)
    );
  }

  selectStudent(row: StudentLookupRow): void {
    this.selectedStudentId = row.id;
    this.selectedStudentSummary = row;
    this.clearance = null;
    this.error = '';
  }

  clearStudentSelection(): void {
    this.selectedStudentId = '';
    this.selectedStudentSummary = null;
    this.clearance = null;
  }

  studentParent(row: any): string {
    const answers = row?.answers ?? {};
    const guardians = Array.isArray(row?.guardians)
      ? row.guardians
      : Array.isArray(answers.guardians)
        ? answers.guardians
        : [];
    return String(
      row?.parentName ||
        answers.fatherName ||
        answers.parentName ||
        answers.guardianName ||
        guardians[0]?.fullName ||
        '—',
    );
  }

  studentAdmissionNo(row: any): string {
    return String(row?.admissionNo || row?.answers?.admissionNo || '—');
  }

  private toLookupRow(row: any): StudentLookupRow {
    return {
      id: String(row?.id || ''),
      fullName: this.studentName(String(row?.id || '')),
      admissionNo: this.studentAdmissionNo(row),
      classSection: this.studentClass(row),
      rollNo: row?.answers?.rollNo ? String(row.answers.rollNo) : undefined,
      parentName: this.studentParent(row),
      mobile: row?.answers?.mobile ? String(row.answers.mobile) : undefined,
      status: row?.status,
    };
  }

  /** Where the selected student will move to under the chosen promotion plan. */
  promotionPreview(): string {
    const student = this.selectedStudent();
    if (!student) return '';
    const from = this.studentClass(student);
    const map = this.promotionMaps.find(
      (m) => (this.payload(m).definitionKey ?? m.definitionKey) === this.promotionMapKey,
    );
    const to = this.payload(map)?.mappings?.[from];
    return to ? `${from} → ${to}` : '';
  }

  classStudentCount(classValue: string): number {
    return this.students.filter((s) => this.studentClass(s) === classValue).length;
  }

  classPromotionTarget(classValue: string): string {
    const map = this.promotionMaps.find(
      (m) => (this.payload(m).definitionKey ?? m.definitionKey) === this.promotionMapKey,
    );
    return this.payload(map)?.mappings?.[classValue] ?? '';
  }

  currentSessionKey(): string {
    const current = this.sessions.find((s) => this.payload(s).current);
    return current ? this.payload(current).definitionKey ?? current.definitionKey ?? '' : '';
  }

  nextSessionKey(): string {
    const current = this.sessions.find((s) => this.payload(s).current);
    return current ? this.payload(current).nextSessionKey ?? '' : '';
  }

  sessionName(def: any): string {
    return this.payload(def).name || def?.definitionKey || '';
  }

  eventTitle(e: any): string {
    const type = String(e?.eventType || '').toUpperCase();
    const labels: Record<string, string> = {
      PROMOTE: 'Student promoted',
      PROMOTED: 'Student promoted',
      PROMOTION: 'Student promoted',
      ROLLOVER: 'Moved to new academic year',
      TC_ISSUE: 'Transfer certificate issued',
      TC_ISSUED: 'Transfer certificate issued',
      TC: 'Transfer certificate issued',
      STATUS_CHANGE: 'Status changed',
    };
    return labels[type] || type.replace(/_/g, ' ').toLowerCase() || 'Event';
  }

  downloadEventPdf(e: any): void {
    if (!e?.id) return;
    this.api.getBlob(`/api/student/lifecycle/events/${e.id}/document`).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `lifecycle-${e.referenceNo || e.id}.pdf`;
        a.click();
        URL.revokeObjectURL(url);
      },
      error: (err) => (this.error = err?.error?.message ?? 'PDF download failed'),
    });
  }

  // ---- promotion plan editor ------------------------------------------------

  private toRows(mappings: Record<string, string>): MapRow[] {
    return Object.entries(mappings || {}).map(([from, to]) => ({ from, to: String(to) }));
  }

  addMapRow(): void {
    this.mapRows = [...this.mapRows, { from: '', to: '' }];
  }

  removeMapRow(index: number): void {
    this.mapRows = this.mapRows.filter((_, i) => i !== index);
  }

  savePromotionMap(): void {
    const key = (this.mapDraftKey || '').trim();
    if (!key) {
      this.error = 'Plan name is required';
      return;
    }
    const mappings: Record<string, string> = {};
    for (const row of this.mapRows) {
      const from = row.from.trim();
      const to = row.to.trim();
      if (!from && !to) continue;
      if (!from || !to) {
        this.error = 'Every step needs both a current class and a next class';
        return;
      }
      if (mappings[from]) {
        this.error = `"${from}" appears twice — each class can only move to one next class`;
        return;
      }
      mappings[from] = to;
    }
    if (!Object.keys(mappings).length) {
      this.error = 'Add at least one promotion step';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .put(`/api/student/lifecycle/promotion-maps/${encodeURIComponent(key)}`, {
        definitionKey: key,
        name: 'Promotion map',
        mappings,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.editingMap = false;
          this.status = 'Promotion plan saved';
          this.reload(true);
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save promotion plan failed';
        },
      });
  }

  // ---- actions ----------------------------------------------------------------

  promoteStudent(): void {
    if (!this.selectedStudentId) {
      this.error = 'Select a student';
      return;
    }
    const student = this.selectedStudent();
    const identity = `${this.studentName(this.selectedStudentId)} (${this.studentAdmissionNo(student)})`;
    if (!confirm(`Promote ${identity} according to ${this.promotionPreview()}?`)) {
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
    if (!this.classValue.trim()) {
      this.error = 'Choose which class to promote';
      return;
    }
    if (!confirm(`Promote every student in ${this.classValue} to their next class?`)) {
      return;
    }
    this.busy = true;
    this.api
      .post<any>('/api/student/lifecycle/promote-by-class', {
        classValue: this.classValue,
        promotionMapKey: this.promotionMapKey,
      })
      .subscribe({
        next: (res) => {
          this.busy = false;
          this.status = `Promoted ${res.count} student(s) from ${res.classValue}`;
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
    if (!this.classValue.trim() || !this.targetSessionId.trim()) {
      this.error = 'Choose a class and the academic year to move into';
      return;
    }
    if (
      !confirm(
        `Move every student in ${this.classValue} to academic year ${this.targetSessionId}` +
          (this.alsoPromote ? ' and promote them to their next class?' : '?'),
      )
    ) {
      return;
    }
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
          this.status = `Moved ${res.count} student(s) from ${res.classValue} into ${res.targetSessionId}`;
          this.loadStudents();
          this.loadEvents();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Session rollover failed';
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
            ? 'No dues found — safe to issue the certificate'
            : 'This student still has pending items — see the checklist below';
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Clearance check failed';
        },
      });
  }

  issueTc(): void {
    if (!this.selectedStudentId) {
      this.error = 'Select a student';
      return;
    }
    const student = this.selectedStudent();
    const name = student ? this.studentName(student.id) : 'this student';
    if (!confirm(`Issue a transfer certificate for ${name}? This marks the student as transferred.`)) {
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
          const pdfReady = res.hasTcDocument || doc?.status === 'READY';
          this.status = pdfReady
            ? `Transfer certificate ${res.referenceNo} issued — PDF is ready`
            : `Transfer certificate ${res.referenceNo} recorded (PDF not generated)`;
          this.loadStudents();
          this.loadEvents();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Issuing the transfer certificate failed';
        },
      });
  }
}
