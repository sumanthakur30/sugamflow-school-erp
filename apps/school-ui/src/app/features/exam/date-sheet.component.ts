import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import {
  DATE_SHEET_CLASSES,
  DATE_SHEET_SEED,
  DATE_SHEET_STATUSES,
  DateSheetStatus,
  ExamDateSheet,
} from './date-sheet.model';

interface SheetDraft {
  title: string;
  className: string;
  year: string;
  from: string;
  to: string;
  students: string;
  subjects: string;
  status: DateSheetStatus;
  subjectNames: string;
}

@Component({
  selector: 'sf-exam-date-sheet',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './date-sheet.component.html',
  styleUrl: './date-sheet.component.scss',
})
export class ExamDateSheetComponent {
  readonly classes = DATE_SHEET_CLASSES;
  readonly statuses = DATE_SHEET_STATUSES;

  sheets: ExamDateSheet[] = DATE_SHEET_SEED.map((row) => ({ ...row, subjectNames: [...row.subjectNames] }));

  query = '';
  className = '';
  from = '';
  to = '';
  status = '';
  applied = { query: '', className: '', from: '', to: '', status: '' };

  editorOpen = false;
  editingId: string | null = null;
  viewing: ExamDateSheet | null = null;
  draft: SheetDraft = this.emptyDraft();

  constructor(private readonly router: Router) {}

  get visible(): ExamDateSheet[] {
    const q = this.applied.query.trim().toLowerCase();
    return this.sheets.filter((sheet) => {
      if (q && !sheet.title.toLowerCase().includes(q)) return false;
      if (this.applied.className && sheet.className !== this.applied.className) return false;
      if (this.applied.status && sheet.status !== this.applied.status) return false;
      if (this.applied.from && sheet.to < this.applied.from) return false;
      if (this.applied.to && sheet.from > this.applied.to) return false;
      return true;
    });
  }

  search(): void {
    this.applied = {
      query: this.query,
      className: this.className,
      from: this.from,
      to: this.to,
      status: this.status,
    };
    this.viewing = null;
  }

  reset(): void {
    this.query = '';
    this.className = '';
    this.from = '';
    this.to = '';
    this.status = '';
    this.search();
  }

  goBack(): void {
    void this.router.navigate(['/admin/dashboard']);
  }

  openCreate(): void {
    this.editingId = null;
    this.viewing = null;
    this.draft = this.emptyDraft();
    this.editorOpen = true;
  }

  openEdit(sheet: ExamDateSheet): void {
    this.editingId = sheet.id;
    this.viewing = null;
    this.draft = {
      title: sheet.title,
      className: sheet.className,
      year: sheet.year,
      from: sheet.from,
      to: sheet.to,
      students: sheet.students == null ? '' : String(sheet.students),
      subjects: String(sheet.subjects),
      status: sheet.status,
      subjectNames: sheet.subjectNames.join(', '),
    };
    this.editorOpen = true;
  }

  cancelEditor(): void {
    this.editorOpen = false;
    this.editingId = null;
  }

  save(): void {
    const title = this.draft.title.trim();
    const names = this.draft.subjectNames
      .split(',')
      .map((name) => name.trim())
      .filter(Boolean);
    const subjects = Number(this.draft.subjects) || names.length || 0;
    const studentsRaw = this.draft.students.trim();
    const next: ExamDateSheet = {
      id: this.editingId || `ds-${Date.now()}`,
      title: title || 'Untitled date sheet',
      className: this.draft.className || this.classes[0],
      year: this.draft.year.trim() || '2026',
      from: this.draft.from || this.draft.to || '2026-01-01',
      to: this.draft.to || this.draft.from || '2026-01-01',
      students: studentsRaw === '' ? null : Number(studentsRaw),
      subjects,
      status: this.draft.status,
      subjectNames: names.length ? names : ['Subject'],
    };
    if (this.editingId) {
      this.sheets = this.sheets.map((sheet) => (sheet.id === this.editingId ? next : sheet));
    } else {
      this.sheets = [next, ...this.sheets];
    }
    this.cancelEditor();
  }

  view(sheet: ExamDateSheet): void {
    this.editorOpen = false;
    this.viewing = sheet;
  }

  closeView(): void {
    this.viewing = null;
  }

  print(sheet: ExamDateSheet): void {
    this.viewing = sheet;
    setTimeout(() => window.print(), 50);
  }

  remove(sheet: ExamDateSheet): void {
    if (!window.confirm(`Delete “${sheet.title}” for ${sheet.className}?`)) return;
    this.sheets = this.sheets.filter((row) => row.id !== sheet.id);
    if (this.viewing?.id === sheet.id) this.viewing = null;
  }

  studentLabel(sheet: ExamDateSheet): string {
    return sheet.students == null ? 'N/A' : String(sheet.students);
  }

  formatDay(iso: string): string {
    const date = new Date(`${iso}T00:00:00`);
    if (Number.isNaN(date.getTime())) return iso;
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  }

  private emptyDraft(): SheetDraft {
    return {
      title: '',
      className: this.classes[0],
      year: '2026',
      from: '',
      to: '',
      students: '',
      subjects: '',
      status: 'Draft',
      subjectNames: '',
    };
  }
}
