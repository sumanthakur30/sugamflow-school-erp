import {
  AfterViewInit,
  Component,
  ElementRef,
  EventEmitter,
  HostBinding,
  HostListener,
  Input,
  OnDestroy,
  OnInit,
  Output,
  ViewChild,
  inject,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import {
  Subject,
  Subscription,
  catchError,
  debounceTime,
  distinctUntilChanged,
  forkJoin,
  map,
  of,
  switchMap,
  timeout,
} from 'rxjs';
import { ApiService, PageResult } from '../../core/api.service';
import {
  DEFAULT_BLOCKED_STUDENT_STATUSES,
  StudentLookupFilters,
  StudentLookupRow,
  isStudentStatusBlocked,
} from './student-lookup.models';

@Component({
  selector: 'sf-student-lookup',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './student-lookup.component.html',
  styleUrls: ['./student-lookup.component.scss'],
})
export class StudentLookupComponent implements OnInit, AfterViewInit, OnDestroy {
  private readonly api = inject(ApiService);
  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly search$ = new Subject<string>();
  private sub?: Subscription;
  private readonly uid = Math.random().toString(36).slice(2, 9);

  readonly inputId = `slu-input-${this.uid}`;
  readonly listId = `slu-list-${this.uid}`;

  @Input() placeholder =
    'Search name, admission no, roll no, mobile, class, parent…';
  @Input() minChars = 2;
  @Input() pageSize = 10;
  @Input() debounceMs = 300;
  @Input() defaultStatus = 'ACTIVE';
  @Input() blockedStatuses: readonly string[] = DEFAULT_BLOCKED_STUDENT_STATUSES;
  @Input() allowInactive = false;
  @Input() showFilters = true;
  @Input() disabled = false;
  @Input() label = 'Find student';
  /** Prefill search box (e.g. admission no from query). */
  @Input() initialQuery = '';
  /**
   * When the first search from {@link initialQuery} returns a row whose
   * admission number matches this value, select it automatically. Used when
   * arriving from another screen for one specific student.
   */
  @Input() autoSelectAdmissionNo = '';
  private autoSelectPending = false;
  /**
   * Also search Admission Applications that are not yet enrolled
   * (needed for fee collection at Reception before Student Master enrollment).
   */
  @Input() includeApplicants = false;

  @Output() studentSelected = new EventEmitter<StudentLookupRow>();
  @Output() cleared = new EventEmitter<void>();

  @ViewChild('searchInput') searchInput?: ElementRef<HTMLInputElement>;

  q = '';
  open = false;
  searching = false;
  error = '';
  results: StudentLookupRow[] = [];
  highlightIndex = -1;
  selected: StudentLookupRow | null = null;
  emptyHint = '';

  @HostBinding('class.slu-host-open')
  get hostOpen(): boolean {
    return this.open && !this.selected;
  }

  filters: StudentLookupFilters = {
    status: 'ACTIVE',
    classSection: '',
    branchId: '',
    academicSessionId: '',
  };
  showFilterPanel = false;

  ngOnInit(): void {
    this.filters.status = this.defaultStatus || 'ACTIVE';
    this.sub = this.search$
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((term) => {
          const t = term.trim();
          if (t.length < this.minChars && !this.hasActiveFilters()) {
            this.results = [];
            this.searching = false;
            this.open = false;
            this.emptyHint = '';
            return of(null);
          }
          this.searching = true;
          this.error = '';
          this.emptyHint = '';
          return forkJoin({
            students: this.searchStudents(t),
            applicants: this.includeApplicants
              ? this.searchApplicants(t)
              : of([] as StudentLookupRow[]),
          }).pipe(
            map(({ students, applicants }) => this.mergeResults(students, applicants)),
            catchError((err) => {
              this.error = err?.error?.message ?? 'Student search failed';
              this.searching = false;
              return of([] as StudentLookupRow[]);
            }),
          );
        }),
      )
      .subscribe((items) => {
        if (items == null) {
          return;
        }
        this.results = items;
        this.searching = false;
        this.highlightIndex = this.results.length ? 0 : -1;
        this.open = true;
        if (this.autoSelectPending) {
          this.autoSelectPending = false;
          const target = String(this.autoSelectAdmissionNo || '').trim().toLowerCase();
          const match = this.results.find(
            (row) => String(row.admissionNo || '').trim().toLowerCase() === target,
          );
          if (match) {
            this.selectRow(match);
            return;
          }
        }
        if (!this.results.length) {
          this.emptyHint = this.includeApplicants
            ? 'No matching students or admission applicants.'
            : 'No matching students in Student Master. If this person is only on Applications, approve & enroll them first — or enable applicant search.';
        }
      });
  }

  ngAfterViewInit(): void {
    if (this.initialQuery?.trim()) {
      this.q = this.initialQuery.trim();
      this.autoSelectPending = !!String(this.autoSelectAdmissionNo || '').trim();
      this.onQueryChange(this.q);
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onQueryChange(value: string): void {
    this.q = value;
    if (this.selected) {
      this.selected = null;
      this.cleared.emit();
    }
    this.search$.next(value);
  }

  onFocus(): void {
    if (this.disabled) return;
    if (this.results.length || this.q.trim().length >= this.minChars || this.hasActiveFilters()) {
      this.open = true;
    }
  }

  applyFilters(): void {
    this.search$.next(this.q);
    this.open = true;
  }

  clearSelection(): void {
    this.selected = null;
    this.q = '';
    this.results = [];
    this.open = false;
    this.highlightIndex = -1;
    this.emptyHint = '';
    this.cleared.emit();
    queueMicrotask(() => this.searchInput?.nativeElement?.focus());
  }

  selectRow(row: StudentLookupRow, event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    if (!this.isSelectable(row)) {
      this.error = `Student status ${row.status || 'unknown'} cannot be selected.`;
      return;
    }
    this.selected = row;
    this.q = row.fullName || row.admissionNo || '';
    this.open = false;
    this.results = [];
    this.highlightIndex = -1;
    this.error = '';
    this.studentSelected.emit(row);
  }

  @HostListener('document:click', ['$event'])
  onDocClick(ev: MouseEvent): void {
    if (!this.host.nativeElement.contains(ev.target as Node)) {
      this.open = false;
    }
  }

  onKeydown(ev: KeyboardEvent): void {
    if (this.disabled) return;
    if (ev.key === 'Escape') {
      this.open = false;
      return;
    }
    if (!this.open && (ev.key === 'ArrowDown' || ev.key === 'Enter')) {
      if (this.q.trim().length >= this.minChars || this.hasActiveFilters()) {
        this.open = true;
        this.search$.next(this.q);
      }
      return;
    }
    if (!this.open || !this.results.length) return;

    if (ev.key === 'ArrowDown') {
      ev.preventDefault();
      this.highlightIndex = Math.min(this.highlightIndex + 1, this.results.length - 1);
    } else if (ev.key === 'ArrowUp') {
      ev.preventDefault();
      this.highlightIndex = Math.max(this.highlightIndex - 1, 0);
    } else if (ev.key === 'Enter') {
      ev.preventDefault();
      const row = this.results[this.highlightIndex];
      if (row) this.selectRow(row);
    }
  }

  trackById(_: number, row: StudentLookupRow): string {
    return `${row.source || 'STUDENT'}:${row.id}`;
  }

  statusClass(status: unknown): string {
    const s = String(status || '')
      .trim()
      .toUpperCase();
    if (s === 'ACTIVE') return 'slu-badge slu-ok';
    if (s === 'APPLICANT' || s === 'IN_PROGRESS') return 'slu-badge slu-warn';
    if (s === 'INACTIVE' || s === 'SUSPENDED') return 'slu-badge slu-warn';
    return 'slu-badge';
  }

  private searchStudents(term: string) {
    const params = {
      q: term || undefined,
      status: this.resolveStatusFilter(),
      classSection: this.filters.classSection || undefined,
      branchId: this.filters.branchId || undefined,
      academicSessionId: this.filters.academicSessionId || undefined,
      includeDeleted: 'false',
    };
    // Prefer directory, but some deployments leave it hanging — time out and
    // fall back to the master search endpoint so results always resolve.
    return this.api.getPage<StudentLookupRow>('/api/student/directory/students', 0, this.pageSize, params).pipe(
      timeout(4000),
      map((page) => this.normalizeStudentRows(page)),
      catchError(() =>
        this.api
          .getPage<StudentLookupRow>('/api/student/students/search', 0, this.pageSize, params)
          .pipe(
            timeout(4000),
            map((page) => this.normalizeStudentRows(page)),
            catchError((err) => {
              this.error = err?.error?.message ?? 'Student search failed';
              return of([] as StudentLookupRow[]);
            }),
          ),
      ),
    );
  }

  private searchApplicants(term: string) {
    const q = term.trim().toLowerCase();
    return this.api.getPage<any>('/api/admission/applications', 0, 100).pipe(
      map((page) => {
        const items = page?.items ?? [];
        const rows: StudentLookupRow[] = [];
        for (const app of items) {
          if (app?.enrolledStudentId || app?.hasEnrollment) continue;
          const row = this.applicantToRow(app);
          let answersBlob = '';
          try {
            answersBlob = JSON.stringify(app?.answers ?? {}).toLowerCase();
          } catch {
            answersBlob = '';
          }
          if (!this.matchesApplicantQuery(row, answersBlob, q)) continue;
          rows.push(row);
          if (rows.length >= this.pageSize) break;
        }
        return rows;
      }),
      catchError(() => of([] as StudentLookupRow[])),
    );
  }

  private matchesApplicantQuery(row: StudentLookupRow, answersBlob: string, q: string): boolean {
    if (!q) return true;
    const hay = [
      row.fullName,
      row.admissionNo,
      row.mobile,
      row.email,
      row.classSection,
      row.parentName,
      answersBlob,
    ]
      .map((x) => String(x || '').toLowerCase())
      .join(' ');
    return hay.includes(q);
  }

  private applicantToRow(app: any): StudentLookupRow {
    const answers = app?.answers ?? {};
    const fullName =
      firstNonBlank(
        answers.fullName,
        answers.applicantName,
        answers.studentName,
        answers.firstName,
      ) || 'Applicant';
    const admissionNo =
      firstNonBlank(
        app?.enrolledAdmissionNo,
        answers.admissionNo,
        app?.id ? `APP-${String(app.id).slice(0, 8).toUpperCase()}` : '',
      ) || undefined;
    return {
      id: String(app.id),
      source: 'ADMISSION',
      applicationId: String(app.id),
      fullName,
      admissionNo,
      classSection: firstNonBlank(answers.classApplied, answers.classSection) || undefined,
      rollNo: answers.rollNo ? String(answers.rollNo) : undefined,
      parentName:
        firstNonBlank(answers.fatherName, answers.parentName, answers.guardianName) || undefined,
      mobile: answers.mobile ? String(answers.mobile) : undefined,
      email: answers.email ? String(answers.email) : undefined,
      status: 'APPLICANT',
      branchId: app.branchId,
      academicSessionId: app.academicSessionId,
    };
  }

  private normalizeStudentRows(page: PageResult<StudentLookupRow> | StudentLookupRow[]): StudentLookupRow[] {
    const items = Array.isArray(page) ? page : (page?.items ?? []);
    return (items || [])
      .map((row) => ({ ...row, source: row.source || ('STUDENT' as const) }))
      .filter((row) => this.isSelectable(row));
  }

  private mergeResults(
    students: StudentLookupRow[],
    applicants: StudentLookupRow[],
  ): StudentLookupRow[] {
    const out = [...students];
    const seenAdm = new Set(
      students.map((s) => String(s.admissionNo || '').trim().toLowerCase()).filter(Boolean),
    );
    const seenMobile = new Set(
      students.map((s) => String(s.mobile || '').trim().toLowerCase()).filter(Boolean),
    );
    for (const a of applicants) {
      const adm = String(a.admissionNo || '').trim().toLowerCase();
      const mob = String(a.mobile || '').trim().toLowerCase();
      if (adm && seenAdm.has(adm)) continue;
      if (mob && seenMobile.has(mob)) continue;
      out.push(a);
    }
    return out.slice(0, this.pageSize + (this.includeApplicants ? this.pageSize : 0));
  }

  private resolveStatusFilter(): string | undefined {
    if (this.filters.status) return this.filters.status;
    if (this.defaultStatus) return this.defaultStatus;
    return undefined;
  }

  private hasActiveFilters(): boolean {
    return !!(
      this.filters.classSection?.trim() ||
      this.filters.branchId?.trim() ||
      this.filters.academicSessionId?.trim() ||
      (this.filters.status && this.filters.status !== this.defaultStatus)
    );
  }

  private isSelectable(row: StudentLookupRow): boolean {
    if (row.deleted) return false;
    if (row.source === 'ADMISSION') {
      // Applicants are allowed when includeApplicants is on (fee at Reception).
      return true;
    }
    const status = String(row.status || '').toUpperCase();
    if (status === 'INACTIVE' && this.allowInactive) return true;
    return !isStudentStatusBlocked(row.status, this.blockedStatuses);
  }
}

function firstNonBlank(...vals: unknown[]): string {
  for (const v of vals) {
    if (v == null) continue;
    const s = String(v).trim();
    if (s) return s;
  }
  return '';
}
