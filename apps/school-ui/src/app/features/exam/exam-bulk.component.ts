import { Component, Input, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import { AuthSessionService } from '../../core/auth-session.service';

interface AcademicClass {
  id: string;
  name: string;
}
interface AcademicSection {
  id: string;
  classId: string;
  name: string;
  studentLabel?: string;
  classTeacherUsername?: string;
}
interface AcademicSubject {
  id: string;
  name: string;
  status?: string;
}
interface Assignment {
  sectionId: string;
  subjectId?: string;
  teacherUsername?: string;
  status?: string;
}
interface TeacherScope {
  sectionIds?: string[];
  classTeacherSectionIds?: string[];
  assignments?: Array<{ sectionId: string; subjectId?: string | null }>;
}
interface ExamDef {
  id: string;
  name: string;
  termKey: string;
  sectionId: string;
  subjectId: string;
  maxMarks: number;
  passingMarks?: number | null;
  examDate?: string | null;
  status: string;
  updatedAt?: string;
  createdBy?: string;
}
interface BoardRow extends ExamDef {
  sectionLabel?: string;
  className?: string;
  subjectName?: string;
  teacherName?: string;
  total?: number;
  completed?: number;
  pending?: number;
  progressPercent?: number;
}
interface MarkRow {
  studentId: string | null;
  admissionNo: string;
  studentName: string;
  rollNo: string;
  marks: string;
  entryStatus: string;
  remarks: string;
  grade: string | null;
  percentage: number | null;
  result: string | null;
  leftSection: boolean;
  open: boolean;
}
interface ImportIssue {
  row: number;
  message: string;
}
interface ImportPreview {
  total: number;
  valid: MarkRow[];
  errors: ImportIssue[];
}

const EXCUSED = new Set(['ABSENT', 'NOT_APPEARED', 'EXEMPTED', 'MEDICAL_LEAVE', 'WITHHELD']);
const ELEVATED = new Set(['SHOP_OWNER', 'SUPER_ADMIN', 'ADMIN', 'PRINCIPAL']);

@Component({
  selector: 'sf-exam-bulk',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './exam-bulk.component.html',
  styleUrls: ['../../shared/admin-page.scss', './exam-bulk.component.scss'],
})
export class ExamBulkComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly session = inject(AuthSessionService);

  @Input() view: 'home' | 'entry' = 'entry';

  loading = true;
  busy = false;
  error = '';
  status = '';
  role = '';

  classes: AcademicClass[] = [];
  sections: AcademicSection[] = [];
  subjects: AcademicSubject[] = [];
  assignments: Assignment[] = [];
  definitions: ExamDef[] = [];
  teacherScope: TeacherScope = {};
  board: BoardRow[] = [];

  classId = '';
  sectionId = '';
  subjectId = '';
  examDefinitionId = '';
  createOpen = false;
  createDraft = { name: '', termKey: 'TERM1', maxMarks: 100, passingMarks: 33, examDate: '' };

  gradebook: any = null;
  rows: MarkRow[] = [];
  query = '';
  filter = 'ALL';
  sortKey: 'rollNo' | 'studentName' | 'admissionNo' | 'marks' | 'entryStatus' = 'rollNo';
  sortDir: 'ASC' | 'DESC' = 'ASC';
  quickEntry = false;
  dialog: '' | 'fill' | 'submit' | 'import' | 'correction' | 'status' | 'clear' = '';
  fillValue = '';
  fillOverwrite = false;
  statusValue = 'PRESENT';
  correctionReason = '';
  importPreview: ImportPreview | null = null;
  history: any[] = [];
  historyFor = '';

  readonly statuses = [
    { value: '', label: 'Pending' },
    { value: 'PRESENT', label: 'Present' },
    { value: 'ABSENT', label: 'Absent' },
    { value: 'NOT_APPEARED', label: 'Not Appeared' },
    { value: 'EXEMPTED', label: 'Exempted' },
    { value: 'MEDICAL_LEAVE', label: 'Medical Leave' },
    { value: 'WITHHELD', label: 'Withheld' },
  ];
  readonly filters = [
    { value: 'ALL', label: 'All' },
    { value: 'PENDING', label: 'Pending' },
    { value: 'ENTERED', label: 'Entered' },
    { value: 'ABSENT', label: 'Absent' },
    { value: 'NOT_APPEARED', label: 'Not Appeared' },
    { value: 'EXEMPTED', label: 'Exempted' },
  ];

  ngOnInit(): void {
    this.role = (this.session.getRole?.() ?? '').toUpperCase();
    if (this.view === 'home') {
      this.loadBoard();
      return;
    }
    this.loadCatalog();
  }

  get elevated(): boolean {
    return ELEVATED.has(this.role) || this.role === '';
  }

  get classOptions(): AcademicClass[] {
    if (!this.isTeacher || !this.teacherScope.sectionIds) return this.classes;
    const allowed = new Set(this.teacherScope.sectionIds);
    const classIds = new Set(
      this.sections.filter((s) => allowed.has(s.id)).map((s) => s.classId),
    );
    return this.classes.filter((c) => classIds.has(c.id));
  }

  get sectionOptions(): AcademicSection[] {
    let rows = this.sections.filter((s) => !this.classId || s.classId === this.classId);
    if (this.isTeacher && this.teacherScope.sectionIds) {
      const allowed = new Set(this.teacherScope.sectionIds);
      rows = rows.filter((s) => allowed.has(s.id));
    }
    return rows;
  }

  get subjectOptions(): AcademicSubject[] {
    const active = this.subjects.filter((s) => String(s.status || 'ACTIVE').toUpperCase() !== 'INACTIVE');
    if (!this.sectionId) return active;
    const onSection = this.assignments.filter(
      (a) => a.sectionId === this.sectionId && String(a.status || 'ACTIVE').toUpperCase() !== 'INACTIVE',
    );
    const ids = new Set(onSection.map((a) => a.subjectId).filter((id): id is string => !!id));
    let rows = ids.size ? active.filter((s) => ids.has(s.id)) : active;
    if (this.isTeacher && !this.isClassTeacherOf(this.sectionId)) {
      const mine = new Set(
        (this.teacherScope.assignments ?? [])
          .filter((a) => a.sectionId === this.sectionId && a.subjectId)
          .map((a) => String(a.subjectId)),
      );
      if (mine.size) {
        rows = rows.filter((s) => mine.has(s.id));
      }
    }
    return rows;
  }

  get examOptions(): ExamDef[] {
    return this.definitions.filter((d) => {
      if (this.sectionId && d.sectionId !== this.sectionId) return false;
      if (this.subjectId && d.subjectId !== this.subjectId) return false;
      return true;
    });
  }

  get maxMarks(): number {
    return Number(this.gradebook?.exam?.maxMarks ?? this.selectedExam?.maxMarks ?? 100);
  }

  get readOnly(): boolean {
    return !!this.gradebook?.readOnly;
  }

  get canUnlock(): boolean {
    return !!this.gradebook?.canUnlock && this.readOnly;
  }

  get allowIncomplete(): boolean {
    return !!this.gradebook?.policy?.allowIncompleteSubmit;
  }

  get selectedExam(): ExamDef | undefined {
    return this.definitions.find((d) => d.id === this.examDefinitionId);
  }

  get visibleRows(): MarkRow[] {
    const q = this.query.trim().toLowerCase();
    let rows = this.rows.filter((row) => {
      if (q) {
        const blob = `${row.studentName} ${row.rollNo} ${row.admissionNo}`.toLowerCase();
        if (!blob.includes(q)) return false;
      }
      if (this.filter === 'PENDING') return !this.isComplete(row);
      if (this.filter === 'ENTERED') return this.isComplete(row) && !EXCUSED.has(row.entryStatus);
      if (this.filter === 'ALL') return true;
      return row.entryStatus === this.filter;
    });
    const dir = this.sortDir === 'ASC' ? 1 : -1;
    rows = [...rows].sort((a, b) => dir * this.compare(a, b));
    return rows;
  }

  get counts() {
    const total = this.rows.length;
    const completed = this.rows.filter((r) => this.isComplete(r)).length;
    return {
      total,
      completed,
      pending: total - completed,
      entered: this.rows.filter((r) => this.isComplete(r) && !EXCUSED.has(r.entryStatus)).length,
      absent: this.rows.filter((r) => r.entryStatus === 'ABSENT').length,
      notAppeared: this.rows.filter((r) => r.entryStatus === 'NOT_APPEARED').length,
      exempted: this.rows.filter((r) => r.entryStatus === 'EXEMPTED').length,
      percent: total ? Math.round((completed * 100) / total) : 0,
    };
  }

  get summary() {
    const nums = this.rows
      .filter((r) => r.marks.trim() !== '' && !EXCUSED.has(r.entryStatus) && !this.markError(r))
      .map((r) => Number(r.marks));
    if (!nums.length) return { average: null as number | null, highest: null as number | null, lowest: null as number | null };
    const sum = nums.reduce((a, b) => a + b, 0);
    return {
      average: Math.round((sum / nums.length) * 100) / 100,
      highest: Math.max(...nums),
      lowest: Math.min(...nums),
    };
  }

  get homeCounts() {
    return {
      pending: this.board.filter((r) => (r.pending ?? 0) > 0 && (r.status === 'OPEN' || r.status === 'DRAFT')).length,
      drafts: this.board.filter((r) => r.status === 'OPEN' || r.status === 'DRAFT').length,
      submitted: this.board.filter((r) => r.status === 'SUBMITTED' || r.status === 'PUBLISHED' || r.status === 'LOCKED').length,
      corrections: this.board.filter((r) => r.status === 'CORRECTION_REQUESTED').length,
    };
  }

  openEntry(id?: string): void {
    void this.router.navigate(['/admin/exam'], {
      queryParams: id ? { bulk: '1', definition: id } : { bulk: '1' },
    });
  }

  openIndividual(): void {
    void this.router.navigate(['/admin/exam'], { queryParams: { new: '1' } });
  }

  backToList(): void {
    void this.router.navigate(['/admin/exam'], { queryParams: {} });
  }

  onClassChange(): void {
    if (!this.sectionOptions.some((s) => s.id === this.sectionId)) {
      this.sectionId = this.sectionOptions[0]?.id ?? '';
    }
    this.onSectionChange();
  }

  onSectionChange(): void {
    if (!this.subjectOptions.some((s) => s.id === this.subjectId)) {
      this.subjectId = this.subjectOptions[0]?.id ?? '';
    }
    this.refreshExamChoice();
  }

  onSubjectChange(): void {
    this.refreshExamChoice();
  }

  onExamChange(): void {
    const exam = this.selectedExam;
    if (!exam) {
      this.gradebook = null;
      this.rows = [];
      return;
    }
    const section = this.sections.find((s) => s.id === exam.sectionId);
    this.classId = section?.classId ?? this.classId;
    this.sectionId = exam.sectionId;
    this.subjectId = exam.subjectId;
    this.loadGradebook();
  }

  downloadDateSheet(): void {
    if (!this.sectionId) return;
    this.busy = true;
    this.error = '';
    this.api.getBlob(`/api/exam/date-sheets/pdf?sectionId=${encodeURIComponent(this.sectionId)}`).subscribe({
      next: (blob) => {
        this.busy = false;
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'date-sheet.pdf';
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.busy = false;
        this.error = 'Could not download the date sheet';
      },
    });
  }

  createExam(): void {
    if (!this.sectionId || !this.subjectId || !this.createDraft.name.trim()) {
      this.error = 'Class, section, subject and exam name are required';
      return;
    }
    this.busy = true;
    this.error = '';
    this.api
      .post<ExamDef>('/api/exam/definitions', {
        sectionId: this.sectionId,
        subjectId: this.subjectId,
        name: this.createDraft.name.trim(),
        termKey: this.createDraft.termKey || 'TERM1',
        maxMarks: Number(this.createDraft.maxMarks) || 100,
        passingMarks: this.createDraft.passingMarks === null ? null : Number(this.createDraft.passingMarks),
        examDate: this.createDraft.examDate || null,
      })
      .subscribe({
        next: (def) => {
          this.busy = false;
          this.createOpen = false;
          this.definitions = [def, ...this.definitions.filter((d) => d.id !== def.id)];
          this.examDefinitionId = def.id;
          this.loadGradebook();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Could not create the exam';
        },
      });
  }

  markError(row: MarkRow): string {
    if (this.marksDisabled(row) || !row.marks.trim()) return '';
    const n = Number(row.marks);
    if (Number.isNaN(n)) return 'Enter a number';
    if (n < 0) return 'Marks cannot be negative';
    if (n > this.maxMarks) return `Marks cannot exceed Maximum Marks (${this.maxMarks}).`;
    return '';
  }

  marksDisabled(row: MarkRow): boolean {
    if (this.readOnly) return true;
    return EXCUSED.has(row.entryStatus) && !this.gradebook?.policy?.allowMarksWhenExcused;
  }

  onStatus(row: MarkRow): void {
    if (this.marksDisabled(row)) row.marks = '';
  }

  onMarksKey(event: KeyboardEvent, row: MarkRow): void {
    const target = event.target as HTMLInputElement;
    const visible = this.visibleRows;
    const index = visible.indexOf(row);
    if (event.key === 'Enter' || event.key === 'ArrowDown') {
      event.preventDefault();
      this.focusMarks(visible[index + 1]);
    } else if (event.key === 'ArrowUp') {
      event.preventDefault();
      this.focusMarks(visible[index - 1]);
    } else if (event.key === 'Tab' && event.shiftKey) {
      event.preventDefault();
      this.focusMarks(visible[index - 1]);
    }
    void target;
  }

  onPaste(event: ClipboardEvent, row: MarkRow): void {
    const text = event.clipboardData?.getData('text') ?? '';
    if (!text.includes('\n') && !text.includes('\t')) return;
    event.preventDefault();
    const visible = this.visibleRows;
    const start = visible.indexOf(row);
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    lines.forEach((line, offset) => {
      const target = visible[start + offset];
      if (!target || this.marksDisabled(target)) return;
      const cells = line.split('\t');
      const marksCell = cells.length === 1 ? cells[0] : cells.find((c) => c !== '' && !Number.isNaN(Number(c))) ?? cells[cells.length - 1];
      target.marks = String(marksCell ?? '').trim();
      if (target.marks && !target.entryStatus) target.entryStatus = 'PRESENT';
    });
  }

  askFill(): void {
    this.fillOverwrite = false;
    this.dialog = 'fill';
  }

  applyFill(): void {
    const n = Number(this.fillValue);
    if (this.fillValue.trim() === '' || Number.isNaN(n) || n < 0 || n > this.maxMarks) {
      this.error = `Fill value must be between 0 and ${this.maxMarks}`;
      return;
    }
    const existing = this.rows.some((r) => !this.marksDisabled(r) && r.marks.trim() !== '');
    if (existing && !this.fillOverwrite) {
      this.fillOverwrite = true;
      return;
    }
    for (const row of this.rows) {
      if (this.marksDisabled(row)) continue;
      if (row.marks.trim() && !this.fillOverwrite) continue;
      row.marks = String(n);
      if (!row.entryStatus) row.entryStatus = 'PRESENT';
    }
    this.dialog = '';
    this.fillOverwrite = false;
    this.error = '';
  }

  askClear(): void {
    this.dialog = 'clear';
  }

  applyClear(): void {
    for (const row of this.rows) {
      if (this.readOnly) continue;
      row.marks = '';
      row.entryStatus = '';
      row.remarks = '';
    }
    this.dialog = '';
  }

  applyStatus(): void {
    for (const row of this.visibleRows) {
      if (this.readOnly) continue;
      row.entryStatus = this.statusValue;
      this.onStatus(row);
    }
    this.dialog = '';
  }

  exportTemplate(): void {
    const lines = ['Student ID,Admission No,Roll No,Student Name,Marks,Status,Remarks'];
    for (const row of this.rows) {
      lines.push(
        [row.studentId ?? '', row.admissionNo, row.rollNo, row.studentName, row.marks, row.entryStatus, row.remarks]
          .map(csv)
          .join(','),
      );
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'marks-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  onImportFile(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.name.toLowerCase().endsWith('.xlsx') || file.name.toLowerCase().endsWith('.xls')) {
      this.error = 'Save the sheet as CSV, or copy the marks column and paste it into the grid.';
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      this.importPreview = this.parseImport(String(reader.result ?? ''));
      this.dialog = 'import';
      this.error = '';
    };
    reader.readAsText(file);
  }

  importValid(): void {
    if (!this.importPreview) return;
    const byId = new Map(this.rows.map((r) => [r.studentId || r.admissionNo, r]));
    for (const incoming of this.importPreview.valid) {
      const row = byId.get(incoming.studentId || incoming.admissionNo);
      if (!row || this.marksDisabled(row)) continue;
      row.marks = incoming.marks;
      row.entryStatus = incoming.entryStatus;
      row.remarks = incoming.remarks;
    }
    this.dialog = '';
    this.importPreview = null;
    this.status = 'Imported valid rows into the grid. Save draft to keep them.';
  }

  saveDraft(): void {
    this.persist(false);
  }

  askSubmit(): void {
    if (this.rows.some((r) => this.markError(r))) {
      this.error = 'Fix invalid marks before submitting.';
      return;
    }
    this.dialog = 'submit';
  }

  confirmSubmit(anyway: boolean): void {
    this.dialog = '';
    this.persist(true, anyway);
  }

  reviewPending(): void {
    this.dialog = '';
    this.filter = 'PENDING';
    this.query = '';
  }

  requestCorrection(): void {
    if (!this.correctionReason.trim() || !this.examDefinitionId) return;
    this.busy = true;
    this.api
      .post(`/api/exam/definitions/${this.examDefinitionId}/correction-request`, {
        reason: this.correctionReason.trim(),
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.dialog = '';
          this.correctionReason = '';
          this.status = 'Correction requested. Marks stay locked until they are unlocked.';
          this.loadGradebook();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Correction request failed';
        },
      });
  }

  unlock(): void {
    if (!this.examDefinitionId) return;
    this.busy = true;
    this.api.post(`/api/exam/definitions/${this.examDefinitionId}/unlock`, { reason: 'Unlocked for correction' }).subscribe({
      next: () => {
        this.busy = false;
        this.status = 'Marks unlocked. You can edit and submit again.';
        this.loadGradebook();
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Unlock failed';
      },
    });
  }

  showHistory(row: MarkRow): void {
    if (!this.examDefinitionId) return;
    this.historyFor = row.studentName;
    const params = row.studentId
      ? `studentId=${encodeURIComponent(row.studentId)}`
      : `admissionNo=${encodeURIComponent(row.admissionNo)}`;
    this.api
      .get<any[]>(`/api/exam/marks/history?examDefinitionId=${this.examDefinitionId}&${params}`)
      .subscribe({
        next: (items) => (this.history = items ?? []),
        error: () => (this.history = []),
      });
  }

  sort(key: typeof this.sortKey): void {
    if (this.sortKey === key) {
      this.sortDir = this.sortDir === 'ASC' ? 'DESC' : 'ASC';
    } else {
      this.sortKey = key;
      this.sortDir = 'ASC';
    }
  }

  statusLabel(value: string): string {
    return this.statuses.find((s) => s.value === value)?.label || value || 'Pending';
  }

  private loadBoard(): void {
    this.loading = true;
    this.api.get<BoardRow[]>('/api/exam/entry-board').subscribe({
      next: (rows) => {
        this.board = rows ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Could not load marks progress';
      },
    });
  }

  private loadCatalog(): void {
    this.loading = true;
    forkJoin({
      classes: this.api.get<AcademicClass[]>('/api/academic/classes').pipe(catchError(() => of([]))),
      sections: this.api.get<AcademicSection[]>('/api/academic/sections').pipe(catchError(() => of([]))),
      subjects: this.api.get<AcademicSubject[]>('/api/academic/subjects').pipe(catchError(() => of([]))),
      assignments: this.api.get<Assignment[]>('/api/academic/assignments').pipe(catchError(() => of([]))),
      exams: this.api.get<ExamDef[]>('/api/exam/definitions').pipe(catchError(() => of([]))),
      scope: this.api.get<TeacherScope>('/api/academic/teacher-scope').pipe(catchError(() => of({}))),
    }).subscribe((catalog) => {
      this.classes = catalog.classes ?? [];
      this.sections = catalog.sections ?? [];
      this.subjects = catalog.subjects ?? [];
      this.assignments = catalog.assignments ?? [];
      this.definitions = catalog.exams ?? [];
      this.teacherScope = catalog.scope ?? {};
      this.loading = false;
      const preset = this.route.snapshot.queryParamMap.get('definition');
      if (preset && this.definitions.some((d) => d.id === preset)) {
        this.examDefinitionId = preset;
        this.onExamChange();
      } else if (this.classOptions.length) {
        this.classId = this.classOptions[0].id;
        this.onClassChange();
      }
    });
  }

  private refreshExamChoice(): void {
    const still = this.examOptions.some((d) => d.id === this.examDefinitionId);
    if (!still) {
      this.examDefinitionId = this.examOptions[0]?.id ?? '';
    }
    if (this.examDefinitionId) this.loadGradebook();
    else {
      this.gradebook = null;
      this.rows = [];
    }
  }

  private loadGradebook(): void {
    if (!this.examDefinitionId) return;
    this.busy = true;
    this.error = '';
    this.api.get<any>(`/api/exam/gradebook?examDefinitionId=${encodeURIComponent(this.examDefinitionId)}`).subscribe({
      next: (data) => {
        this.gradebook = data;
        this.rows = (data?.students ?? []).map((s: any) => ({
          studentId: s.studentId ?? null,
          admissionNo: s.admissionNo ?? '',
          studentName: s.studentName ?? '',
          rollNo: s.rollNo ?? '',
          marks: s.marksObtained == null ? '' : String(s.marksObtained),
          entryStatus: s.entryStatus ?? (s.marksObtained != null ? 'PRESENT' : ''),
          remarks: s.remarks ?? '',
          grade: s.grade ?? null,
          percentage: s.percentage ?? null,
          result: s.result ?? null,
          leftSection: !!s.leftSection,
          open: false,
        }));
        this.busy = false;
      },
      error: (err) => {
        this.busy = false;
        this.gradebook = null;
        this.rows = [];
        this.error = err?.error?.message ?? 'Could not load the class';
      },
    });
  }

  private persist(submit: boolean, anyway = false): void {
    if (!this.examDefinitionId || this.readOnly) return;
    if (this.rows.some((r) => this.markError(r))) {
      this.error = 'Fix invalid marks before saving.';
      return;
    }
    const marks = this.rows.map((r) => ({
      studentId: r.studentId,
      admissionNo: r.admissionNo || null,
      studentName: r.studentName,
      rollNo: r.rollNo || null,
      marksObtained: r.marks.trim() === '' ? null : Number(r.marks),
      entryStatus: r.entryStatus || null,
      remarks: r.remarks || null,
    }));
    this.busy = true;
    this.error = '';
    this.api
      .put<any>('/api/exam/gradebook/bulk', {
        examDefinitionId: this.examDefinitionId,
        expectedUpdatedAt: this.gradebook?.exam?.updatedAt,
        marks,
      })
      .subscribe({
        next: (data) => {
          this.gradebook = data;
          if (!submit) {
            this.busy = false;
            this.status = 'Draft saved.';
            this.loadGradebook();
            return;
          }
          this.api
            .post<any>(`/api/exam/definitions/${this.examDefinitionId}/submit`, { submitAnyway: anyway })
            .subscribe({
              next: () => {
                this.busy = false;
                this.status = 'Marks submitted. The grid is now locked.';
                this.loadGradebook();
              },
              error: (err) => {
                this.busy = false;
                this.error = err?.error?.message ?? 'Submit failed';
                this.loadGradebook();
              },
            });
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Save failed';
        },
      });
  }

  private parseImport(text: string): ImportPreview {
    const lines = text.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
    const errors: ImportIssue[] = [];
    const valid: MarkRow[] = [];
    const seen = new Set<string>();
    lines.forEach((line, index) => {
      if (index === 0 && /student/i.test(line)) return;
      const cells = splitCsv(line);
      const studentId = cells[0] || '';
      const admission = cells[1] || '';
      const roll = cells[2] || '';
      const name = cells[3] || '';
      const marks = cells[4] || '';
      const status = (cells[5] || '').toUpperCase().replace(/ /g, '_');
      const remarks = cells[6] || '';
      const rowNo = index + 1;
      const match = this.rows.find(
        (r) => (studentId && r.studentId === studentId) || (admission && r.admissionNo === admission),
      );
      if (!match) {
        errors.push({ row: rowNo, message: 'Student not found in this class' });
        return;
      }
      const key = match.studentId || match.admissionNo;
      if (seen.has(key)) {
        errors.push({ row: rowNo, message: 'Duplicate student' });
        return;
      }
      seen.add(key);
      if (marks.trim()) {
        const n = Number(marks);
        if (Number.isNaN(n)) {
          errors.push({ row: rowNo, message: 'Marks are not a number' });
          return;
        }
        if (n < 0 || n > this.maxMarks) {
          errors.push({ row: rowNo, message: `Marks ${marks} exceed maximum marks ${this.maxMarks}` });
          return;
        }
      }
      if (status && !['PRESENT', 'ABSENT', 'NOT_APPEARED', 'EXEMPTED', 'MEDICAL_LEAVE', 'WITHHELD'].includes(status)) {
        errors.push({ row: rowNo, message: `Unknown status ${status}` });
        return;
      }
      valid.push({
        ...match,
        marks,
        entryStatus: status,
        remarks,
        rollNo: roll || match.rollNo,
        studentName: name || match.studentName,
      });
    });
    const missing = this.rows.filter((r) => !seen.has(r.studentId || r.admissionNo));
    for (const row of missing) {
      errors.push({ row: 0, message: `Missing student ${row.studentName}` });
    }
    return { total: Math.max(0, lines.length - (lines.length && /student/i.test(lines[0]) ? 1 : 0)), valid, errors };
  }

  private isComplete(row: MarkRow): boolean {
    if (EXCUSED.has(row.entryStatus)) return true;
    return row.marks.trim() !== '' && !this.markError(row);
  }

  private compare(a: MarkRow, b: MarkRow): number {
    if (this.sortKey === 'marks') {
      const av = a.marks.trim() === '' ? -1 : Number(a.marks);
      const bv = b.marks.trim() === '' ? -1 : Number(b.marks);
      return av - bv;
    }
    if (this.sortKey === 'rollNo') {
      return rollCompare(a.rollNo, b.rollNo);
    }
    return String(a[this.sortKey] || '').localeCompare(String(b[this.sortKey] || ''), undefined, { numeric: true });
  }

  private focusMarks(row?: MarkRow): void {
    if (!row) return;
    const key = row.studentId || row.admissionNo;
    const el = document.getElementById('mark-' + key) as HTMLInputElement | null;
    el?.focus();
    el?.select();
  }

  private get isTeacher(): boolean {
    return this.role === 'TEACHER';
  }

  private isClassTeacherOf(sectionId: string): boolean {
    return (this.teacherScope.classTeacherSectionIds ?? []).includes(sectionId);
  }
}

function csv(value: string): string {
  const text = value ?? '';
  if (/[",\n]/.test(text)) return `"${text.replace(/"/g, '""')}"`;
  return text;
}

function splitCsv(line: string): string[] {
  const out: string[] = [];
  let cur = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (quoted && ch === '"' && line[i + 1] === '"') {
      cur += '"';
      i++;
    } else if (ch === '"') {
      quoted = !quoted;
    } else if (ch === ',' && !quoted) {
      out.push(cur.trim());
      cur = '';
    } else {
      cur += ch;
    }
  }
  out.push(cur.trim());
  return out;
}

function rollCompare(a: string, b: string): number {
  const an = Number(a);
  const bn = Number(b);
  if (!Number.isNaN(an) && !Number.isNaN(bn)) return an - bn;
  return a.localeCompare(b, undefined, { numeric: true });
}
