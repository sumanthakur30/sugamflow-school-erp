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
  persisted: boolean;
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
  gridMode = false;

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
  admissionNo = '';
  loadedDefinitionId = '';
  private loadedSnapshot = '';
  createOpen = false;
  createDraft = { name: '', termKey: 'TERM1', maxMarks: 100, passingMarks: 33, examDate: '' };

  gradebook: any = null;
  rows: MarkRow[] = [];
  query = '';
  filter = 'ALL';
  sortKey: 'rollNo' | 'studentName' | 'admissionNo' | 'marks' | 'entryStatus' = 'rollNo';
  sortDir: 'ASC' | 'DESC' = 'ASC';
  page = 1;
  pageSize = 25;
  quickEntry = false;
  dialog: '' | 'fill' | 'submit' | 'import' | 'correction' | 'status' | 'clear' | 'discard' | 'reset' | 'locked' | 'delete' = '';
  fillValue = '';
  fillOverwrite = false;
  statusValue = 'PRESENT';
  correctionReason = '';
  reviewNote = '';
  editingKey = '';
  editBackup: { marks: string; entryStatus: string; remarks: string } | null = null;
  deleteReason = '';
  deleteTarget: MarkRow | null = null;
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

  grid: {
    sectionLabel?: string;
    className?: string;
    academicSessionId?: string;
    columns: Array<{
      examDefinitionId: string;
      subjectName: string;
      examName: string;
      status: string;
      readOnly: boolean;
      maxMarks: number;
    }>;
    students: Array<{
      studentId: string | null;
      admissionNo: string;
      studentName: string;
      rollNo: string;
      cells: Record<string, { marks?: number | null; entryStatus?: string | null; grade?: string | null }>;
    }>;
  } | null = null;
  gridQuery = '';

  ngOnInit(): void {
    this.role = (this.session.getRole?.() ?? '').toUpperCase();
    this.gridMode = this.route.snapshot.queryParamMap.get('grid') === '1';
    this.route.queryParamMap.subscribe((params) => {
      this.gridMode = params.get('grid') === '1';
    });
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
    if (!this.classId) return [];
    let rows = this.sections.filter((s) => s.classId === this.classId);
    if (this.isTeacher && this.teacherScope.sectionIds) {
      const allowed = new Set(this.teacherScope.sectionIds);
      rows = rows.filter((s) => allowed.has(s.id));
    }
    return rows;
  }

  get subjectOptions(): AcademicSubject[] {
    const active = this.subjects.filter((s) => String(s.status || 'ACTIVE').toUpperCase() !== 'INACTIVE');
    if (!this.sectionId) return [];
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
    if (!this.sectionId || !this.subjectId) return [];
    return this.definitions.filter((d) => d.sectionId === this.sectionId && d.subjectId === this.subjectId);
  }

  get sheetDirty(): boolean {
    return this.sheetSnapshot() !== this.loadedSnapshot;
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

  get correctionRequested(): boolean {
    return this.gradebook?.exam?.status === 'CORRECTION_REQUESTED';
  }

  get canReview(): boolean {
    return this.canUnlock && this.correctionRequested;
  }

  get canRequestCorrection(): boolean {
    const status = this.gradebook?.exam?.status;
    return this.readOnly && (status === 'SUBMITTED' || status === 'PUBLISHED');
  }

  get allowIncomplete(): boolean {
    return !!this.gradebook?.policy?.allowIncompleteSubmit;
  }

  get selectedExam(): ExamDef | undefined {
    return this.definitions.find((d) => d.id === this.examDefinitionId);
  }

  get className(): string {
    return this.classes.find((c) => c.id === this.classId)?.name ?? '';
  }

  get subjectName(): string {
    return this.subjects.find((s) => s.id === this.subjectId)?.name ?? '';
  }

  get sessionLabel(): string {
    const session = this.gradebook?.exam?.academicSessionId;
    return session == null || session === '' ? '' : String(session);
  }

  get passingMarks(): number | null {
    const value = this.gradebook?.exam?.passingMarks;
    if (value == null || value === '') return null;
    const n = Number(value);
    return Number.isNaN(n) ? null : n;
  }

  get examStatusLabel(): string {
    return String(this.gradebook?.exam?.status ?? '').replaceAll('_', ' ');
  }

  get filteredActive(): boolean {
    return this.filter !== 'ALL' || !!this.query.trim();
  }

  get pageCount(): number {
    return Math.max(1, Math.ceil(this.visibleRows.length / this.pageSize));
  }

  get currentPage(): number {
    return Math.min(this.page, this.pageCount);
  }

  get pagedRows(): MarkRow[] {
    const page = Math.min(this.page, this.pageCount);
    const start = (page - 1) * this.pageSize;
    return this.visibleRows.slice(start, start + this.pageSize);
  }

  get viewCounts() {
    return this.countRows(this.visibleRows);
  }

  get viewSummary() {
    return this.summarize(this.visibleRows);
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
    return this.countRows(this.rows);
  }

  get summary() {
    return this.summarize(this.rows);
  }

  get homeCounts() {
    return {
      pending: this.board.filter((r) => (r.pending ?? 0) > 0 && (r.status === 'OPEN' || r.status === 'DRAFT')).length,
      drafts: this.board.filter((r) => r.status === 'OPEN' || r.status === 'DRAFT').length,
      submitted: this.board.filter((r) => r.status === 'SUBMITTED' || r.status === 'PUBLISHED' || r.status === 'LOCKED').length,
      corrections: this.board.filter((r) => r.status === 'CORRECTION_REQUESTED').length,
    };
  }

  get filteredGridStudents() {
    const rows = this.grid?.students ?? [];
    const q = this.gridQuery.trim().toLowerCase();
    if (!q) return rows;
    return rows.filter((row) =>
      [row.studentName, row.admissionNo, row.rollNo].some((value) => (value || '').toLowerCase().includes(q)),
    );
  }

  openGrid(): void {
    void this.router.navigate(['/admin/exam'], { queryParams: { bulk: '1', grid: '1' } });
  }

  loadSectionGrid(): void {
    this.error = '';
    if (!this.classId || !this.sectionId) {
      this.error = 'Select class and section.';
      return;
    }
    this.busy = true;
    this.api.get<NonNullable<ExamBulkComponent['grid']>>(`/api/exam/gradebook/section?sectionId=${this.sectionId}`).subscribe({
      next: (grid) => {
        this.busy = false;
        this.grid = grid ?? { columns: [], students: [] };
        this.gridQuery = '';
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Could not load the section grid';
      },
    });
  }

  cellText(
    student: NonNullable<ExamBulkComponent['grid']>['students'][number],
    examDefinitionId: string,
  ): string {
    const cell = student.cells?.[examDefinitionId];
    if (!cell) return '—';
    if (cell.entryStatus && EXCUSED.has(cell.entryStatus)) return this.statusLabel(cell.entryStatus);
    if (cell.marks == null) return '—';
    return String(cell.marks);
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
    this.sectionId = '';
    this.subjectId = '';
    this.examDefinitionId = '';
    this.error = '';
    this.clearSheetIfClean();
  }

  onSectionChange(): void {
    if (!this.subjectOptions.some((s) => s.id === this.subjectId)) {
      this.subjectId = '';
    }
    this.clearExamIfMissing();
    this.error = '';
    this.clearSheetIfClean();
  }

  onSubjectChange(): void {
    this.clearExamIfMissing();
    this.error = '';
    this.clearSheetIfClean();
  }

  onExamChange(): void {
    this.error = '';
    if (this.loadedDefinitionId !== this.examDefinitionId) {
      this.clearSheetIfClean();
    }
  }

  search(): void {
    this.error = '';
    if (!this.classId || !this.sectionId || !this.subjectId || !this.examDefinitionId) {
      this.error = 'Select class, section, subject, and exam before searching.';
      return;
    }
    const sameSheet = !!this.gradebook && this.loadedDefinitionId === this.examDefinitionId;
    if (sameSheet) {
      this.query = this.admissionNo.trim();
      this.page = 1;
      return;
    }
    if (this.sheetDirty) {
      this.dialog = 'discard';
      return;
    }
    this.applySearch();
  }

  applySearch(): void {
    this.dialog = '';
    this.query = this.admissionNo.trim();
    this.loadGradebook();
  }

  requestReset(): void {
    if (this.sheetDirty) {
      this.dialog = 'reset';
      return;
    }
    this.applyReset();
  }

  applyReset(): void {
    this.dialog = '';
    this.classId = '';
    this.sectionId = '';
    this.subjectId = '';
    this.examDefinitionId = '';
    this.admissionNo = '';
    this.query = '';
    this.filter = 'ALL';
    this.gradebook = null;
    this.rows = [];
    this.error = '';
    this.status = '';
    this.loadedDefinitionId = '';
    this.captureSnapshot();
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

  reviewCorrection(apply: boolean): void {
    if (!this.examDefinitionId || !this.reviewNote.trim()) return;
    this.busy = true;
    const path = apply ? 'correction-apply' : 'correction-reject';
    this.api
      .post(`/api/exam/definitions/${this.examDefinitionId}/${path}`, { reason: this.reviewNote.trim() })
      .subscribe({
        next: () => {
          this.busy = false;
          this.dialog = '';
          this.reviewNote = '';
          this.status = apply
            ? 'Correction applied. Marks can be edited and submitted again.'
            : 'Correction rejected. Marks stay read-only.';
          this.loadGradebook();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? (apply ? 'Could not apply this correction' : 'Could not reject this correction');
        },
      });
  }

  unlock(): void {
    if (!this.examDefinitionId || this.correctionRequested) return;
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

  rowKey(row: MarkRow): string {
    return row.studentId || row.admissionNo;
  }

  isEditing(row: MarkRow): boolean {
    return !!this.editingKey && this.editingKey === this.rowKey(row);
  }

  startEdit(row: MarkRow): void {
    if (this.readOnly) {
      this.dialog = 'locked';
      return;
    }
    if (this.editingKey && this.editingKey !== this.rowKey(row)) {
      const current = this.rows.find((item) => this.rowKey(item) === this.editingKey);
      if (current && this.editDirty(current)) {
        this.error = 'Save or cancel the row you are editing first.';
        return;
      }
      if (current) this.cancelEdit(current);
    }
    this.editingKey = this.rowKey(row);
    this.editBackup = { marks: row.marks, entryStatus: row.entryStatus, remarks: row.remarks };
    this.error = '';
  }

  cancelEdit(row: MarkRow): void {
    if (this.editBackup && this.isEditing(row)) {
      row.marks = this.editBackup.marks;
      row.entryStatus = this.editBackup.entryStatus;
      row.remarks = this.editBackup.remarks;
    }
    this.editingKey = '';
    this.editBackup = null;
    this.error = '';
  }

  saveEdit(row: MarkRow): void {
    const problem = this.markError(row);
    if (problem) {
      this.error = problem;
      return;
    }
    this.persist(false, false, 'Marks saved.');
  }

  askDelete(row: MarkRow): void {
    if (this.readOnly) {
      this.dialog = 'locked';
      return;
    }
    const current = this.rows.find((item) => this.rowKey(item) === this.editingKey);
    if (current && this.editDirty(current)) {
      this.error = 'Save or cancel the open edit before deleting a mark.';
      return;
    }
    if (!row.persisted && !row.marks.trim() && !row.entryStatus && !row.remarks.trim()) return;
    this.deleteTarget = row;
    this.deleteReason = '';
    this.dialog = 'delete';
  }

  confirmDelete(): void {
    const row = this.deleteTarget;
    if (!row) return;
    if (!row.persisted) {
      row.marks = '';
      row.entryStatus = '';
      row.remarks = '';
      row.grade = null;
      this.dialog = '';
      this.deleteTarget = null;
      this.status = 'Unsaved marks cleared for ' + row.studentName + '.';
      return;
    }
    const reason = this.deleteReason.trim();
    if (!reason || !this.examDefinitionId) return;
    this.busy = true;
    this.error = '';
    this.api
      .post<any>('/api/exam/gradebook/clear', {
        examDefinitionId: this.examDefinitionId,
        expectedUpdatedAt: this.gradebook?.exam?.updatedAt,
        studentId: row.studentId,
        admissionNo: row.admissionNo || null,
        reason,
      })
      .subscribe({
        next: () => {
          this.busy = false;
          this.dialog = '';
          this.deleteTarget = null;
          this.deleteReason = '';
          this.editingKey = '';
          this.editBackup = null;
          this.status = 'Marks removed for ' + row.studentName + '. The student stays on this exam.';
          this.loadGradebook();
        },
        error: (err) => {
          this.busy = false;
          this.error = err?.error?.message ?? 'Could not delete these marks';
        },
      });
  }

  setFilter(value: string): void {
    this.filter = value;
    this.page = 1;
  }

  clearFilters(): void {
    this.query = '';
    this.admissionNo = '';
    this.filter = 'ALL';
    this.page = 1;
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
      const exam = preset ? this.definitions.find((d) => d.id === preset) : undefined;
      if (exam) {
        const section = this.sections.find((s) => s.id === exam.sectionId);
        this.classId = section?.classId ?? '';
        this.sectionId = exam.sectionId;
        this.subjectId = exam.subjectId;
        this.examDefinitionId = exam.id;
        this.loadGradebook();
      }
    });
  }

  private clearExamIfMissing(): void {
    if (!this.examOptions.some((d) => d.id === this.examDefinitionId)) {
      this.examDefinitionId = '';
    }
  }

  private clearSheetIfClean(): void {
    if (this.sheetDirty) return;
    this.gradebook = null;
    this.rows = [];
    this.loadedDefinitionId = '';
    this.captureSnapshot();
  }

  private sheetSnapshot(): string {
    return this.rows
      .map((row) => `${row.studentId ?? ''}|${row.admissionNo}|${row.marks}|${row.entryStatus}|${row.remarks}`)
      .join('\n');
  }

  private captureSnapshot(): void {
    this.loadedSnapshot = this.sheetSnapshot();
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
          persisted: s.marksObtained != null || !!s.entryStatus || !!s.remarks,
        }));
        this.editingKey = '';
        this.editBackup = null;
        if (this.loadedDefinitionId !== this.examDefinitionId) this.page = 1;
        this.loadedDefinitionId = this.examDefinitionId;
        this.captureSnapshot();
        this.busy = false;
      },
      error: (err) => {
        this.busy = false;
        this.error = err?.error?.message ?? 'Could not load the class';
        if (this.sheetDirty) return;
        this.gradebook = null;
        this.rows = [];
        this.loadedDefinitionId = '';
        this.captureSnapshot();
      },
    });
  }

  private editDirty(row: MarkRow): boolean {
    if (!this.editBackup || !this.isEditing(row)) return false;
    return (
      row.marks !== this.editBackup.marks ||
      row.entryStatus !== this.editBackup.entryStatus ||
      row.remarks !== this.editBackup.remarks
    );
  }

  private countRows(rows: MarkRow[]) {
    const total = rows.length;
    const completed = rows.filter((r) => this.isComplete(r)).length;
    return {
      total,
      completed,
      pending: total - completed,
      entered: rows.filter((r) => this.isComplete(r) && !EXCUSED.has(r.entryStatus)).length,
      absent: rows.filter((r) => r.entryStatus === 'ABSENT').length,
      notAppeared: rows.filter((r) => r.entryStatus === 'NOT_APPEARED').length,
      exempted: rows.filter((r) => r.entryStatus === 'EXEMPTED').length,
      percent: total ? Math.round((completed * 100) / total) : 0,
    };
  }

  private summarize(rows: MarkRow[]) {
    const nums = rows
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

  private persist(submit: boolean, anyway = false, savedMessage = 'Draft saved.'): void {
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
            this.editingKey = '';
            this.editBackup = null;
            this.status = savedMessage;
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
