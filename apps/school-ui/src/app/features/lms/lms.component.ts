import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ApiService } from '../../core/api.service';
import {
  headerSortIndicator,
  nextHeaderSort,
  pageMeta,
  sortRows,
} from '../../shared/list-toolbar/list-controls';

type LmsView = 'assignments' | 'publish' | 'settings';
type AssignmentFilter = '' | 'PUBLISHED' | 'DRAFT' | 'CLOSED' | 'DUE_SOON' | 'OVERDUE';

interface AcademicClass {
  id: string;
  name?: string;
}

interface AcademicSection {
  id: string;
  classId?: string;
  name?: string;
  studentLabel?: string;
}

interface AcademicSubject {
  id: string;
  name?: string;
  code?: string;
}

interface HomeworkRow {
  id: string;
  title?: string;
  description?: string;
  subjectKey?: string;
  classSection?: string;
  sectionId?: string;
  dueAt?: string;
  status?: string;
  createdAt?: string;
}

@Component({
  selector: 'sf-lms',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './lms.component.html',
  styleUrls: [
    '../../shared/admin-page.scss',
    '../../shared/list-toolbar/list-toolbar.component.scss',
    './lms.component.scss',
  ],
})
export class LmsComponent implements OnInit {
  private readonly api = inject(ApiService);

  loading = true;
  saving = false;
  error = '';
  message = '';
  academicWarning = '';
  settings: Record<string, unknown> = {};
  classes: AcademicClass[] = [];
  sections: AcademicSection[] = [];
  subjects: AcademicSubject[] = [];
  items: HomeworkRow[] = [];

  view: LmsView = 'assignments';
  formSubmitted = false;
  form = this.emptyForm();

  q = '';
  sectionFilter = '';
  subjectFilter = '';
  statusFilter: AssignmentFilter = '';
  sortBy = 'createdAt';
  sortDir: 'ASC' | 'DESC' = 'DESC';
  pageIndex = 0;
  pageSize = 25;

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.error = '';
    this.academicWarning = '';

    forkJoin({
      config: this.api.get<any>('/api/config/modules/lms').pipe(
        catchError((err) => {
          this.academicWarning =
            err?.error?.message ?? 'LMS settings are unavailable; homework remains accessible.';
          return of(null);
        }),
      ),
      classes: this.api.get<AcademicClass[]>('/api/academic/classes').pipe(
        catchError(() => {
          this.academicWarning = 'Some academic choices could not be loaded. Try refreshing.';
          return of([]);
        }),
      ),
      sections: this.api.get<AcademicSection[]>('/api/academic/sections').pipe(
        catchError(() => {
          this.academicWarning = 'Some academic choices could not be loaded. Try refreshing.';
          return of([]);
        }),
      ),
      subjects: this.api.get<AcademicSubject[]>('/api/academic/subjects').pipe(
        catchError(() => {
          this.academicWarning = 'Some academic choices could not be loaded. Try refreshing.';
          return of([]);
        }),
      ),
      homework: this.api.get<HomeworkRow[]>('/api/exam/homework'),
    }).subscribe({
      next: ({ config, classes, sections, subjects, homework }) => {
        this.settings = (config?.settings as Record<string, unknown>) || {};
        this.classes = classes ?? [];
        this.sections = sections ?? [];
        this.subjects = subjects ?? [];
        this.items = Array.isArray(homework) ? homework : [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load assignments';
      },
    });
  }

  loadHomework(showLoading = false): void {
    if (showLoading) this.loading = true;
    this.api.get<HomeworkRow[]>('/api/exam/homework').subscribe({
      next: (rows) => {
        this.items = rows ?? [];
        this.loading = false;
      },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message ?? 'Failed to load assignments';
      },
    });
  }

  selectView(view: LmsView): void {
    this.view = view;
    this.error = '';
  }

  openSummary(filter: AssignmentFilter): void {
    this.view = 'assignments';
    this.statusFilter = filter;
    this.pageIndex = 0;
    this.error = '';
  }

  get homeworkEnabled(): boolean {
    return this.settings['homeworkEnabled'] !== false;
  }

  get publishedCount(): number {
    return this.items.filter((item) => this.normalizedStatus(item) === 'PUBLISHED').length;
  }

  get dueSoonCount(): number {
    return this.items.filter((item) => this.isDueSoon(item)).length;
  }

  get overdueCount(): number {
    return this.items.filter((item) => this.isOverdue(item)).length;
  }

  get draftCount(): number {
    return this.items.filter((item) => this.normalizedStatus(item) === 'DRAFT').length;
  }

  get minDueDate(): string {
    const now = new Date();
    const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 10);
  }

  get formValid(): boolean {
    return (
      !!this.form.title.trim() &&
      !!this.form.sectionId &&
      !!this.form.subjectId &&
      !!this.form.dueDate &&
      !this.isPastDate(this.form.dueDate)
    );
  }

  create(): void {
    this.formSubmitted = true;
    this.error = '';
    this.message = '';
    if (!this.formValid) {
      this.error = 'Complete the required fields and choose a due date that is not in the past.';
      return;
    }

    const section = this.sections.find((item) => item.id === this.form.sectionId);
    const subject = this.subjects.find((item) => item.id === this.form.subjectId);
    if (!section || !subject) {
      this.error = 'The selected section or subject is no longer available. Refresh and try again.';
      return;
    }

    this.saving = true;
    this.api
      .post<HomeworkRow>('/api/exam/homework', {
        title: this.form.title.trim(),
        description: this.form.description.trim() || null,
        subjectKey: subject.code?.trim() || subject.name?.trim() || 'general',
        sectionId: section.id,
        classSection: this.sectionLabel(section),
        dueDate: this.form.dueDate,
        status: 'PUBLISHED',
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.formSubmitted = false;
          this.message = `“${this.form.title.trim()}” was published successfully.`;
          this.form = this.emptyForm();
          this.view = 'assignments';
          this.statusFilter = '';
          this.loadHomework();
        },
        error: (err) => {
          this.saving = false;
          this.error = err?.error?.message ?? 'Homework could not be published';
        },
      });
  }

  sectionLabel(sectionOrId: AcademicSection | string | null | undefined): string {
    const section =
      typeof sectionOrId === 'string'
        ? this.sections.find((item) => item.id === sectionOrId)
        : sectionOrId;
    if (!section) return 'Section unavailable';
    if (section.studentLabel?.trim()) return section.studentLabel.trim();
    const className = this.classes.find((item) => item.id === section.classId)?.name?.trim();
    return [className, section.name?.trim()].filter(Boolean).join(' · ') || 'Unnamed section';
  }

  assignmentSection(item: HomeworkRow): string {
    const section = item.sectionId
      ? this.sections.find((candidate) => candidate.id === item.sectionId)
      : undefined;
    return section ? this.sectionLabel(section) : item.classSection?.trim() || 'All sections';
  }

  subjectLabel(key: string | null | undefined): string {
    const value = String(key || '').trim();
    if (!value || value.toLowerCase() === 'general') return 'General';
    const subject = this.subjects.find(
      (item) =>
        item.id === value ||
        item.code?.toLowerCase() === value.toLowerCase() ||
        item.name?.toLowerCase() === value.toLowerCase(),
    );
    if (subject) return subject.name?.trim() || subject.code?.trim() || 'Subject';
    if (/^[0-9a-f]{8}-[0-9a-f-]{27}$/i.test(value)) return 'Subject unavailable';
    return value.replace(/[_-]+/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
  }

  statusLabel(item: HomeworkRow): string {
    if (this.isOverdue(item)) return 'Overdue';
    if (this.isDueSoon(item)) return 'Due soon';
    const status = this.normalizedStatus(item);
    return status.charAt(0) + status.slice(1).toLowerCase();
  }

  statusClass(item: HomeworkRow): string {
    if (this.isOverdue(item)) return 'overdue';
    if (this.isDueSoon(item)) return 'soon';
    const status = this.normalizedStatus(item);
    if (status === 'CLOSED') return 'closed';
    if (status === 'DRAFT') return 'draft';
    return 'published';
  }

  fmtDate(value?: string): string {
    if (!value) return 'No due date';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return String(value);
    return date.toLocaleDateString('en-IN', {
      day: 'numeric',
      month: 'short',
      year: 'numeric',
    });
  }

  dueHint(item: HomeworkRow): string {
    if (!item.dueAt) return 'No deadline';
    const days = this.daysUntil(item.dueAt);
    if (days < 0) return `${Math.abs(days)} day${Math.abs(days) === 1 ? '' : 's'} overdue`;
    if (days === 0) return 'Due today';
    if (days === 1) return 'Due tomorrow';
    return `Due in ${days} days`;
  }

  sortAssignmentsBy(key: string): void {
    const next = nextHeaderSort(this.sortBy, this.sortDir, key);
    this.sortBy = next.sortBy;
    this.sortDir = next.sortDir;
    this.pageIndex = 0;
  }

  sortIcon(key: string): string {
    return headerSortIndicator(this.sortBy, this.sortDir, key);
  }

  private matchingAssignments(): HomeworkRow[] {
    let rows = this.items;
    const q = this.q.trim().toLowerCase();
    if (q) {
      rows = rows.filter(
        (item) =>
          String(item.title || '').toLowerCase().includes(q) ||
          String(item.description || '').toLowerCase().includes(q) ||
          this.assignmentSection(item).toLowerCase().includes(q) ||
          this.subjectLabel(item.subjectKey).toLowerCase().includes(q),
      );
    }
    if (this.sectionFilter) {
      rows = rows.filter((item) => item.sectionId === this.sectionFilter);
    }
    if (this.subjectFilter) {
      rows = rows.filter(
        (item) => this.subjectLabel(item.subjectKey) === this.subjectLabel(this.subjectFilter),
      );
    }
    if (this.statusFilter === 'OVERDUE') {
      rows = rows.filter((item) => this.isOverdue(item));
    } else if (this.statusFilter === 'DUE_SOON') {
      rows = rows.filter((item) => this.isDueSoon(item));
    } else if (this.statusFilter) {
      rows = rows.filter((item) => this.normalizedStatus(item) === this.statusFilter);
    }
    return rows;
  }

  get filteredAssignments(): HomeworkRow[] {
    const rows = sortRows(this.matchingAssignments(), this.sortBy, this.sortDir, (row, key) => {
      if (key === 'section') return this.assignmentSection(row);
      if (key === 'subject') return this.subjectLabel(row.subjectKey);
      if (key === 'status') return this.statusLabel(row);
      return row[key as keyof HomeworkRow];
    });
    const start = this.pageIndex * this.pageSize;
    return rows.slice(start, start + this.pageSize);
  }

  get filteredTotal(): number {
    return this.matchingAssignments().length;
  }

  get pageLabel(): string {
    const meta = pageMeta(this.pageIndex, this.pageSize, this.filteredTotal);
    return meta.from ? `${meta.from}–${meta.to} of ${this.filteredTotal}` : '0 assignments';
  }

  get filtersActive(): boolean {
    return !!(this.q || this.sectionFilter || this.subjectFilter || this.statusFilter);
  }

  clearFilters(): void {
    this.q = '';
    this.sectionFilter = '';
    this.subjectFilter = '';
    this.statusFilter = '';
    this.sortBy = 'createdAt';
    this.sortDir = 'DESC';
    this.pageIndex = 0;
  }

  subjectOptionKey(subject: AcademicSubject): string {
    return subject.code?.trim() || subject.name?.trim() || subject.id;
  }

  private normalizedStatus(item: HomeworkRow): string {
    return String(item.status || 'PUBLISHED').toUpperCase();
  }

  private isOverdue(item: HomeworkRow): boolean {
    return (
      this.normalizedStatus(item) === 'PUBLISHED' &&
      !!item.dueAt &&
      this.daysUntil(item.dueAt) < 0
    );
  }

  private isDueSoon(item: HomeworkRow): boolean {
    if (this.normalizedStatus(item) !== 'PUBLISHED' || !item.dueAt) return false;
    const days = this.daysUntil(item.dueAt);
    return days >= 0 && days <= 7;
  }

  private daysUntil(value: string): number {
    const due = new Date(value);
    if (Number.isNaN(due.getTime())) return 0;
    const today = new Date();
    due.setHours(0, 0, 0, 0);
    today.setHours(0, 0, 0, 0);
    return Math.ceil((due.getTime() - today.getTime()) / 86400000);
  }

  private isPastDate(value: string): boolean {
    return !!value && value < this.minDueDate;
  }

  private emptyForm(): {
    title: string;
    description: string;
    subjectId: string;
    sectionId: string;
    dueDate: string;
  } {
    return { title: '', description: '', subjectId: '', sectionId: '', dueDate: '' };
  }
}
